package eu.openminted.content.bridge;

import eu.openminted.content.connector.Query;
import eu.openminted.content.connector.SearchResult;
import eu.openminted.registry.core.domain.Facet;
import eu.openminted.registry.core.domain.Value;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        query.getParams().put("resultrights", new ArrayList<>());
        query.getParams().get("resultrights").add("Open Access");
        query.getParams().get("resultrights").add("Embargo");
        query.getParams().put("__indexrecordidentifier", new ArrayList<>());
        query.getParams().get("__indexrecordidentifier").add("od________18::7ff13d895bc479a9143760e3ed68b6ed");
        query.setKeyword("*:*");

        query.setTo(1000);

//        query.getParams().put("resultrights", new ArrayList<>());
//        query.getParams().get("resultrights").add("Open Access");

        contentBridging.bridge(query);
    }

    @Test
//    @Ignore
    public void select() throws Exception {

        Query query = new Query();
        query.setParams(new HashMap<>());
        query.setKeyword("*:*");
        query.getParams().put("sort", new ArrayList<>());
        query.getParams().get("sort").add("__indexrecordidentifier asc");
        query.getParams().put("resultrights", new ArrayList<>());
        query.getParams().get("resultrights").add("Open Access");
        query.getParams().get("resultrights").add("Closed Access");

        List<String> facets = new ArrayList<>();
        facets.add("resultrights");
        facets.add("resultrightsid");
        facets.add("resultdateofacceptance");
        query.setFacets(facets);

        SearchResult searchResult = contentBridging.search(query);

        for (Facet facet : searchResult.getFacets()) {
            System.out.println("facet:{" + facet.getLabel() + "[");
            for (Value value : facet.getValues()) {
                System.out.println("\t{" + value.getValue() + ":" + value.getCount() + "}");
            }
            System.out.println("]}");
        }
        System.out.println("reading " + searchResult.getPublications().size() +
                " publications from " + searchResult.getFrom() +
                " to " + searchResult.getTo() +
                " out of " + searchResult.getTotalHits() + " total hits.");
    }
}