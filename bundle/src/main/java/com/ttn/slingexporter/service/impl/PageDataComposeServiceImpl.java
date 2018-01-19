package com.ttn.slingexporter.service.impl;

import com.day.cq.commons.jcr.JcrConstants;
import com.ttn.slingexporter.service.ComponentPropertiesService;
import com.ttn.slingexporter.service.PageComponentProcessorService;
import com.ttn.slingexporter.service.PageDataComposeService;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.service.component.ComponentContext;

import java.util.*;

@Component(immediate = true)
@Service(PageDataComposeService.class)
@Property(name = "template.path", label = "Templates paths", description = "The template path for translation", value = {
        "invest-india/components/page/basepage=main",
        "invest-india/components/page/one-column-page=col-1",
        "invest-india/components/page/two-column-page=col-1;col-2;col-2-inherited",
        "invest-india/components/page/blogpage=blogcomponent",
        "foundation/components/table=tableData",
        "invest-india/components/content/policy-description=text"
}, cardinality = Integer.MAX_VALUE)

public class PageDataComposeServiceImpl implements PageDataComposeService {

 /*   @Reference
    private HeadlessResourceResolverService resourceResolverService;*/

    Map<String, List<String>> validPropertiesMap = new HashMap<String, List<String>>();

    public static final String BLOG_COMPONENT_NODE = "invest-india/components/content/blogcomponent";
    public static final String POLICY_DESCRIPTION_NODE = "invest-india/components/content/policy-description";
    public static final String ACCORDIAN_CONTAINER_NODE = "invest-india/components/content/accordioncontainer";
    public static final String ROW_CONTAINER_NODE = "invest-india/components/content/row-container";
    public static final String TAB_CONTAINER_NODE = "invest-india/components/content/tab-container";
    public static final String TARGETED_NODE = "cq/personalization/components/target";

    @Activate
    protected void activate(ComponentContext componentContext) throws Exception {
        Dictionary props = componentContext.getProperties();
        String[] list = (String[]) props.get("template.path");
        for (String config : list) {
            String key = (config).split("=")[0];
            String value = (config).split("=")[1];
            List<String> valueList = Arrays.asList(value.split(";"));
            validPropertiesMap.put(key, valueList);
        }
    }

    @Reference
    private PageComponentProcessorService pageComponentProcessorService;

    @Reference
    ComponentPropertiesService componentPropertiesService;



   /* public ResourceResolver getResourceResolver(){
        return this.resourceResolverService.getResourceResolver();
    }*/

    JSONArray jsonArray = new JSONArray();

    JSONObject page;

    private String domainUrl = "";

    public JSONObject composePageData(ResourceResolver resourceResolver, String resourcePath, String requestUrl) throws JSONException {
        domainUrl = requestUrl.substring(0,requestUrl.indexOf("/content"));
        page = new JSONObject();
        Resource resource = resourceResolver.resolve(resourcePath);
        JSONArray contentList = new JSONArray();

        if (resource != null && !Resource.RESOURCE_TYPE_NON_EXISTING.equals(resource.getResourceType())) {
            ValueMap valueMap = resource.adaptTo(ValueMap.class);
            page.put("title", valueMap.get("jcr:title"));
            page.put("description", valueMap.get("jcr:description"));

            String pageResourceType = valueMap.get("sling:resourceType", String.class);
            if (validPropertiesMap.containsKey(pageResourceType)) {
                List<String> parsysNodes = validPropertiesMap.get(pageResourceType);
                for (String parsysNode : parsysNodes) {     // for col nodes
                    Resource colChild = resource.getChild(parsysNode);
                    if (colChild != null) {
                        if (TARGETED_NODE.equalsIgnoreCase(colChild.getResourceType())) {
                            colChild = handleTarget(colChild);
                            System.out.println("Targetted Component Found");
                        }
                        if (BLOG_COMPONENT_NODE.equals(colChild.getResourceType())) {
                            page.put("blogDetail", addComponents(colChild, new JSONObject()));
                            colChild  = colChild.getChild("blogparsys");
                        }else if(ACCORDIAN_CONTAINER_NODE.equalsIgnoreCase(colChild.getResourceType())){
                            System.out.println("Accordian node");
                        }else if(ROW_CONTAINER_NODE.equalsIgnoreCase(colChild.getResourceType())){
                            System.out.println("Row Container node");
                        }
                        Iterator<Resource> children = colChild.listChildren();
                        while (children.hasNext()) {            // list of resources in col parsys
                            JSONObject jsonObject = composeComponentData(null,null,children.next(), resource, new JSONArray(),false);
                            if (jsonObject.length() > 0) {
                                contentList.put(jsonObject);
                            }
                        }
                    }
                }
                page.put("content", contentList);
            }
        }
        return page;
    }

    public Resource handleTarget(Resource content) throws JSONException {
        return content.getChild("default");
    }

    private JSONObject composeComponentData(JSONObject content, JSONArray conArray, Resource child, Resource resource, JSONArray tabArray, boolean isTab) throws JSONException {
        if (content == null) {
            content = new JSONObject();
        }
        if(conArray == null) {
            conArray = new JSONArray();
        }
        if(isTab && tabArray == null){
            tabArray = new JSONArray();
        }
        switch (child.getValueMap().get("sling:resourceType", String.class)) {
            case ComponentPropertiesService.BLOG_LIST_COMPONENT:
                Iterator<Resource> blogResourceItr = resource.getParent().listChildren();
                while (blogResourceItr.hasNext()) {
                    Resource blogResource = blogResourceItr.next();
                    JSONObject con = new JSONObject();
                    if (blogResource.getValueMap().get(JcrConstants.JCR_PRIMARYTYPE, String.class).equals("cq:Page")) {
                        Resource blogCompRes = blogResource.getChild("jcr:content/blogcomponent");
                        if (blogCompRes != null) {
                            con = addComponents(blogCompRes, con);
                        }
                    }
                    Resource blogListImage = blogResource.getChild("jcr:content/image");
                    if(blogListImage != null){
                        con.put("image",blogListImage.getValueMap().get("fileReference"));
                    }
                    if (con.length() > 0) {
                        if(con.has("path")){
                            con.put("path",blogResource.getPath()+"/jcr:content.compose.json");
                        }
                        conArray.put(con);
                    }
                }
                content.put("blog-components", conArray);
                break;
            case ComponentPropertiesService.LnD_LIST_COMPONENT:
                Iterator<Resource> lndResourceItr = resource.getParent().listChildren();
                while (lndResourceItr.hasNext()) {
                    Resource lndResource = lndResourceItr.next();
                    JSONObject con = new JSONObject();
                    if (lndResource.getValueMap().get(JcrConstants.JCR_PRIMARYTYPE, String.class).equals("cq:Page")) {
                        Resource lndResourceComp = lndResource.getChild("jcr:content");
                        if (lndResourceComp != null) {
                            con = addComponents(lndResourceComp, con);
                            Resource lndCompImageResource = lndResourceComp.getChild("image");
                            if(lndCompImageResource != null) {
                                if(lndCompImageResource.getValueMap().containsKey("fileReference")) {
                                    con.put("image", lndCompImageResource.getValueMap().get("fileReference", String.class));
                                }else if(lndCompImageResource.getChild("file/jcr:content") != null){
                                    con.put("image",lndCompImageResource.getChild("file/jcr:content").getPath());
                                }
                            }
                        }
                    }
                    if (con.length() > 0) {
                        if(con.has("path")){
                            con.put("path",lndResource.getPath()+"/jcr:content.compose.json");
                        }
                        con.put("type", "lnd-listing");
                        conArray.put(con);
                    }
                }
                content.put("lnd-components", conArray);
                break;
            case ComponentPropertiesService.ACCORDIAN_CONATINER_COMPONENT:
                String accordianTitle = child.getValueMap().get("accordionTitle", String.class);
                Resource parsysNode = child.getChild("accordion-container");
                if(parsysNode != null){
                    Iterator<Resource> accordianItems = parsysNode.listChildren();
                    while (accordianItems.hasNext()){
                        JSONObject con = new JSONObject();
                        Resource accordian = accordianItems.next();
                        con = addComponents(accordian, con);
                        conArray.put(con);
                    }
                }
                if(accordianTitle != null) {
                    content.put("accordionTitle", accordianTitle.replaceAll("\\<.*?>", ""));
                }
                if(isTab && tabArray != null){
                    JSONObject tab = new JSONObject();
                    tab.put("accordion-list",conArray);
                    tabArray.put(tab);
                    content.put("tab-array",tabArray);
                }else{
                    content.put("accordion-list",conArray);
                }

                break;
            case ComponentPropertiesService.ROW_CONATINER_COMPONENT:
                Resource rowParsysNode = child.getChild("row-content");
                if(rowParsysNode != null){
                    Iterator<Resource> rowContainerItems = rowParsysNode.listChildren();
                    while (rowContainerItems.hasNext()){
                        JSONObject con = new JSONObject();
                        Resource row = rowContainerItems.next();
                        con = addComponents(row, con);
                        if(row.isResourceType("invest-india/components/content/textimage") || row.isResourceType("invest-india/components/content/resource-component")) {
                            Resource imageResource = row.getChild("image");
                            if(imageResource != null) {
                                con.put("image", imageResource.getValueMap().get("fileReference", String.class));
                            }
                        } else if (row.isResourceType("invest-india/components/content/contentpodwrap")) {
                            Resource contentPodResource = row.getChild("row-content");
                            if(contentPodResource != null) {
                                Iterator<Resource> contentPodChildren = contentPodResource.listChildren();
                                JSONArray childContentPodArray = new JSONArray();
                                while(contentPodChildren.hasNext()) {
                                    JSONObject childContentPodObject = new JSONObject();
                                    Resource childContentPod = contentPodChildren.next();
                                    childContentPodObject = addComponents(childContentPod, childContentPodObject);
                                    if(childContentPod.isResourceType("invest-india/components/content/textimage")) {
                                        Resource imageResource = childContentPod.getChild("image");
                                        if(imageResource != null) {
                                            childContentPodObject.put("image", imageResource.getValueMap().get("fileReference", String.class));
                                        }
                                    }
                                    childContentPodArray.put(childContentPodObject);
                                }
                                con.put("content-pod", childContentPodArray);
                            }
                        } else if (row.isResourceType("invest-india/components/content/form-fields/columncontrol")) {
                            Iterator<Resource> colRes = row.listChildren();
                            JSONArray matArray = new JSONArray();
                            while(colRes.hasNext()) {
                                Iterator<Resource> matRes = colRes.next().listChildren();

                                while (matRes.hasNext()) {
                                    JSONObject matObj = new JSONObject();
                                    matObj = addComponents(matRes.next(), matObj);
                                    matArray.put(matObj);
                                }

                            }
                            con.put("matrix", matArray);
                        }
                        if(con.length() > 0) {
                            conArray.put(con);
                        }
                    }
                }
                if(conArray.length() > 0) {
                    content.put("row-container", conArray);
                }
                break;
            case POLICY_DESCRIPTION_NODE :
                Resource text = child.getChild("text");
                content = addComponents(text, new JSONObject());
                if(child.getValueMap().get("heading") != null) {
                    content.put("heading",child.getValueMap().get("heading"));
                }
                break;
            case TAB_CONTAINER_NODE:
                Iterator<Resource> r = child.listChildren();
                while(r.hasNext()) {
                    Resource tab = r.next();
                    Iterator<Resource> tabs = tab.listChildren();
                    while(tabs.hasNext()){
                        Resource t = tabs.next();
                        composeComponentData(content,conArray,t, tab,tabArray,false);
                    }
                }
                break;
            case "invest-india/components/content/tab-content" :
                Iterator<Resource> r1 = child.listChildren();
                while(r1.hasNext()) {
                    Resource tab = r1.next();
                    Iterator<Resource> c = tab.getChild("col-0").listChildren();
                    while(c.hasNext()) {
                        Resource rr = c.next();
                        composeComponentData(content,null,rr, tab, tabArray,true);
                    }
                    JSONArray arr = (JSONArray)content.get("tab-array");
                    if(arr != null && arr.length() >0) {
                        int l = arr.length();
                        ((JSONObject) arr.get(l - 1)).put("tabHeading", child.getValueMap().get("tabHeading"));
                    }
                }
                break;
            case ComponentPropertiesService.TARGETED_COMPONENT:
                Resource targetChild = child.getChild("default");
                if (targetChild != null) {
                    content = composeComponentData(content, conArray, targetChild, child, tabArray, false);
                }
                break;
            default:
                content = addComponents(child, new JSONObject());
        }
        return content;
    }

    private JSONObject addComponents(Resource r, JSONObject content) throws JSONException {
        JSONObject node = new JSONObject();
        if (componentPropertiesService.containsComponent(r.getResourceType())) {
            List<String> properties = componentPropertiesService.getPropertiesForComponent(r.getResourceType());
            ValueMap propertiesMap = r.adaptTo(ValueMap.class);
            for (String p : properties) {
                if (propertiesMap.containsKey(p)) {
                    if(propertiesMap.get(p) instanceof String[]) {
                        String[] val = ((String[]) propertiesMap.get(p)).clone();
                        JSONArray array = new JSONArray();
                        for(int i =0;i<val.length;i++){
                            JSONObject n = new JSONObject();
                            processData(val[i], p, n);
                            array.put(n);
                        }
                        node.put(p+"Array",array);
                    }else{
                        String val = propertiesMap.get(p).toString();
                        processData(val, p, node);
                    }

                } else if (p.equals("path")) {
                    processData(r.getPath() + "/jcr:content.compose.json", p, node);
                }
            }
            if(propertiesMap.containsKey("text")) {
                node.put("type", "text");
            } else if(propertiesMap.containsKey("criteriaImage")) {
                node.put("type", "criteriaImage");
            } else if(propertiesMap.containsKey("fileReference")) {
                node.put("type", "image");
            } else if(propertiesMap.containsKey("blogTitle")) {
                node.put("type", "blogComponent");
            } else if(propertiesMap.containsKey("asset")) {
                node.put("type", "video");
            } else if(propertiesMap.containsKey("linkURL")) {
                node.put("type", "text-image");
            }
            //content.put(r.getName(), node);
        }
        return node;
    }

    private void processData(String data, String type, JSONObject node) throws JSONException {
        if (type.equalsIgnoreCase("text") || type.equalsIgnoreCase("description")) {
            Document doc = Jsoup.parse(data);
            data = data.replaceAll("\\<img.*?>", "--img--");
            data = data.replaceAll("\\<iframe.*?>", "--iframe--");
            data = data.replaceAll("\\<.*?>", "");

            Elements tables = doc.getElementsByTag("table");
            JSONArray tableArray = new JSONArray();
            for (Element table: tables) {
                 tableArray.put(tableProcesser(table, data));
                table.remove();
            }
            if(tableArray.length() > 0) {
                node.put("table", tableArray);
            }
            Elements links = doc.getElementsByTag("a");
            Elements imgs = doc.getElementsByTag("img");
            Elements iframes = doc.getElementsByTag("iframe");

            JSONArray linkArr = new JSONArray();
            JSONArray imgArr = new JSONArray();
            JSONArray iframeArr = new JSONArray();
            Map<String,Integer> textIndex = new HashMap<>();
            int index = 0;

            for (Element link : links) {
                JSONObject linkObj = new JSONObject();
                String href = link.attr("href");
                String text = link.text();
                linkObj.put("text", text);
                linkObj.put("href", href);
                if(href.contains("/content/dam/invest-india")){
                    href = domainUrl + href;
                }
                if(textIndex.containsKey(text)){
                    index = data.indexOf(text,textIndex.get(text) + text.length());
                }else{
                    index = data.indexOf(text);
                }
                textIndex.put(text,index);
                linkObj.put("index", index);

                linkArr.put(linkObj);
            }
            textIndex.clear();
            for (Element img : imgs) {
                JSONObject imgObj = new JSONObject();
                String src = img.attr("src");
                String text = "--img--";
                imgObj.put("src", src);
                index = data.indexOf(text);
              /*  if(textIndex.containsKey(text)){
                    index = data.indexOf(text,textIndex.get(text) + text.length());
                }else{
                    index = data.indexOf(text);
                }*/
              //  textIndex.put(text,index);
                imgObj.put("index", index);
                imgObj.put("width", img.attr("width"));
                imgObj.put("height", img.attr("height"));
                imgArr.put(imgObj);
                //removing --img--
                data = data.replaceFirst("--img--","");
            }
            textIndex.clear();
            for (Element iframe : iframes) {
                JSONObject iframeObj = new JSONObject();
                String src = iframe.attr("src");
                String text = "--iframe--";
                iframeObj.put("src", src);
                index = data.indexOf(text);
              /*  if(textIndex.containsKey(text)){
                    index = data.indexOf(text,textIndex.get(text) + text.length());
                }else{
                    index = data.indexOf(text);
                }*/
                //  textIndex.put(text,index);
                iframeObj.put("index", index);
                iframeObj.put("width", iframe.attr("width"));
                iframeObj.put("height", iframe.attr("height"));
                iframeArr.put(iframeObj);
                //removing --iframe--
                data = data.replaceFirst("--iframe--","");
            }
            node.put(type, data);


            if (linkArr.length() > 0) {
                node.put("a", linkArr);
            }
            if (imgArr.length() > 0) {
                node.put("img", imgArr);
            }
            if (iframeArr.length() > 0) {
                node.put("iframe", iframeArr);
            }


        } else if(type.equalsIgnoreCase("tableData")){
            Document doc = Jsoup.parse(data);
            for(Element table: doc.getElementsByTag("table")) {
                node.put(type, tableProcesser(table, data ));
            }
        } else if(type.equalsIgnoreCase("publishDate")){
            node.put(type, new Date());
        } else {
            data = data.replaceAll("\\<.*?>", "");
            node.put(type, data);
        }
        node.put("type", type);
    }

    private JSONArray tableProcesser(Element table, String data) throws JSONException {
            data = data.replaceAll("\\<img.*?>", "--img--");
            Elements rows = table.select("tr");
            JSONArray rowArray = new JSONArray();
            Map<String,Integer> textIndex = new HashMap<>();
            int index = 0;
            for (Element row : rows) {
                Elements cols = row.select("td");
                cols.addAll(row.select("th"));
                JSONArray colArray = new JSONArray();
                for (Element col : cols) {
                    JSONObject column = new JSONObject();
                    Elements links = col.getElementsByTag("a");
                    JSONArray linkArr = new JSONArray();
                    for (Element link : links) {
                        JSONObject linkObj = new JSONObject();
                        String href = link.attr("href");
                        if(href.contains("/content/dam/invest-india")){
                            href = domainUrl + href;
                        }
                        String text = link.text();
                        linkObj.put("text", text);
                        linkObj.put("href", href);
                        if(textIndex.containsKey(text)){
                            index = col.toString().indexOf(text, textIndex.get(text) + text.length());
                        }else{
                            index = col.toString().indexOf(text);
                        }
                        linkObj.put("index", index);
                        textIndex.put(text,index);
                        linkArr.put(linkObj);
                    }

                    if (linkArr.length() > 0) {
                        column.put("a", linkArr);
                    }

                    Elements imgs = col.getElementsByTag("img");
                    JSONArray imgArr = new JSONArray();
                    for (Element img : imgs) {
                        JSONObject imgObj = new JSONObject();
                        imgObj.put("src", img.attr("src"));
                        imgObj.put("alt", img.attr("alt"));
                        imgObj.put("height", img.attr("height"));
                        imgObj.put("width", img.attr("width"));
                        index = data.indexOf("--img--");
                        imgObj.put("index", index);
                        imgArr.put(imgObj);
                        data = data.replaceFirst("--img--","");
                    }
                    if (col.text().length() > 0) {
                        column.put("text", col.text());
                    }
                    if (imgArr.length() > 0) {
                        column.put("img", imgArr);
                    }

                    colArray.put(column);
                }
                rowArray.put(colArray);
            }
            return rowArray;
    }
}
