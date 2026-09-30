package com.deepank.careerraft.discovery;
import com.deepank.careerraft.domain.Job;import java.util.List;import java.util.Map;
public record DiscoveryResult(List<Job> jobs,List<String> failures,Map<String,Integer> jobsBySource){public boolean partial(){return !failures.isEmpty()&&!jobs.isEmpty();}}
