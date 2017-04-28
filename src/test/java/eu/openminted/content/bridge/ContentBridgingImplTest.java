package eu.openminted.content.bridge;

import eu.openminted.content.connector.Query;
import eu.openminted.content.connector.SearchResult;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.HashMap;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ContentBridgingConfiguration.class})
public class ContentBridgingImplTest {

    @Autowired
    private ContentBridgingImpl contentBridging;

    @Test
    @Ignore
    public void bridge() throws Exception {

        while (contentBridging.getDefaultCollection() == null || contentBridging.getDefaultCollection().isEmpty()) {
            Thread.sleep(1000);
        }

        Query query = new Query();
        query.setParams(new HashMap<>());

        query.getParams().put("sort", new ArrayList<>());
        query.getParams().get("sort").add("__indexrecordidentifier asc");
        query.setKeyword("digital");
        query.getParams().put("resultrights", new ArrayList<>());
        query.getParams().get("resultrights").add("Open Access");

        contentBridging.bridge(query);
    }

    @Test
//    @Ignore
    public void select() throws Exception {

        Query query = new Query();
        query.setParams(new HashMap<>());
        query.setKeyword("*:*");


//        query.getParams().put("sort", new ArrayList<>());
//        query.getParams().get("sort").add("__indexrecordidentifier asc");
//        query.getParams().put("sort", new ArrayList<>());
//        query.getParams().get("sort").add("__indexrecordidentifier asc");
//        query.setKeyword("digital");
//        query.getParams().put("resultrights", new ArrayList<>());
//        query.getParams().get("resultrights").add("Open Access");

        SearchResult result = contentBridging.search(query);
        System.out.println("\nFacets " + result.getFacets().get(0).getLabel() + "\n");
        System.out.println("\nTotal hits " + result.getTotalHits() + "\n");

//        SolrClient solrClient = new HttpSolrClient.Builder(contentBridging.getLocalHost()).build();
//        SolrQuery solrQuery = new SolrQuery("*:*");
//        solrQuery.setStart(0);
//        solrQuery.setRows(100);
//
//        QueryResponse queryResponse = solrClient.query(solrQuery);
//        int count = 0;
//        for (SolrDocument document : queryResponse.getResults()) {
//            String doc = document
//                    .getFieldValues(contentBridging.getQueryOutputField()).toArray()[0].toString();
//            if (doc.contains("indexinfo")) {
//                System.out.println(doc);
//                count++;
//            }
//        }
//        System.out.println("\nTotal count of indexed " + count + "\n");
    }
}