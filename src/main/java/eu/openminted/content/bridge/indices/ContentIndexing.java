package eu.openminted.content.bridge.indices;

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

public class ContentIndexing {
    private static DocumentBuilderFactory dbf;
    private static XPath xpath;

    static {
        dbf = DocumentBuilderFactory.newInstance();
        xpath = XPathFactory.newInstance().newXPath();
    }

    public static boolean hasBestLicense(InputStream is, String xml) throws ParserConfigurationException, XPathExpressionException, IOException, SAXException {

        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        String value = (String) xpath.evaluate("//*[local-name()='entity']/*[local-name()='result']/bestaccessright/@classname", doc, XPathConstants.STRING);

        if(value==null || value.isEmpty())
            return false;
        else
            return true;

    }


    public static Map<String, Object> indexFields(InputStream is, String xml) throws ParserConfigurationException, XPathExpressionException, IOException, SAXException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        XPath xpath = XPathFactory.newInstance().newXPath();
        Document opeinaireServiceDoc = dbf.newDocumentBuilder().parse(is);
        Map<String, Object> indexedFields = new HashMap<String,Object>();
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

    private static Map.Entry<String, Object> parseXml(String xml, String field, String expression)
            throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

        Map.Entry<String, Object> entry;
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        String value = (String) xpath.evaluate(expression, doc, XPathConstants.STRING);

        if (value == null || value.isEmpty()) {
            NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
            List<String> values = new ArrayList<String>();
            for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i).getNodeValue() != null && !nodes.item(i).getNodeValue().isEmpty())
                    values.add(nodes.item(i).getNodeValue());
            }
            entry = new AbstractMap.SimpleEntry<String, Object>(field, values);
        } else {
            entry = new AbstractMap.SimpleEntry<String, Object>(field, value);
        }
        return entry;
    }
}
