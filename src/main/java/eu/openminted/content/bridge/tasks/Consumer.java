package eu.openminted.content.bridge.tasks;

import eu.openminted.content.bridge.indices.ContentIndexing;
import eu.openminted.content.index.IndexPublication;
import eu.openminted.content.index.entities.Publication;
import eu.openminted.content.openaire.OpenAireSolrClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
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

public class Consumer implements Runnable {
    private static final Logger logger = LogManager.getLogger(Consumer.class);

    private ApplicationContext applicationContext;
    private Transformer transformer;
    private IndexPublication index;
    private long initiationTime;
    private DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    private DocumentBuilder dBuilder;
    private BlockingQueue parsingQueue = null;
    private BlockingQueue solrQueue = null;
    private Producer producer;


    public Consumer(BlockingQueue queue, BlockingQueue solrQueue,ApplicationContext applicationContext, Transformer transformer, IndexPublication index,
                    Producer producer) {
        this.applicationContext = applicationContext;
        this.transformer = transformer;
        this.index = index;
        this.initiationTime = System.currentTimeMillis();
        this.parsingQueue = queue;
        this.solrQueue = solrQueue;
        this.producer = producer;
        logger.info("Consumer is up and running...");
    }

    @Override
    public void run() {

        while(producer.isRunning() || !parsingQueue.isEmpty()) {
            String filename = "";
            try {
                filename = (String) parsingQueue.take();
                if(filename.equals("interrupt")) {
                    logger.info("Breaking..");
                    break;
                }
                File fileToParse = new File(filename);
                dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(fileToParse);
                doc.getDocumentElement().normalize();

                //Get the records list
                NodeList nodes = doc.getElementsByTagName("record");


                Resource resource = applicationContext.getResource("classpath:openaire_profile.xml");
                Publication indexResponse = null;
                Element record = (Element) nodes.item(0);
                String recordID = record.getElementsByTagName("dri:objIdentifier").item(0).getTextContent();

                dBuilder = dbFactory.newDocumentBuilder();
                doc = dBuilder.newDocument();

                indexResponse = index.getPublication(recordID);
                if (indexResponse!=null) {

                    if (indexResponse.getUrl().contains("pdfs/media/pdfs"))
                        indexResponse.setUrl(indexResponse.getUrl().replace("pdfs/media/pdfs", "pdfs"));

                    Element newNode = (Element) record.getElementsByTagName("oaf:result").item(0);
                    Element indexInfoElement = doc.createElement("indexinfo");

                    Attr openaireAttrNode = doc.createAttribute("id");
                    openaireAttrNode.setValue(indexResponse.getOpenaireId());

                    Text hashKeyText = doc.createTextNode(indexResponse.getHashValue());
                    Text mimeTypeText = doc.createTextNode(indexResponse.getMimeType());
                    Text urlText = doc.createTextNode(indexResponse.getUrl());

                    Element hashKeyElement = doc.createElement("hashkey");
                    Element mimeTypeElement = doc.createElement("mimetype");
                    Element urlElement = doc.createElement("url");

                    hashKeyElement.appendChild(hashKeyText);
                    mimeTypeElement.appendChild(mimeTypeText);
                    urlElement.appendChild(urlText);

                    indexInfoElement.setAttributeNode(openaireAttrNode);
                    indexInfoElement.appendChild(hashKeyElement);
                    indexInfoElement.appendChild(mimeTypeElement);
                    indexInfoElement.appendChild(urlElement);


                    String indexInfoElementString = elementToString(indexInfoElement);

                    appendXmlFragment(dBuilder, newNode, indexInfoElementString);

                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    StreamResult result = new StreamResult(new StringWriter());
                    DOMSource source = new DOMSource(newNode);
                    transformer.transform(source, result);

                    SolrInputDocument solrInputDocument = new SolrInputDocument();
                    Map<String, Object> indexedFields = null;
                    String xmlOutput = nodeToString(nodes.item(0));


                    try {
                        InputStream is = resource.getInputStream();
                        if(!ContentIndexing.hasBestLicense(is,xmlOutput)) {
                            //No need to continue with this.. we need only the Open Access and Embargo ones
                            fileToParse.delete();
                            continue;
                        }

                        indexedFields = ContentIndexing.indexFields(is, xmlOutput);

                    } catch (ParserConfigurationException e) {
                        logger.error(this.getClass().toString() + ": Parser Configuration exception", e);
                    } catch (XPathExpressionException e) {
                        logger.error(this.getClass().toString() + ": XPath Expression exception", e);
                    }

                    solrInputDocument.setField("__indexrecordidentifier", recordID);
                    solrInputDocument.setField("__fulltext", new String[]{});

                    if (indexedFields != null) {
                        for (Map.Entry<String, Object> p : indexedFields.entrySet()) {
                            if (p.getKey().equalsIgnoreCase("resultdateofacceptance")) {
                                Date date = null;

                                String[] dateOfAcceptance = p.getValue().toString().split("-");
                                SimpleDateFormat valueDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                                TimeZone UTC = TimeZone.getTimeZone("UTC");
                                valueDateFormat.setTimeZone(UTC);
                                try {
                                    switch (dateOfAcceptance.length) {
                                        case 1:
                                            if (!dateOfAcceptance[0].trim().isEmpty()) {
                                                SimpleDateFormat yearFormat = new SimpleDateFormat("YYYY");
                                                yearFormat.setTimeZone(UTC);
                                                date = yearFormat.parse(dateOfAcceptance[0].trim());
                                            }
                                            break;
                                        case 2:
                                            SimpleDateFormat yearMonthFormat = new SimpleDateFormat("YYYY-MM");
                                            yearMonthFormat.setTimeZone(UTC);
                                            date = yearMonthFormat.parse(dateOfAcceptance[0] + "-" + dateOfAcceptance[1]);
                                            break;
                                        case 3:
                                            SimpleDateFormat yearMonthDayFormat = new SimpleDateFormat("YYYY-MM-dd");
                                            yearMonthDayFormat.setTimeZone(UTC);
                                            date = yearMonthDayFormat.parse(dateOfAcceptance[0] + "-" + dateOfAcceptance[1] + "-" + dateOfAcceptance[2]);
                                            break;
                                        default:
                                            break;
                                    }
                                    if (date != null) {
                                        solrInputDocument.addField(p.getKey(), valueDateFormat.format(date));
                                    }
                                } catch (Exception e) {

                                }
                            } else {
                                solrInputDocument.addField(p.getKey(), p.getValue());
                            }
                        }
                    }

                    solrInputDocument.setField("__result", xmlOutput);

                    String documentIndexInfo = "";
                    if (indexResponse != null) {
                        solrInputDocument.setField("hashvalue", indexResponse.getHashValue());
                        solrInputDocument.setField("mimetype", indexResponse.getMimeType());
                        solrInputDocument.setField("fulltext", indexResponse.getUrl());
                        documentIndexInfo = recordID
                                + " contains fulltext with hashkey " + indexResponse.getHashValue()
                                + " at " + indexResponse.getUrl() + "\n";
                    } else {
                        documentIndexInfo = recordID + " does not contain fulltext.\n";
                    }

                    if (solrInputDocument.getField("resultbestlicense")
                            .getValue()
                            .toString()
                            .contains("Open Access")
                            || solrInputDocument.getField("resultbestlicense")
                            .getValue()
                            .toString()
                            .contains("Embargo")) {
                        logger.info("Saving "+filename + " to solr");
                        solrQueue.put(solrInputDocument);
                    }

                }

                fileToParse.delete();

            } catch (IOException e) {
                logger.info(e.getMessage());
            } catch (ParserConfigurationException e) {
                logger.info(e.getMessage());
            } catch (SAXException e) {
                logger.error("Append error");
            } catch (TransformerException e) {
                logger.error(e.getMessage());
            } catch (InterruptedException e) {
                logger.error("Failed to parse object from the queue");
            }
        }

        try {
            solrQueue.put(null);
            logger.info("Consumer is out..");
        } catch (InterruptedException e) {
           logger.error(e.getMessage());
        }


    }



    private String nodeToString(Node printingNode) {
        StringWriter sw = new StringWriter();
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.transform(new DOMSource(printingNode), new StreamResult(sw));
        } catch (TransformerException te) {
           logger.error("nodeToString Transformer Exception");
        }
        return sw.toString();
    }

    private String elementToString(Element element){
        Transformer transformer = null;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(element);
            transformer.transform(source, result);

           return result.getWriter().toString();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        return "empty";
    }

    private String documentToString(Document doc){
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }

    public static void appendXmlFragment(DocumentBuilder docBuilder, Node parent,String fragment) throws IOException, SAXException {
        Document doc = parent.getOwnerDocument();
        Node fragmentNode = docBuilder.parse(
                new InputSource(new StringReader(fragment)))
                .getDocumentElement();
        fragmentNode = doc.importNode(fragmentNode, true);
        parent.appendChild(fragmentNode);
    }

}