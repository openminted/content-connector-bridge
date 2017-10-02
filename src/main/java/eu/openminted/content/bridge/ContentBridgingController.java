package eu.openminted.content.bridge;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ContentBridgingController {
    private static Logger log = Logger.getLogger(ContentBridgingController.class.getName());

    @Autowired
    private ContentBridging contentBridging;

    @PostMapping(value = "/bridge/insert", consumes = "multipart/form-data")
    public void insert(@RequestParam("file") MultipartFile zipfile){
        contentBridging.bridge(zipfile);
    }
}
