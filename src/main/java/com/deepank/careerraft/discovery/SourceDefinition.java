package com.deepank.careerraft.discovery;
import java.util.List;
import java.util.Map;
public record SourceDefinition(String name,String provider,String identifier,boolean enabled,double minHours,double maxHours,int maxRequestsPerDay,int jitterMinutes,List<Double> allowedIntervalsHours,Map<String,Object> options){
 public SourceDefinition{allowedIntervalsHours=allowedIntervalsHours==null?List.of():List.copyOf(allowedIntervalsHours);options=options==null?Map.of():Map.copyOf(options);}
}
