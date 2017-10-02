package eu.openminted.content.bridge;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

class ContentIndexing {
    private static DocumentBuilderFactory dbf;
    private static XPath xpath;

    static {
        dbf = DocumentBuilderFactory.newInstance();
        xpath = XPathFactory.newInstance().newXPath();
    }

    /**
     * Parses the openaire_profile.xml file and retrieves values (via xpath) from the publication
     * to add it as solr document field when inserting document into Solr index
     * @param is the openaire_profile.xml file as input stream
     * @param xml the publication xml as string
     * @return a map of fields
     * @throws ParserConfigurationException
     * @throws XPathExpressionException
     * @throws IOException
     * @throws SAXException
     */
    static Map<String, Object> indexFields(InputStream is, String xml) throws ParserConfigurationException, XPathExpressionException, IOException, SAXException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document opeinaireServiceDoc = dbf.newDocumentBuilder().parse(is);
        Map<String, Object> indexedFields = new HashMap<>();
        String fieldName = "";
        String expression = "";
        NodeList fields = (NodeList) xpath.evaluate("//RESOURCE_PROFILE/BODY/STATUS/LAYOUTS/LAYOUT/FIELDS/FIELD[@indexable=\"true\"]", opeinaireServiceDoc, XPathConstants.NODESET);

        for (int i = 0; i < fields.getLength(); i++) {
            Node field = fields.item(i);

            fieldName = (String) xpath.evaluate("@name", field, XPathConstants.STRING);
            expression = (String) xpath.evaluate("@xpath", field, XPathConstants.STRING);

            if (!fieldName.isEmpty() && !expression.isEmpty()) {
                Map.Entry<String, Object> mapEntry = parseXml(xml, fieldName, expression);
                if (mapEntry != null && mapEntry.getValue() != null) {
                    indexedFields.put(mapEntry.getKey(), mapEntry.getValue());
                }
            }
        }
        return indexedFields;
    }

    /**
     * Reads the xml to get value of solr document's field
     * @param xml the xml to read
     * @param field the name of the field to find value
     * @param expression xpath expression that will retrieve the value from the xml
     * @return a map entry with the name of the field and the corresponding value
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws XPathExpressionException
     */
    private static Map.Entry<String, Object> parseXml(String xml, String field, String expression)
            throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

        Map.Entry<String, Object> entry;
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        String value = (String) xpath.evaluate(expression, doc, XPathConstants.STRING);

        if (value == null || value.isEmpty()) {
            NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
            List<String> values = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i).getNodeValue() != null && !nodes.item(i).getNodeValue().isEmpty())
                    values.add(nodes.item(i).getNodeValue());
            }
            entry = new AbstractMap.SimpleEntry<>(field, values);
        } else {
            entry = new AbstractMap.SimpleEntry<>(field, value);
        }
        return entry;
    }
}
