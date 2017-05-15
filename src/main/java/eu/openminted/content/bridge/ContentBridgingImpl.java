package eu.openminted.content.bridge;

import eu.openminted.content.connector.Query;
import eu.openminted.content.connector.SearchResult;
import eu.openminted.content.index.IndexConfiguration;
import eu.openminted.content.index.IndexPublication;
import eu.openminted.content.openaire.OpenAireSolrClient;
import eu.openminted.registry.domain.Facet;
import eu.openminted.registry.domain.Value;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
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
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContentBridgingImpl implements ContentBridging {
    private static Logger log = Logger.getLogger(ContentBridgingImpl.class.getName());

    private String defaultCollection;

    @org.springframework.beans.factory.annotation.Value("${services.openaire.getProfile}")
    private String getProfileUrl;

    @org.springframework.beans.factory.annotation.Value("${solr.remote.client.type}")
    private String remoteClientType;

    @org.springframework.beans.factory.annotation.Value("${solr.local.client.type}")
    private String localClientType;

    @org.springframework.beans.factory.annotation.Value("${solr.remote.hosts}")
    private String remoteHosts;

    @org.springframework.beans.factory.annotation.Value("${solr.local.hosts}")
    private String localHosts;

    @org.springframework.beans.factory.annotation.Value("${solr.local.default.collection}")
    private String localDefaultCollection;

    @org.springframework.beans.factory.annotation.Value("${solr.query.limit:0}")
    private Integer queryLimit;

    @org.springframework.beans.factory.annotation.Value("${solr.query.output.field}")
    private String queryOutputField;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private IndexConfiguration esConfig;

    @Autowired
    private IndexPublication index;

    private DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    private XPath xpath = XPathFactory.newInstance().newXPath();

    @PostConstruct
    void init() {
        setDefaultConnection();
    }

    @Override
    public void bridge(Query query) {
        try {
            OpenAireSolrClient client = new OpenAireSolrClient(remoteClientType, remoteHosts, defaultCollection, queryLimit);

            try {
                Resource resource = applicationContext.getResource("classpath:openaire_profile.xml");

                client.fetchMetadata(query,
                        new ContentBridgingStreamingResponseCallback(index, localClientType, queryOutputField, localHosts, localDefaultCollection, resource));
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

    @Override
    public SearchResult search(Query query) {
        final String FACET_DOCUMENT_TYPE_FIELD = "documentType";
        final String FACET_DOCUMENT_TYPE_LABEL = "Document Type";
        final String FACET_DOCUMENT_TYPE_COUNT_NAME = "fullText";

        if (query == null) {
            query = new Query("*:*", new HashMap<>(), new ArrayList<>(), 0, 1);
        } else if (query.getKeyword() == null || query.getKeyword().isEmpty()) {
            query.setKeyword("*:*");
        }
        query.setFrom(0);
        query.setTo(1);

        OpenAireSolrClient client = new OpenAireSolrClient(localClientType, localHosts, localDefaultCollection, queryLimit);
        SearchResult searchResult = new SearchResult();

        if (query.getFacets() == null) query.setFacets(new ArrayList<>());

        QueryResponse queryResponse = null;
        Map<String, Facet> facets = new HashMap<>();

        try {
            queryResponse = client.query(query);
            searchResult.setFrom((int) queryResponse.getResults().getStart());
            searchResult.setTo((int) queryResponse.getResults().getStart() + queryResponse.getResults().size());
            searchResult.setTotalHits((int) queryResponse.getResults().getNumFound());


            if (queryResponse.getFacetFields() != null) {
                for (FacetField facetField : queryResponse.getFacetFields()) {
                    Facet facet = buildFacet(facetField);
                    if (facet != null) {
                        if (!facets.containsKey(facet.getField())) {
                            facets.put(facet.getField(), facet);
                        }
                    }
                }
                // Facet Field documenttype does not exist in OpenAIRE, so we added it explicitly
                if (searchResult.getTotalHits() > 0) {
                    Facet documentTypeFacet = buildFacet(FACET_DOCUMENT_TYPE_FIELD, FACET_DOCUMENT_TYPE_LABEL, FACET_DOCUMENT_TYPE_COUNT_NAME, searchResult.getTotalHits());
                    facets.put(documentTypeFacet.getField(), documentTypeFacet);
                }
            }

            searchResult.setFacets(new ArrayList<>(facets.values()));
            searchResult.setPublications(new ArrayList<>());

            for (SolrDocument document : queryResponse.getResults()) {
                String doc = document.getFieldValues(queryOutputField).toArray()[0].toString();
                searchResult.getPublications().add(doc);
            }
        }
        catch (Exception e) {
            log.error("search.Exception", e);
        }
        return searchResult;
    }

    private Facet buildFacet(FacetField facetField) {

        Facet facet = new Facet();
        facet.setField(facetField.getName());
        facet.setLabel(facetField.getName());
        List<Value> values = new ArrayList<>();
        for (FacetField.Count count : facetField.getValues()) {
            if (count.getCount() == 0) continue;
            Value value = new Value();
            value.setValue(count.getName());
            value.setCount((int) count.getCount());
            values.add(value);
        }
        facet.setValues(values);
        return facet;
    }

    private Facet buildFacet(String field, String label, String countName, int countValue) {
        Facet facet = new Facet();
        facet.setLabel(label);
        facet.setField(field);

        List<Value> values = new ArrayList<>();
        Value value = new Value();
        value.setValue(countName);
        value.setCount(countValue);
        values.add(value);

        facet.setValues(values);
        return facet;
    }

    /***
     * Sets defaultConnection by querying services.openaire.eu
     */
    private void setDefaultConnection() {
        InputStream inputStream;
        try {
            inputStream = connectToOpenAireServices(new URL(getProfileUrl));
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

    private InputStream connectToOpenAireServices(URL url)
            throws IOException, NoSuchAlgorithmException, KeyManagementException {
        InputStream inputStream;
        URLConnection con;

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


        return inputStream;
    }

    String getDefaultCollection() {
        return defaultCollection;
    }

    public String getLocalHosts() {
        return localHosts;
    }

    public String getQueryOutputField() {
        return queryOutputField;
    }
}
