package eu.openminted.content.bridge.tasks;

import com.google.gson.Gson;
import eu.dnetlib.data.objectstore.rmi.ObjectStoreFile;
import eu.dnetlib.data.objectstore.rmi.ObjectStoreService;
import eu.dnetlib.data.objectstore.rmi.ObjectStoreServiceException;
import eu.dnetlib.domain.EPR;
import eu.dnetlib.enabling.resultset.rmi.ResultSetService;
import eu.dnetlib.utils.EPRUtils;
import eu.openminted.content.bridge.utils.MyFilenameFilter;
import eu.openminted.content.index.entities.Publication;
import eu.openminted.content.index.entities.utils.ExtensionResolver;
import eu.openminted.omtdcache.CacheDataIDMD5;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class AskingStores implements Runnable {

    private static final Logger logger = LogManager.getLogger(AskingStores.class);

    private String pathToFiles;

    // Openaire services
    private String objectStoreAddress = "http://services.openaire.eu:8280/is/services/objectStore";
    private String rsAddress = "http://services.openaire.eu:8280/is/services/resultSet";

    private ObjectStoreService storeService;
    private ResultSetService rsService;
    private final CacheDataIDMD5 md5Calculator = new CacheDataIDMD5();
    private BlockingQueue bq;
    private boolean running = false;

    public AskingStores(BlockingQueue queue, String pathToFiles) {
        this.setBlockingQueue(queue);
        this.pathToFiles = pathToFiles;
        this.running = true;
        logger.info("Asking stores is up and running..");
    }

    public boolean isRunning(){
        return this.running;
    }


    @Override
    public void run() {

        Gson gson = new Gson();

        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ObjectStoreService.class);
        factory.setAddress(objectStoreAddress);
        storeService = (ObjectStoreService) factory.create();

        factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ResultSetService.class);
        factory.setAddress(rsAddress);
        rsService = (ResultSetService) factory.create();

       logger.info("Getting list of stores");
        List<String> stores = storeService.getListOfObjectStores();
        try {
            for (String store : stores) {
                logger.info("Getting result set from store " + store);
                W3CEndpointReference w3cEpr = null;
                w3cEpr = storeService.deliverObjects(store, 0L, new Date().getTime());


                EPR epr = EPRUtils.createEPR(w3cEpr);
                String rsId = epr.getParameter("ResourceIdentifier");
                int count = 0;
                int attempts = 0;
                while(count == 0 && attempts < 10) {
                    try {
                        count = rsService.getNumberOfElements(rsId);
                    }catch (Exception e){
                        attempts++;
                        try {
                            logger.debug("Connection may have been interrupted. Retrying in "+1000*attempts+ "s");
                            Thread.sleep(1000 * attempts);
                        } catch (InterruptedException e1) {
                            logger.error(e1.getMessage());
                            break;
                        }

                    }
                }
                boolean firstDoc = true;

                for (int i = 0; i < count; i += 1000) {
//                    logger.info(new Date() + "  Getting records " + i + " - " + (i + 1000) + "/" + count + " from store " + store);
//                    logger.info("Total size in count ::" + count +
//                            " Total size in Size::" + storeService.getSize(store));
                    List<String> objects = null;
                    int counter = 0;

                    while (objects == null && counter < 5) {
                        try {
                            objects = rsService.getResult(rsId, i, i + 1000, "waiting");
                        } catch (Exception e) {
                            e.printStackTrace();
                            counter++;
                        }
                    }

                    if (objects == null)
                        continue;

                    // Check where you have retrieved all documents in this store
                    if (firstDoc) {
                        ObjectStoreFile md = gson.fromJson(objects.get(0), ObjectStoreFile.class);
                        String filename = md.getObjectID().substring(0, md.getObjectID().lastIndexOf("::"));

                        if (!filename.contains("::")) {
//                            logger.info("Store " + store + " contains invalid filename, eg " + filename);
                            break;
                        }
//                        logger.info("Ready to scan " + pathToFiles);
                        String storePrefix = filename.substring(0, filename.lastIndexOf("::"));
                        MyFilenameFilter filter = new MyFilenameFilter(storePrefix);
                        String[] listFiles = new File(pathToFiles).list(filter);
//                        logger.info("Already downloaded " + new File(pathToFiles).list(filter).length
//                                + "files from store " + store);

                        if (listFiles.length == storeService.getSize(store)) {
//                            logger.info("Downloaded all files from store " + store);
                            break;
                        }

                        firstDoc = false;
                    }


                    for (int j = 0; j < objects.size(); j++) {
                        final ObjectStoreFile md = gson.fromJson(objects.get(j), ObjectStoreFile.class);
                        final String filename = md.getObjectID().substring(0, md.getObjectID().lastIndexOf("::"));
                        final String metadataFilename = pathToFiles + "metadata/" + filename + ".json";

//                        logger.info("Checking for the " + (i + j) + " object in store " + store);

                        if (!new File(metadataFilename).exists() && md.getFileSizeKB() < 20000) {

//                            logger.info(Thread.currentThread().getName() + " - " + filename);
                            String extension = ExtensionResolver.getExtension(md.getMimeType());
//                            logger.info(Thread.currentThread().getName() + " - " + filename + " - download");
                            InputStream is = null;
                            try {
                                // Get publication file
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                is = new URL(md.getURI()).openStream ();
                                byte[] byteChunk = new byte[20000]; // Or whatever size you want to read in at a time.
                                int n;

                                while ( (n = is.read(byteChunk)) > 0 ) {
                                    baos.write(byteChunk, 0, n);
                                }

                                // Create Publication document for Elastic Search
                                Publication pub = new Publication();
                                // openaireId
                                pub.setOpenaireId(filename);
                                // mimeType
                                pub.setMimeType(md.getMimeType());
                                // hash value
                                pub.setHashValue( md5Calculator.getID(baos.toByteArray()));
                                // URL to file
                                pub.setUrl(md.getURI());
//                                logger.info(Thread.currentThread().getName() + " " + pub);
                                try {
//                                    logger.info("Adding publication with id: "+pub.getOpenaireId() + " to the queue");
                                    bq.put(pub);
                                } catch (InterruptedException e) {
                                    logger.error(e.getMessage() +"--- continuing");
                                    continue;
                                }

                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                IOUtils.closeQuietly(is);
                            }
                        } else {
                            logger.info("file " + metadataFilename + " already exists or is over 20MB");
                        }
                    }
                }


            }
        } catch (ObjectStoreServiceException e) {
            logger.error("Fatal error " + e.getMessage() + " --- TERMINATING");
        }
        logger.info("Asking stores is done..");

        for(int i=0;i<10;i++) {
            try {
                bq.put(null);
            } catch (InterruptedException e) {
                logger.error(e.getMessage() +"--- continuing");
            }
        }
        running = false;
    }

    public void setBlockingQueue(BlockingQueue bq) {
        this.bq = bq;
    }


}
