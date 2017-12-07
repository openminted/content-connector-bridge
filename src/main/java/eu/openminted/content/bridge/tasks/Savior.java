package eu.openminted.content.bridge.tasks;

import eu.openminted.content.bridge.indices.ContentIndexing;
import eu.openminted.content.index.IndexPublication;
import eu.openminted.content.index.entities.Publication;
import eu.openminted.content.openaire.OpenAireSolrClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.util.SolrCLI;
import org.joda.time.DateTime;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;

public class Savior implements Runnable {

    private static final Logger logger = LogManager.getLogger(Savior.class);


    private static Logger log = Logger.getLogger(Savior.class.getName());

    private ApplicationContext applicationContext;
    private SolrClient localSolrClient;
    private BlockingQueue queue = null;
    private Producer producer;


    public Savior(BlockingQueue queue,
                  ApplicationContext applicationContext,
                  Producer producer, SolrClient solrClient ) {
        this.producer = producer;
        this.applicationContext = applicationContext;
        this.queue = queue;
        this.localSolrClient = solrClient;

        logger.info("Savior is up and running..");

    }

    @Override
    public void run() {
        int batch_size = 0;
        int added = 0;
        while(true){
            try {
                SolrInputDocument solrInputDocument = (SolrInputDocument) queue.take();
                if(solrInputDocument==null)
                    break;
                localSolrClient.add(solrInputDocument);
                batch_size++;
                if(batch_size==100){
                    localSolrClient.commit();
                    batch_size=0;
                    added++;
                    logger.info("Added "+added*100 + " so far to Solr");
                }


            } catch (SolrServerException e) {
                logger.error(e.getMessage());
            } catch (IOException e) {
                logger.error(e.getMessage());
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
        try {
            localSolrClient.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        logger.info("Savior is done..");
    }

}