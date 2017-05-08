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

    static Map<String, List<String>> indexFields(InputStream is, String xml) throws ParserConfigurationException, XPathExpressionException, IOException, SAXException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document opeinaireServiceDoc = dbf.newDocumentBuilder().parse(is);
        Map<String, List<String>> indexedFields = new HashMap<>();
        String fieldName;
        String expression;
        NodeList fields = (NodeList) xpath.evaluate("//RESOURCE_PROFILE/BODY/STATUS/LAYOUTS/LAYOUT/FIELDS/FIELD[@indexable=\"true\"]", opeinaireServiceDoc, XPathConstants.NODESET);

        for (int i = 0; i < fields.getLength(); i++) {
            Node field = fields.item(i);

            fieldName = (String) xpath.evaluate("@name", field, XPathConstants.STRING);
            expression = (String) xpath.evaluate("@xpath", field, XPathConstants.STRING);

            if (!fieldName.isEmpty() && !expression.isEmpty()) {
                Map.Entry<String, List<String>> mapEntry = parseXml(xml, fieldName, expression);
                if (mapEntry != null && mapEntry.getValue().size() > 0) {
                    indexedFields.put(mapEntry.getKey(), mapEntry.getValue());
                }
            }
        }
        return indexedFields;
    }

    private static Map.Entry<String, List<String>> parseXml(String xml, String field, String expression)
            throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

        Map.Entry<String, List<String>> entry = new AbstractMap.SimpleEntry<>(field, new ArrayList<>());
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        String value = (String) xpath.evaluate(expression, doc, XPathConstants.STRING);

        if (value == null || value.isEmpty()) {
            NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i).getNodeValue() != null && !nodes.item(i).getNodeValue().isEmpty())
                    entry.getValue().add(nodes.item(i).getNodeValue());
            }
        } else {
            entry.getValue().add(value);
        }
        return entry;
    }
}
