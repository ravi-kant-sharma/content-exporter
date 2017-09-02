package com.ttn.slingexporter.service;

import java.util.List;

public interface ComponentPropertiesService {

    public static final String BLOG_LIST_COMPONENT = "invest-india/components/content/bloglist";

    public static final String LnD_LIST_COMPONENT = "invest-india/components/content/learning-and-development-listing";

    List<String> getPropertiesForComponent(String component);

    boolean containsComponent(String component);
}
