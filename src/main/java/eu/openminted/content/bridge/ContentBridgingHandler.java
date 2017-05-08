package eu.openminted.content.bridge;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ContentBridgingHandler extends DefaultHandler {
    private String value = "";
    private String identifier = "";
    private String format = "";
    private String description = "";
    private String fulltext = "";

    private static Logger log = Logger.getLogger(ContentBridgingHandler.class.getName());
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.equalsIgnoreCase("dri:objIdentifier")) {
            value = "";
        } else if (qName.equalsIgnoreCase("format")) {
            value = "";
        } else if (qName.equalsIgnoreCase("description")) {
            value = "";
        } else if (qName.equalsIgnoreCase("fulltext")) {
            value = "";
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equalsIgnoreCase("dri:objIdentifier")) {
            identifier = value;
        } else if (qName.equalsIgnoreCase("format")) {
            format = value;
        } else if (qName.equalsIgnoreCase("description")) {
            description = value;
        } else if (qName.equalsIgnoreCase("fulltext")) {
            fulltext = value;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        value = new String(ch, start, length);
    }

    public String getIdentifier() {
        return identifier;
    }

    String getFormat() {
        return format;
    }

    String getDescription() {
        return description;
    }

    String getFulltext() {
        return fulltext;
    }
}
