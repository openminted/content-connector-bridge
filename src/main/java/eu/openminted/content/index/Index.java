package eu.openminted.content.index;

import java.util.Properties;

public interface Index {
    boolean containsId(String identifier);

    IndexResponse getOrTryAddHashId(Properties properties);
}
