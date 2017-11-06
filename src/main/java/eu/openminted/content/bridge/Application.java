package eu.openminted.content.bridge;

import eu.openminted.content.bridge.tasks.Consumer;
import eu.openminted.content.bridge.tasks.Producer;
import eu.openminted.content.index.IndexConfiguration;
import eu.openminted.content.index.IndexPublication;
import eu.openminted.content.openaire.OpenAireSolrClient;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.FileReader;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@SpringBootApplication
public class Application implements CommandLineRunner{


    @Bean
    public IndexConfiguration getEsConfig() throws Exception {
        return IndexConfiguration.getInstance();
    }

    @Bean
    public IndexPublication getIndex() throws Exception {
        return new IndexPublication(getEsConfig());
    }

    @org.springframework.beans.factory.annotation.Value("${solr.local.client.type}")
    private String localClientType;

    @org.springframework.beans.factory.annotation.Value("${solr.local.hosts}")
    private String localHosts;

    @org.springframework.beans.factory.annotation.Value("${solr.local.default.collection}")
    private String localDefaultCollection;

    @org.springframework.beans.factory.annotation.Value("${solr.query.output.field}")
    private String queryOutputField;

    @org.springframework.beans.factory.annotation.Value("${xml.path.directory}")
    private String xmlPathDirectory;

    private DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

    private XPath xpath = XPathFactory.newInstance().newXPath();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private IndexPublication index;

    private OpenAireSolrClient localSolrClient;

    private int MAX_THREADS = 10;

    public static void main(String[] args){
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... strings) throws Exception {

        Options opt = new Options();
        opt.addOption("f", true, "Hadoop configuration file (mandatory)");
        opt.addOption("v", false, "verbose (optional)");
        opt.addOption("n",true,"Number of documents to process each time");
        BasicParser parser = new BasicParser();
        CommandLine cl = parser.parse(opt, strings);
        if (!cl.hasOption("n")) {
            HelpFormatter f = new HelpFormatter();
            f.printHelp("java -jar [JAR_FILE]", opt);
            return;
        }

        Configuration conf = new Configuration();
        Properties p = new Properties();
        p.load(new FileReader(cl.getOptionValue("f")));

        Path path = new Path(p.getProperty("hdfs.exporter.seq.file"));
        String outDir = p.getProperty("hdfs.exporter.out.dir");

        System.out.println("Path:"+path.toString());

        for(Map.Entry<Object, Object> e : p.entrySet()) {
            conf.set(e.getKey().toString(), e.getValue().toString());
        }

        System.out.println("Configuration set, initiating process..");
        iniate();
        int numberOfDocs = Integer.parseInt(cl.getOptionValue("n"));

        BlockingQueue blockingQueue = new ArrayBlockingQueue(numberOfDocs);

        Producer producer = new Producer(blockingQueue,conf,path,outDir, cl.hasOption("v"),MAX_THREADS);

        new Thread(producer).start();


        for(int i=0;i<MAX_THREADS;i++){
            new Thread(new Consumer(blockingQueue,
                    applicationContext,
                    TransformerFactory.newInstance().newTransformer(),
                    index,
                    new OpenAireSolrClient(localClientType,
                            localHosts,
                            localDefaultCollection),
                    producer
            )).start();
        }
    }

    public void iniate(){
        this.localSolrClient = new OpenAireSolrClient(localClientType, localHosts, localDefaultCollection);
    }


}
