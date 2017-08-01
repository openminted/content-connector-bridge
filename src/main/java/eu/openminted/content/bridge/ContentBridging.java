package eu.openminted.content.bridge;

import eu.openminted.content.connector.Query;
import eu.openminted.content.connector.SearchResult;
import org.springframework.web.multipart.MultipartFile;

public interface ContentBridging {
    void bridge(Query query);

    void bridge(MultipartFile zipFile);

    SearchResult search(Query query);
}
