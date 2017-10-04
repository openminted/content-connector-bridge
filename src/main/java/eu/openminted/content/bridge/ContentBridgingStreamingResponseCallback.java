package eu.openminted.content.bridge;

import eu.openminted.content.index.IndexPublication;
import eu.openminted.content.index.entities.Publication;
import eu.openminted.content.openaire.OpenAireSolrClient;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.core.io.Resource;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.*;
import javax.xml.transform.*;
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
import java.util.Properties;
import java.util.TimeZone;

public class ContentBridgingStreamingResponseCallback extends StreamingResponseCallback {
    private static Logger log = Logger.getLogger(ContentBridgingStreamingResponseCallback.class.getName());
    private String outputField;
    private ContentBridgingHandler handler;
    private SAXParser saxParser;
    private DocumentBuilder builder;
    private Transformer transformer;
    private OpenAireSolrClient openAireSolrClient;
    private Resource resource;
    private IndexPublication index;
    private long count;

    ContentBridgingStreamingResponseCallback(IndexPublication index, String solrClientType, String field, String host, String defaultCollection, Resource resource)
            throws JAXBException, ParserConfigurationException, SAXException, TransformerConfigurationException {
        this.outputField = field;
        this.handler = new ContentBridgingHandler();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        this.saxParser = factory.newSAXParser();
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setIgnoringComments(true);
        this.builder = domFactory.newDocumentBuilder();
        this.transformer = TransformerFactory.newInstance().newTransformer();
        this.openAireSolrClient = new OpenAireSolrClient(solrClientType, host, defaultCollection);
        this.resource = resource;
        this.index = index;
        this.count = 0;
    }

    /**
     * Reads the solrDocument and creates a new SolrInputDocument
     * that is inserted into the new Solr index.
     *
     * @param solrDocument
     */
    @Override
    public void streamSolrDocument(SolrDocument solrDocument) {
        try {
            String xml = solrDocument.getFieldValue(outputField).toString()
                    .replaceAll("\\[|\\]", "");
            xml = xml.trim();

            saxParser.parse(new InputSource(new StringReader(xml)), handler);

            boolean hasAbstract = false;
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

                for (Map.Entry<String, Object> f : solrDocument.entrySet()) {
                    // field "version" will be recreated in the new index
                    if (f.getKey().equals("_version_")) continue;
                    solrInputDocument.setField(f.getKey(), f.getValue());
                }

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
                solrInputDocument.setField(outputField, xmlOutput);

                String documentIndexInfo = "";
                if (indexResponse != null) {
                    solrInputDocument.setField("hashvalue", indexResponse.getHashValue());
                    solrInputDocument.setField("mimetype", indexResponse.getMimeType());
                    solrInputDocument.setField("fulltext", indexResponse.getUrl());
                    documentIndexInfo = handler.getIdentifier()
                            + " contains fulltext with hashkey " + indexResponse.getHashValue()
                            + " of type " + indexResponse.getMimeType()
                            + " at " + indexResponse.getUrl() + "\n";
                } else {
                    documentIndexInfo = handler.getIdentifier() + " does not contain fulltext.\n";
                }

                if (solrInputDocument.getField("resultrights")
                        .getValue()
                        .toString()
                        .contains("Open Access")) {

                    log.info(solrInputDocument.getField("resultrights").getValue().toString());

//                    openAireSolrClient.add(solrInputDocument);
                    count++;
                    log.info("Store contains " + count + " documents.\n" + documentIndexInfo);
                }
            }
        } catch (SAXException | IOException | TransformerException e) {
            log.error("ContentBridgingStreamingResponseCallback.streamSolrDocument", e);
        }
    }

    @Override
    public void streamDocListInfo(long l, long l1, Float aFloat) {

    }
}
