package eu.openminted.content.bridge;

import eu.openminted.content.connector.Query;
import eu.openminted.content.connector.SearchResult;

public interface ContentBridging {
    void bridge(Query query);

    SearchResult search(Query query);
}
