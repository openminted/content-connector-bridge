package eu.openminted.content.bridge;

import eu.openminted.content.index.IndexPublication;
import eu.openminted.content.index.entities.Publication;
import eu.openminted.content.openaire.OpenAireSolrClient;
import org.apache.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.Resource;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

public class ParseDocumentTask implements Runnable {
    private static Logger log = Logger.getLogger(ParseDocumentTask.class.getName());

    private ApplicationContext applicationContext;
    private String xml;
    private SAXParser saxParser;
    private DocumentBuilder builder;
    private Transformer transformer;
    private ContentBridgingHandler handler;
    private IndexPublication index;
    private OpenAireSolrClient localSolrClient;
    private long initiationTime;

    public ParseDocumentTask(ApplicationContext applicationContext,
                             String xml, SAXParser saxParser, DocumentBuilder builder,
                             Transformer transformer, IndexPublication index,
                             OpenAireSolrClient localSolrClient) {
        this.applicationContext = applicationContext;
        this.xml = xml;
        this.saxParser = saxParser;
        this.builder = builder;
        this.transformer = transformer;
        this.index = index;
        this.localSolrClient = localSolrClient;
        this.handler = new ContentBridgingHandler();
        this.initiationTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        boolean hasAbstract = false;
        Resource resource = applicationContext.getResource("classpath:openaire_profile.xml");

        try {
            saxParser.parse(new InputSource(new StringReader(xml)), handler);

            if (handler.getDescription() != null
                    && !handler.getDescription().isEmpty()) {
                hasAbstract = true;
            }

            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            Publication indexResponse = null;

            if (index.containsPublication(handler.getIdentifier())) {

                indexResponse = index.getPublication(handler.getIdentifier());

                //todo: Remove the following lines after correction the url string
                if (indexResponse.getUrl().contains("pdfs/media/pdfs"))
                    indexResponse.setUrl(indexResponse.getUrl().replace("pdfs/media/pdfs", "pdfs"));

                Node node = doc.getElementsByTagName("oaf:result").item(0);
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

                node.appendChild(indexInfoElement);
            }

            if (hasAbstract || index.containsPublication(handler.getIdentifier())) {
                // Create the new xml with the additional elements
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                StreamResult result = new StreamResult(new StringWriter());
                DOMSource source = new DOMSource(doc);
                transformer.transform(source, result);

                String xmlOutput = result.getWriter().toString();

                SolrInputDocument solrInputDocument = new SolrInputDocument();
                Map<String, Object> indexedFields = null;

                try {
                    InputStream is = resource.getInputStream();
                    indexedFields = ContentIndexing.indexFields(is, xmlOutput);

                } catch (ParserConfigurationException e) {
                    log.error(this.getClass().toString() + ": Parser Configuration exception", e);
                } catch (XPathExpressionException e) {
                    log.error(this.getClass().toString() + ": XPath Expression exception", e);
                }

                solrInputDocument.setField("__indexrecordidentifier", handler.getIdentifier());
                solrInputDocument.setField("__fulltext", new String[]{});
//                for (Map.Entry<String, Object> f : solrDocument.entrySet()) {
//                    // field "version" will be recreated in the new index
//                    if (f.getKey().equals("_version_")) continue;
//                    solrInputDocument.setField(f.getKey(), f.getValue());
//                }

                if (indexedFields != null)
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
                solrInputDocument.setField("__result", xmlOutput);

                String documentIndexInfo = "";
                if (indexResponse != null) {
                    solrInputDocument.setField("hashvalue", indexResponse.getHashValue());
                    solrInputDocument.setField("mimetype", indexedFields.get("mimetype"));//indexResponse.getMimeType());
                    solrInputDocument.setField("fulltext", indexResponse.getUrl());
                    documentIndexInfo = handler.getIdentifier()
                            + " contains fulltext with hashkey " + indexResponse.getHashValue()
                            + " at " + indexResponse.getUrl() + "\n";
                } else {
                    documentIndexInfo = handler.getIdentifier() + " does not contain fulltext.\n";
                }

                if (solrInputDocument.getField("resultrights")
                        .getValue()
                        .toString()
                        .contains("Open Access")
                        || solrInputDocument.getField("resultrights")
                        .getValue()
                        .toString()
                        .contains("Embargo")) {

//                    log.info(solrInputDocument.getField("resultrights").getValue().toString());
                    localSolrClient.add(solrInputDocument);
                }
            }
            long finishedTime = System.currentTimeMillis() - this.initiationTime;
            log.info("Finished parsing xml " + handler.getIdentifier() + " in " + finishedTime + " milis");

        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }
}
