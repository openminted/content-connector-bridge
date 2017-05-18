package eu.openminted.content.bridge;

import eu.openminted.content.index.IndexConfiguration;
import eu.openminted.content.index.IndexPublication;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

@Configuration
@ComponentScan("eu.openminted.content.bridge")
public class ContentBridgingConfiguration {
    @Bean
    public IndexConfiguration getEsConfig() throws Exception {
        return IndexConfiguration.getInstance();
    }

    @Bean
    public IndexPublication getIndex() throws Exception {
        return new IndexPublication(getEsConfig());
    }

    @Bean
    public PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() throws IOException {
        final PropertyPlaceholderConfigurer ppc = new PropertyPlaceholderConfigurer();
        ppc.setLocations((Resource[]) ArrayUtils.addAll(
                new PathMatchingResourcePatternResolver().getResources("classpath*:application.properties"),
                new PathMatchingResourcePatternResolver().getResources("classpath*:test.properties")
                )
        );

        return ppc;
    }
}
