package eu.openminted.content.bridge;

import eu.openminted.content.bridge.tasks.AskingStores;
import eu.openminted.content.bridge.tasks.ParsingDocument;
import eu.openminted.content.bridge.tasks.SavingToSolr;
import eu.openminted.content.index.IndexConfiguration;
import eu.openminted.content.index.IndexPublication;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.xml.transform.TransformerFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
public class Application implements CommandLineRunner{


    private static final Logger logger = LogManager.getLogger(Application.class);

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

    @org.springframework.beans.factory.annotation.Value("${pathToFiles}")
    private String pathToFiles;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private IndexPublication index;

    private int MAX_THREADS = 3;

    public static void main(String[] args){
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... strings) throws Exception {

        Options opt = new Options();
        opt.addOption("v", false, "verbose (optional)");
        opt.addOption("n",true,"Number of documents to process each time");
        opt.addOption("t", true, "Number of consumer's threads");


        BasicParser parser = new BasicParser();
        CommandLine cl = parser.parse(opt, strings);
        if (!cl.hasOption("n")) {
            HelpFormatter f = new HelpFormatter();
            f.printHelp("java -jar [JAR_FILE]", opt);
            return;
        }


        logger.info("Configuration set, initiating process..");
        int numberOfDocs = Integer.parseInt(cl.getOptionValue("n"));
        MAX_THREADS = Integer.parseInt(cl.getOptionValue("t"));

        BlockingQueue blockingQueue = new ArrayBlockingQueue(numberOfDocs);
        BlockingQueue solrQueue = new ArrayBlockingQueue(numberOfDocs);

        AskingStores askingStores = new AskingStores(blockingQueue, pathToFiles);

        logger.info(localHosts+"/"+localDefaultCollection);
        SolrClient solrClient = new HttpSolrClient.Builder(localHosts+"/"+localDefaultCollection).build();

        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);


        new Thread(new SavingToSolr(solrQueue,applicationContext, solrClient)).start();

        new Thread(askingStores).start();


        for (int i = 0; i < MAX_THREADS; i++) {
            executor.execute(new ParsingDocument(blockingQueue,
                    solrQueue,
                    applicationContext,
                    TransformerFactory.newInstance().newTransformer(),
                    index,
                    askingStores
            ));
        }

    }

}
