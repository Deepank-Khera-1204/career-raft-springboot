package com.deepank.careerraft.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import com.deepank.careerraft.config.RuntimePaths;
import java.util.*;

@Component
public class SourceConfigLoader {
 private final ObjectMapper yaml=new ObjectMapper(new YAMLFactory());
 public List<SourceDefinition> load(){
   Path p=RuntimePaths.config().resolve("sources.yaml");
   if(!Files.exists(p)) throw new IllegalStateException("Job source configuration missing: "+p);
   try{
     Map<String,Object> root=yaml.readValue(Files.readString(p),new TypeReference<>(){});
     List<SourceDefinition> out=new ArrayList<>();
     Object raw=root.get("sources");
     if(raw instanceof List<?> list) for(Object x:list) if(x instanceof Map<?,?> m){
       Map<String,Object> v=cast(m);
       out.add(new SourceDefinition(
         Objects.toString(v.get("name")),
         Objects.toString(v.get("provider")),
         Objects.toString(v.get("identifier")),
         Boolean.parseBoolean(Objects.toString(v.getOrDefault("enabled",true))),
         Double.parseDouble(Objects.toString(v.getOrDefault("min_hours",3))),
         Double.parseDouble(Objects.toString(v.getOrDefault("max_hours",8))),
         Integer.parseInt(Objects.toString(v.getOrDefault("max_requests_per_day",6))),
         Integer.parseInt(Objects.toString(v.getOrDefault("jitter_minutes",30))),
         numbers(v.get("allowed_intervals_hours")),
         map(v.get("options"))
       ));
     }
     return out;
   }catch(Exception e){throw new IllegalStateException("Unable to load config/sources.yaml",e);}
 }
 private static List<Double> numbers(Object x){if(!(x instanceof List<?> l))return List.of();return l.stream().map(v->Double.parseDouble(Objects.toString(v))).toList();}
 @SuppressWarnings("unchecked") private static Map<String,Object> map(Object x){return x instanceof Map<?,?>m?(Map<String,Object>)(Map<?,?>)m:Map.of();}
 @SuppressWarnings("unchecked") private static Map<String,Object> cast(Map<?,?>m){return (Map<String,Object>)(Map<?,?>)m;}
}
