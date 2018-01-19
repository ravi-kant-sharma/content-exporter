package com.ttn.slingexporter.service;

import java.util.List;

public interface ComponentPropertiesService {

    public static final String BLOG_LIST_COMPONENT = "invest-india/components/content/bloglist";

    public static final String LnD_LIST_COMPONENT = "invest-india/components/content/learning-and-development-listing";

    public static final String ACCORDIAN_CONATINER_COMPONENT = "invest-india/components/content/accordioncontainer";

    public static final String ROW_CONATINER_COMPONENT = "invest-india/components/content/row-container";

    public static final String TARGETED_COMPONENT = "cq/personalization/components/target";

    List<String> getPropertiesForComponent(String component);

    boolean containsComponent(String component);
}
