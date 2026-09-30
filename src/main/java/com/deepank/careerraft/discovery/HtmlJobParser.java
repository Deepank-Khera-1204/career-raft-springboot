package com.deepank.careerraft.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class HtmlJobParser implements JobParser {
  private final ObjectMapper mapper=new ObjectMapper();
  @Override public Map<String,String> extract(String html){
    if(html==null||html.isBlank())return null;
    for(Element script:Jsoup.parse(html).select("script[type=application/ld+json]")){
      try{JsonNode root=mapper.readTree(script.data());JsonNode jp=findJobPosting(root);if(jp!=null){Map<String,String> r=posting(jp);if(!r.getOrDefault("description","").isBlank())return r;}}
      catch(Exception ignored){}
    }
    Document doc=Jsoup.parse(html);String title=metadataTitle(doc);String company=metadataCompany(doc);Elements candidates=doc.select("main,article,[id*=job-description],[class*=job-description],[data-automation-id*=job]");String best="";for(Element e:candidates){String text=e.text();if(text.length()>=100&&text.length()>best.length())best=text;}if(best.isBlank())return null; if(Set.of("careers","careers at amazon","jobs","job search","search jobs").contains(title.toLowerCase(Locale.ROOT)))title="";return map("title",title,"description",best,"location","","salary_text","","employment_type","","company",company);
  }
  private JsonNode findJobPosting(JsonNode n){if(n==null)return null;if(n.isArray()){for(JsonNode x:n){JsonNode r=findJobPosting(x);if(r!=null)return r;}return null;}if(!n.isObject())return null;JsonNode t=n.get("@type");if(t!=null&&(t.isTextual()?"JobPosting".equals(t.asText()):contains(t,"JobPosting")))return n;return findJobPosting(n.get("@graph"));}
  private boolean contains(JsonNode a,String s){for(JsonNode x:a)if(s.equals(x.asText()))return true;return false;}
  private Map<String,String> posting(JsonNode v){String desc=clean(v.path("description").asText(""));String title=v.path("title").asText("");String location=locations(v.path("jobLocation"));String salary=salary(v.path("baseSalary"));String type=v.path("employmentType").isArray()?join(v.path("employmentType")):v.path("employmentType").asText("");String company="";JsonNode ho=v.get("hiringOrganization");if(ho!=null)company=ho.path("name").asText(ho.path("legalName").asText(""));return map("title",title,"description",desc,"location",location,"salary_text",salary,"employment_type",type,"company",company);}
  private String clean(String s){return Jsoup.parse(s).text().replaceAll("\\s+"," ").trim();}
  private String locations(JsonNode n){List<String>out=new ArrayList<>();if(n.isObject())n=mapper.createArrayNode().add(n);if(n.isArray())for(JsonNode x:n){JsonNode a=x.path("address").isObject()?x.path("address"):x;List<String>p=new ArrayList<>();for(String k:List.of("addressLocality","addressRegion","addressCountry"))if(a.hasNonNull(k))p.add(a.get(k).asText());if(!p.isEmpty())out.add(String.join(", ",p));}return String.join(" / ",new LinkedHashSet<>(out));}
  private String salary(JsonNode n){if(n==null||n.isMissingNode())return "";JsonNode v=n.path("value");String cur=n.path("currency").asText("");if(v.isObject()){if(v.has("minValue")&&v.has("maxValue"))return v.path("minValue").asText()+"-"+v.path("maxValue").asText()+" "+cur;String x=v.path("minValue").asText(v.path("maxValue").asText(""));return (x+" "+cur).trim();}return (v.asText("")+" "+cur).trim();}
  private String join(JsonNode n){List<String>r=new ArrayList<>();for(JsonNode x:n)r.add(x.asText());return String.join(", ",r);}
  private String metadataTitle(Document d){Element e=d.selectFirst("title");return e==null?"":e.text().split(" \\| | \\| | - | — | :: ",2)[0].trim();}
  private String metadataCompany(Document d){for(String q:List.of("meta[property=og:site_name]","meta[name=application-name]")){Element e=d.selectFirst(q);if(e!=null)return e.attr("content").trim();}return "";}
  private Map<String,String> map(String... kv){Map<String,String>m=new LinkedHashMap<>();for(int i=0;i+1<kv.length;i+=2)m.put(kv[i],kv[i+1]);return m;}
}
