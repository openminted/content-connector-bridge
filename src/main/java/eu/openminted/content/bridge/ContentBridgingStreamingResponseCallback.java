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
import java.util.Map;
import java.util.Properties;

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
        this.openAireSolrClient = new OpenAireSolrClient(solrClientType, host, defaultCollection, 0);
        this.resource = resource;
        this.index = index;
    }

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

            if (index.containsPublication(handler.getIdentifier())) {

                Publication indexResponse = index.getPublication(handler.getIdentifier());

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

            log.debug("\nPDF for " + handler.getIdentifier() + " exists: " + index.containsPublication(handler.getIdentifier()) + "\n");

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
                        solrInputDocument.addField(p.getKey(), p.getValue());
                    }
                solrInputDocument.setField(outputField, xmlOutput);

                openAireSolrClient.add(solrInputDocument);
            }
        } catch (SAXException | IOException | TransformerException e) {
            log.error("ContentBridgingStreamingResponseCallback.streamSolrDocument", e);
        }
    }

    @Override
    public void streamDocListInfo(long l, long l1, Float aFloat) {

    }
}
