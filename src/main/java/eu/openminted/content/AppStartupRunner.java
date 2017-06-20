package eu.openminted.content;

import eu.openminted.content.bridge.ContentBridgingImpl;
import eu.openminted.content.connector.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;

@Component
public class AppStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AppStartupRunner.class);
    @Autowired
    private ContentBridgingImpl contentBridging;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Your application started with option names : {}", args.getOptionNames());

        Query query = new Query();
        query.setParams(new HashMap<>());

        query.getParams().put("sort", new ArrayList<>());
        query.getParams().get("sort").add("__indexrecordidentifier asc");

        query.getParams().put("resultrights", new ArrayList<>());
        query.getParams().get("resultrights").add("Open Access");
        query.getParams().get("resultrights").add("Embargo");

        String keyword = "*:*";
        int limit = 100;

        // Reading application options
        try {
            if (args.containsOption("solr.query.limit")) {
                String value = args.getOptionValues("solr.query.limit").get(0);
                limit = Integer.parseInt(value);
            }

            if (args.containsOption("query"))
                keyword = args.getOptionValues("query").get(0);

        } catch (NumberFormatException e) {
            log.error("Illegal number format at a numeric option. Continuing with limit 10");
        }

        contentBridging.setQueryLimit(limit);
        query.setKeyword(keyword);

        contentBridging.bridge(query);
        log.info("Finished! You may terminate the app by pressing Ctrl + C");
    }
}