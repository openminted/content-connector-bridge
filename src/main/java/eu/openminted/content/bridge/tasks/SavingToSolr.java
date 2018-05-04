package eu.openminted.content.bridge.tasks;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.util.concurrent.BlockingQueue;

public class SavingToSolr implements Runnable {

    private static final Logger logger = LogManager.getLogger(SavingToSolr.class);

    private ApplicationContext applicationContext;
    private SolrClient localSolrClient;
    private BlockingQueue queue = null;


    public SavingToSolr(BlockingQueue queue,
                        ApplicationContext applicationContext,
                        SolrClient solrClient ) {

        this.applicationContext = applicationContext;
        this.queue = queue;
        this.localSolrClient = solrClient;

        logger.info("SavingToSolr is up and running..");

    }

    @Override
    public void run() {
        int batch_size = 0;
        int added = 0;
        while(true){
            try {
                SolrInputDocument solrInputDocument = (SolrInputDocument) queue.take();
                if(solrInputDocument==null) {
                    localSolrClient.commit();
                    logger.info("Sent the last "+batch_size+ " to Solr and i am out!");
                    break;
                }
                localSolrClient.add(solrInputDocument);
                batch_size++;
                if(batch_size==1000){
                    localSolrClient.commit();
                    batch_size=0;
                    added++;
                    logger.info("Added "+added*1000 + " so far to Solr");
                }


            } catch (SolrServerException | IOException | InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
        try {
            localSolrClient.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        logger.info("SavingToSolr is done..");
    }

}