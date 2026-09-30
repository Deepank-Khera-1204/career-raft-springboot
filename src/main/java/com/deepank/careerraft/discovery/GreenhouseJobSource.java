package com.deepank.careerraft.discovery;

import com.deepank.careerraft.domain.Job;import org.springframework.stereotype.Component;import java.util.*;

@Component public class GreenhouseJobSource implements JobSource{
 private final SimpleHttpClient http;public GreenhouseJobSource(SimpleHttpClient http){this.http=http;}
 public String provider(){return "greenhouse";}
 public List<Job> fetch(SourceDefinition d){Map<String,Object>payload=http.getJson("https://boards-api.greenhouse.io/v1/boards/"+d.identifier()+"/jobs?content=true");Object raw=payload.get("jobs");if(!(raw instanceof List<?>list))return List.of();List<Job>out=new ArrayList<>();for(Object o:list)if(o instanceof Map<?,?>rawMap){Map<String,Object>x=cast(rawMap);String id=Objects.toString(x.get("id"),""),title=Objects.toString(x.get("title"),"").trim(),url=Objects.toString(x.get("absolute_url"),"");if(id.isBlank()||url.isBlank())continue;String loc="";if(x.get("location") instanceof Map<?,?>lm)loc=Objects.toString(lm.get("name"),"");out.add(new Job(SourceSupport.jobId(d.name(),id),d.name(),id,d.identifier(),title,url,org.jsoup.Jsoup.parse(Objects.toString(x.get("content"),"")).text().replaceAll("\\s+"," ").trim(),loc,null,null,null,SourceSupport.parseIso(Objects.toString(x.get("updated_at"),null)),null));}return out;}
 @SuppressWarnings("unchecked")private static Map<String,Object>cast(Map<?,?>m){return(Map<String,Object>)(Map<?,?>)m;}
}
