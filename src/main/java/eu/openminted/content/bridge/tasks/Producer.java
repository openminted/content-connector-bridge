package eu.openminted.content.bridge.tasks;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.BlockingQueue;

public class Producer implements Runnable {


    private BlockingQueue bq = null;
    private Configuration conf;
    private Path path;
    private String outDir;
    private boolean verbose = false;
    private boolean running = false;
    private int maxThreads = 10;

    public Producer(BlockingQueue queue, Configuration conf, Path path, String outDir, boolean verbose, int maxThreads) {
        this.setBlockingQueue(queue);
        this.conf=conf;
        this.path = path;
        this.outDir = outDir;
        this.verbose = verbose;
        this.maxThreads = maxThreads;
        running = true;
    }

    public boolean isRunning(){
        return running;
    }


    @Override
    public void run() {
        int pcount = 0;
        int rcount = 0;
        int dcount = 0;
        int ocount = 0;
        int noDoc = 0;
        try {
            for(final Pair<Text, Text> p : SequenceFileUtils.read(path, conf)) {
                try {
                    String subfile = p.getFirst().toString();
                    //String subdir = p.getFirst().toString().split("::")[1].substring(0, 3);
                    noDoc++;
                    String type = "";
                    if (p.getSecond().toString().contains("oaf:datasource")) {
                        type = "datasources"; dcount++;
                    } else if (p.getSecond().toString().contains("oaf:project")) {
                        type = "projects"; pcount++;
                    } else if (p.getSecond().toString().contains("oaf:organization")) {
                        type = "organizations"; ocount++;
                    } else if (p.getSecond().toString().contains("oaf:person")) {
                        type = "persons";continue;
                    } else if (p.getSecond().toString().contains("oaf:result")) {
                        type = "results";rcount++;
                    } else {
                        throw new IllegalArgumentException("invalid entity type");
                    }

                    if(type.equals("results")) {
                        File currentdir = new File(outDir + "/" + type);//
                        FileUtils.forceMkdir(currentdir);
                        String entity;

                        if (p.getSecond().toString().startsWith("<?xml ")) {
                            entity = p.getSecond().toString().substring(p.getSecond().toString().indexOf("?>") + 2);
                        } else
                            entity = p.getSecond().toString();

                        StringReader reader = new StringReader(entity);
                        String filename = currentdir + "/" + subfile + ".xml";
                        FileWriter writer;

                        writer = new FileWriter(filename, true);

                        if (verbose) System.out.println("w: " + filename);

                        IOUtils.copy(reader, writer);
                        reader.close();
                        writer.close();
                        bq.add(filename);
                        System.out.println(rcount +" results have been queued so far..");
                    }
                } catch(IOException e) {
                    System.err.println("e: " + p.getFirst() + " " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("It's over!");
        running = false;
        for(int i=0;i<maxThreads;i++)
            bq.add("interrupt");
    }

    public void setBlockingQueue(BlockingQueue bq) {
        this.bq = bq;
    }

}
