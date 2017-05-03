package eu.openminted.content.bridge;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import eu.dnetlib.elasticsearch.ElasticSearchConfiguration;
import eu.dnetlib.elasticsearch.ElasticSearchConnection;
import eu.dnetlib.elasticsearch.entities.Publication;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Get;

@SpringBootApplication
public class ContentIndexTest {
	
	private static final Logger log = LoggerFactory.getLogger(ContentIndexTest.class);

	public static void main(String args[]) {
		SpringApplication.run(ContentIndexTest.class);
	}
	
	@Bean
	public ElasticSearchConfiguration esConfig() throws IOException, Exception {
		return( ElasticSearchConfiguration.getInstance());
	}
	
	@Bean
	public JestClient getClient(ElasticSearchConfiguration esConfig) {
		ElasticSearchConnection configES = new ElasticSearchConnection(esConfig.getHost(), esConfig.getPort());
        JestClient client = configES.client();
        return client;
	}
	
	@Bean
	public CommandLineRunner run(ElasticSearchConfiguration esConfig, JestClient client)  {
		return args -> {		
				 			                
		    String index = esConfig.getIndex();
		    String document = esConfig.getDocumentType();
		        
			
		    Get get = new Get.Builder(index, "aa1e5455-51d5-4e30-87ad-a40de74029c3").type(document).build();
		    JestResult result = client.execute(get);
		    log.info("Response code ::" + result.getResponseCode());		   
		    Publication pub = result.getSourceAsObject(Publication .class);
		    log.info(pub.toString());		
			
		};
	}

}
