package eu.openminted.content.mocks;

import eu.openminted.content.bridge.IndexResponse;

import java.util.Properties;

public interface MockIndex {
    boolean containsId(String identifier);

    IndexResponse getOrTryAddHashId(Properties properties);
}
