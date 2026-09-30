package com.deepank.careerraft.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import com.deepank.careerraft.config.RuntimePaths;
import java.util.*;

@Component
public class CareerKnowledgeBase {
    private static final Set<String> ALLOWED = Set.of("direct","supported_by_project","inferred");
    private final Path root = RuntimePaths.data();
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<String,Map<String,Object>> docs = new LinkedHashMap<>();

    public CareerKnowledgeBase() {
        for (String name : List.of("profile","experience","projects","skills","education","certifications","sources")) {
            docs.put(name, load(name));
        }
        validate();
    }

    public Map<String,Object> profile(){return docs.get("profile");}
    public List<Map<String,Object>> roles(){return list(docs.get("experience"),"roles");}
    public List<Map<String,Object>> projects(){return list(docs.get("projects"),"projects");}
    public List<Map<String,Object>> education(){return list(docs.get("education"),"education");}
    public List<Map<String,Object>> sources(){return list(docs.get("sources"),"sources");}
    public List<Map<String,Object>> skills(){return list(docs.get("skills"),"skills");}

    public Map<String,Map<String,Object>> projectsById(){
        Map<String,Map<String,Object>> out=new LinkedHashMap<>();
        for(Map<String,Object> p:projects()) out.put(Objects.toString(p.get("id"),""),p);
        return out;
    }

    public List<Map<String,Object>> confirmedExperience(){
        List<Map<String,Object>> out=new ArrayList<>();
        for(Map<String,Object> role:roles())
            for(Object raw:listObjects(role.get("responsibilities")))
                if(raw instanceof Map<?,?> m && "confirmed".equals(Objects.toString(m.get("evidence_status"),"")))
                    out.add(cast(m));
        return out;
    }

    public List<String> directSkills(){
        return skills().stream()
                .filter(s->Set.of("direct","supported_by_project").contains(Objects.toString(s.get("evidence"))))
                .map(s->Objects.toString(s.get("name"))).toList();
    }

    public List<String> inferredSkills(){
        return skills().stream()
                .filter(s->"inferred".equals(Objects.toString(s.get("evidence"))))
                .map(s->Objects.toString(s.get("name"))).toList();
    }

    private Map<String,Object> load(String name){
        Path path=root.resolve(name+".yaml");
        if(!Files.exists(path))throw new IllegalStateException("Career Knowledge Base file missing: "+path);
        try{return yaml.readValue(Files.readString(path),new TypeReference<>(){});}
        catch(Exception e){throw new IllegalStateException("Unable to load Career Knowledge Base file: "+path,e);}
    }
    private void validate(){
        requireList("experience","roles");requireList("projects","projects");requireList("skills","skills");
        requireList("education","education");requireList("sources","sources");
        Set<String>ids=new HashSet<>();checkIds(ids,roles());checkIds(ids,projects());checkIds(ids,education());checkIds(ids,sources());
        for(Map<String,Object>s:skills())if(!ALLOWED.contains(Objects.toString(s.get("evidence"))))
            throw new IllegalStateException("Unsupported skill evidence level: "+s.get("evidence"));
        for(Map<String,Object>p:projects())if(p.get("name")==null||p.get("url")==null)
            throw new IllegalStateException("Every project must have a name and URL.");
        Map<String,Object>policy=map(profile().get("application_policy"));
        if(((Number)policy.getOrDefault("resume_pages",1)).intValue()!=1)
            throw new IllegalStateException("Career Raft requires a one-page resume policy.");
        Map<String,Object>prefs=map(profile().get("job_preferences"));
        double ceiling=Double.parseDouble(Objects.toString(prefs.get("experience_ceiling_years"),"99"));
        if(ceiling>2)throw new IllegalStateException("Career Raft experience ceiling must not exceed 2 years.");
    }
    private void requireList(String doc,String key){if(!(docs.get(doc).get(key) instanceof List<?>))throw new IllegalStateException("Career Knowledge Base field "+key+" must be a list.");}
    private static void checkIds(Set<String>ids,List<Map<String,Object>>items){for(Map<String,Object>i:items){String id=Objects.toString(i.get("id"),"");if(id.isBlank()||!ids.add(id))throw new IllegalStateException("Career Knowledge Base contains duplicate or missing IDs.");}}
    @SuppressWarnings("unchecked")private static Map<String,Object>map(Object o){return o instanceof Map<?,?>m?(Map<String,Object>)(Map<?,?>)m:Map.of();}
    private static List<Map<String,Object>>list(Map<String,Object>m,String key){List<Map<String,Object>>out=new ArrayList<>();for(Object o:listObjects(m.get(key)))if(o instanceof Map<?,?>mm)out.add(cast(mm));return out;}
    private static List<?>listObjects(Object o){return o instanceof List<?>l?l:List.of();}
    @SuppressWarnings("unchecked")private static Map<String,Object>cast(Map<?,?>m){return(Map<String,Object>)(Map<?,?>)m;}
}
