package eu.openminted.content.bridge;

import eu.openminted.content.mocks.Index;
import eu.openminted.content.mocks.IndexImpl;
import eu.openminted.content.mocks.IndexResponse;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

public class ContentBridgingStreamingResponseCallback extends StreamingResponseCallback {
    private static Logger log = Logger.getLogger(ContentBridgingStreamingResponseCallback.class.getName());
    private String outputField;
    private ContentBridgingHandler handler;
    private SAXParser saxParser;
    private DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
    private DocumentBuilder builder;
    private Transformer transformer;
    private HttpSolrClient solrClient;

    ContentBridgingStreamingResponseCallback(String field, String host) throws JAXBException, ParserConfigurationException, SAXException, TransformerConfigurationException {
        outputField = field;
        handler = new ContentBridgingHandler();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        saxParser = factory.newSAXParser();
        domFactory.setIgnoringComments(true);
        builder = domFactory.newDocumentBuilder();
        transformer = TransformerFactory.newInstance().newTransformer();
        solrClient = new HttpSolrClient.Builder(host).build();
        solrClient.setRequestWriter(new BinaryRequestWriter());
    }

    @Override
    public void streamSolrDocument(SolrDocument solrDocument) {
        try {

            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + solrDocument.getFieldValue(outputField).toString()
                    .replaceAll("\\[|\\]", "");

            saxParser.parse(new InputSource(new StringReader(xml)), handler);

            Index index = IndexImpl.getIndexInstance();

            boolean hasAbstract = false;
            if (handler.getDescription() != null
                    && !handler.getDescription().isEmpty()) {
                hasAbstract = true;
            }

            IndexResponse indexResponse = null;
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            Properties properties = new Properties();
            properties.setProperty("identifier", handler.getIdentifier());
            properties.setProperty("fulltext", handler.getFulltext());
            properties.setProperty("mimetype", handler.getFormat());

            if ((indexResponse = index.getOrTryAddHashId(properties)) != null) {

                Node node = doc.getElementsByTagName("oaf:result").item(0);
                Element indexInfoElement = doc.createElement("indexinfo");

                Attr openaireAttrNode = doc.createAttribute("id");
                openaireAttrNode.setValue(indexResponse.getOpenaAireId());

                Text hashKeyText = doc.createTextNode(indexResponse.getHashKey());
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

            System.out.println("\nPDF for " + handler.getIdentifier() + " exists: " + index.containsId(handler.getIdentifier()) + "\n");

            if (hasAbstract || index.containsId(handler.getIdentifier())) {
                // Create the new xml with the additional elements
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                StreamResult result = new StreamResult(new StringWriter());
                DOMSource source = new DOMSource(doc);
                transformer.transform(source, result);

                String xmlOutput = result.getWriter().toString();

                SolrInputDocument solrInputDocument = new SolrInputDocument();
                for( Map.Entry<String, Object> f : solrDocument.entrySet()) {
                    if (f.getKey().equals("_version_")) continue;
                    if (!f.getKey().equals("__result"))
                        System.out.println("\n" + f.getKey() + " = " + f.getValue());
                    solrInputDocument.setField(f.getKey(), f.getValue()) ;
                }
                solrInputDocument.addField(outputField, xmlOutput);
                System.out.println(solrInputDocument);

                solrClient.add(solrInputDocument);
                solrClient.commit();
            }
        } catch (SAXException | IOException | TransformerException e) {
            log.error("ContentBridgingStreamingResponseCallback.streamSolrDocument", e);
        }
        catch (SolrServerException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void streamDocListInfo(long l, long l1, Float aFloat) {

    }
}
