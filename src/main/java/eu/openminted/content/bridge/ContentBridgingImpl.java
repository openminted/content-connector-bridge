package eu.openminted.content.bridge;

import eu.openminted.content.connector.Query;
import eu.openminted.content.openaire.OpenAireSolrClient;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Component
public class ContentBridgingImpl implements ContentBridging {
    private static Logger log = Logger.getLogger(ContentBridgingImpl.class.getName());

    private String defaultCollection;

    @org.springframework.beans.factory.annotation.Value("${services.openaire.getProfile}")
    private String getProfileUrl;

    @org.springframework.beans.factory.annotation.Value("${solr.hosts}")
    private String hosts;

    @org.springframework.beans.factory.annotation.Value("${solr.local.host}")
    private String localHost;

    @org.springframework.beans.factory.annotation.Value("${solr.query.limit}")
    private String queryLimit;

    private int limit;

    @org.springframework.beans.factory.annotation.Value("${solr.query.output.field}")
    private String queryOutputField;

    @PostConstruct
    void init() {
        try {
            limit = Integer.parseInt(queryLimit);
            System.out.println("\n\n\n\n\n\n" + limit + "\n\n\n\n\n");
        } catch (NumberFormatException e) {
            log.error("ContentBridging: Wrong format of limit number.", e);
            limit = 0;
        } catch (Exception e) {
            log.error("ContentBridging: Exception during reading limit number.", e);
            limit = 0;
        }
        setDefaultConnection();
    }

    @Override
    public void bridge(Query query) {
        try {
            OpenAireSolrClient client = new OpenAireSolrClient();
            client.setDefaultCollection(defaultCollection);
            client.setHosts(hosts);
            client.setQueryLimit(limit);
            try {
                client.fetchMetadata(query,
                        new ContentBridgingStreamingResponseCallback(queryOutputField, localHost));
            } catch (IOException e) {
                log.info("Fetching metadata has been interrupted. See debug for details!");
                log.debug("ContentBridging.bridge", e);
            }
        } catch (SAXException | JAXBException | ParserConfigurationException e) {
            log.error("Error at bridge", e);
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        }
    }

    /***
     * Sets defaultConnection by querying services.openaire.eu
     */
    private void setDefaultConnection() {
        InputStream inputStream;
        URLConnection con;
        try {
            URL url = new URL(getProfileUrl);
            Authenticator.setDefault(new Authenticator() {

                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication("admin", "driver".toCharArray());
                }
            });

            try {
                con = url.openConnection();
                inputStream = con.getInputStream();
            } catch (SSLHandshakeException e) {

                // Create a trust manager that does not validate certificate chains
                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }};

                // Install the all-trusting trust manager
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                con = url.openConnection();
                inputStream = con.getInputStream();
            }


            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            XPath xpath = XPathFactory.newInstance().newXPath();

            Document doc = dbf.newDocumentBuilder().parse(inputStream);
            String value = (String) xpath.evaluate("//RESOURCE_PROFILE/BODY/CONFIGURATION/SERVICE_PROPERTIES/PROPERTY[@key=\"mdformat\"]/@value", doc, XPathConstants.STRING);

            if (value != null && !value.isEmpty())
                defaultCollection = value.toUpperCase() + "-index-openaire";

            log.debug("Updating defaultCollection to '" + defaultCollection + "'");
        } catch (IOException e) {

            log.error("Error applying SSLContext - IOException", e);
        } catch (NoSuchAlgorithmException e) {

            log.error("Error applying SSLContext - NoSuchAlgorithmException", e);
        } catch (KeyManagementException e) {

            log.error("Error applying SSLContext - KeyManagementException", e);
        } catch (SAXException e) {

            log.error("Error parsing value - SAXException", e);
        } catch (XPathExpressionException e) {

            log.error("Error parsing value - XPathExpressionException", e);
        } catch (ParserConfigurationException e) {

            log.error("Error parsing value - ParserConfigurationException", e);
        }
    }

    public String getDefaultCollection() {
        return defaultCollection;
    }

    public String getLocalHost() {
        return localHost;
    }

    public String getQueryOutputField() {
        return queryOutputField;
    }
}
