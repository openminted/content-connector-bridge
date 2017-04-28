package eu.openminted.content.bridge;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ContentBridgingHandler extends DefaultHandler {
    String value = "";
    String identifier = "";
    String format = "";
    String description = "";
    String fulltext = "";

    private static Logger log = Logger.getLogger(ContentBridgingHandler.class.getName());
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equalsIgnoreCase("dri:objIdentifier")) {
            identifier = value;
            value = "";
        } else if (qName.equalsIgnoreCase("format")) {
            format = value;
            value = "";
        } else if (qName.equalsIgnoreCase("description")) {
            description = value;
            value = "";
        } else if (qName.equalsIgnoreCase("fulltext")) {
            fulltext = value;
            value = "";
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        value = new String(ch, start, length);
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getFormat() {
        return format;
    }

    public String getDescription() {
        return description;
    }

    public String getFulltext() {
        return fulltext;
    }
}
