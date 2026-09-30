package com.deepank.careerraft.discovery;

import com.deepank.careerraft.domain.Job;import org.springframework.stereotype.Service;import java.util.*;

@Service public class DiscoveryService{
 private final SourceConfigLoader config;private final List<JobSource>adapters;
 public DiscoveryService(SourceConfigLoader c,List<JobSource>a){config=c;adapters=List.copyOf(a);}
 public DiscoveryResult discover(){Map<String,JobSource>byProvider=new HashMap<>();for(JobSource s:adapters)byProvider.put(s.provider(),s);List<Job>jobs=new ArrayList<>();List<String>failures=new ArrayList<>();Map<String,Integer>counts=new LinkedHashMap<>();for(SourceDefinition d:config.load()){if(!d.enabled())continue;JobSource source=byProvider.get(d.provider());if(source==null){failures.add(d.name()+": unsupported provider "+d.provider());counts.put(d.name(),0);continue;}try{List<Job>b=source.fetch(d);jobs.addAll(b);counts.put(d.name(),b.size());}catch(Exception e){failures.add(d.name()+": "+e.getMessage());counts.put(d.name(),0);}}return new DiscoveryResult(List.copyOf(jobs),List.copyOf(failures),Map.copyOf(counts));}
}
