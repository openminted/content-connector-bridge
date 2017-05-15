package eu.openminted.content.bridge;

import eu.openminted.content.index.IndexConfiguration;
import eu.openminted.content.index.IndexPublication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ComponentScan("eu.openminted.content.bridge")
public class ContentBridgingConfiguration {
    @Bean
    private IndexConfiguration getEsConfig() throws Exception {
        return IndexConfiguration.getInstance();
    }

    @Bean
    public IndexPublication getIndex() throws Exception {
        IndexPublication index = new IndexPublication(getEsConfig());
        return index;
    }
}
