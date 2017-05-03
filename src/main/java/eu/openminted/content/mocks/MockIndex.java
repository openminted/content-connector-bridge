package eu.openminted.content.mocks;

import java.util.Properties;

public interface MockIndex {
    boolean containsId(String identifier);

    IndexResponse getOrTryAddHashId(Properties properties);
}
