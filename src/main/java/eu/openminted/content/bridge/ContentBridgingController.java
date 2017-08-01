package eu.openminted.content.bridge;

import eu.openminted.content.index.IndexPublication;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.File;
import java.io.IOException;

@RestController
public class ContentBridgingController {
    private static Logger log = Logger.getLogger(ContentBridgingController.class.getName());

    @Autowired
    private ContentBridging contentBridging;

//    @RequestMapping(value="/bridge/insert", method=RequestMethod.POST)
//    @ResponseBody
//    public void insert(@RequestBody String entity){
//        contentBridging.bridge(entity);
//    }

    @PostMapping(value = "/bridge/insert", consumes = "multipart/form-data")
//    @RequestMapping(value="/bridge/insert", method=RequestMethod.POST, consumes = "multipart/form-data")
//    @ResponseBody
    public void insert(@RequestParam("file") MultipartFile zipfile){
        contentBridging.bridge(zipfile);
    }
}
