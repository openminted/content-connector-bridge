package eu.openminted.content.bridge.service.rest;

import eu.openminted.content.bridge.ContentBridging;
import eu.openminted.content.connector.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentBridgingController {
    @Autowired
    ContentBridging contentBridging;

    @RequestMapping(value = "/content/bridge", method = RequestMethod.POST, headers = "Accept=application/json")
    public void bridge(Query query) {

        this.contentBridging.bridge(query);
    }
}
