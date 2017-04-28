package eu.openminted.content.mocks;

import eu.openminted.omtdcache.CacheDataID;
import eu.openminted.omtdcache.CacheDataIDMD5;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/***
 * This is a mocking index object
 */
public class IndexImpl implements Index {
    private static Logger log = Logger.getLogger(IndexImpl.class.getName());
    private static Map<String, IndexResponse> index;
    CacheDataID cacheDataIDProvider;

    private IndexImpl() {
        index = new HashMap<>();
        cacheDataIDProvider = new CacheDataIDMD5();
    }

    public static IndexImpl getIndexInstance() {
        return IndexInstance.instance;
    }

    @Override
    public boolean containsId(String identifier) {
        return index.containsKey(identifier);
    }

    @Override
    public IndexResponse getOrTryAddHashId(Properties properties) {
        String identifier = properties.getProperty("identifier");
        String fulltext = properties.getProperty("fulltext");
        String mimetype = properties.getProperty("mimetype");

        if (index.containsKey(identifier)) return index.get(identifier);

        IndexResponse indexResponse = null;

        if (fulltext == null
                || fulltext.isEmpty())
            fulltext = "http://adonis.athenarc.gr/pdfs/" + identifier + ".pdf";

        if (mimetype == null
                || mimetype.isEmpty())
            mimetype = "application/pdf";

        try (InputStream inputStream = new URL(fulltext).openStream()) {
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = br.readLine()) != null) {
                stringBuilder.append(line);
            }
            br.close();
            String hashKey = cacheDataIDProvider.getID(stringBuilder.toString().getBytes());

            indexResponse = new IndexResponse();
            indexResponse.setOpenaAireId(identifier);
            indexResponse.setHashKey(hashKey);
            indexResponse.setMimeType(mimetype);
            indexResponse.setUrl(fulltext);

            index.put(identifier, indexResponse);
            indexResponse = index.get(identifier);
        } catch (Exception e) {
            log.error("getOrTryAddHashId error opening stream", e);
        }
        return indexResponse;
    }

    private static class IndexInstance {
        static final IndexImpl instance = new IndexImpl();
    }

}
