package eu.openminted.content.bridge.utils;

import java.io.File;
import java.io.FilenameFilter;

public class MyFilenameFilter implements FilenameFilter {

    private String filterName;

    public MyFilenameFilter(String filterName) {
	this.filterName = filterName;
    }


    @Override
    public boolean accept(File dir, String name) {
	return name.startsWith(this.filterName);

    }

}
