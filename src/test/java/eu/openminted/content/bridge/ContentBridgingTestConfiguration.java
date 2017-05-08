package eu.openminted.content.bridge;

import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

@Configuration
public class ContentBridgingTestConfiguration {
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
