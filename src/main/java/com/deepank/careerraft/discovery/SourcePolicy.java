package com.deepank.careerraft.discovery;
import java.util.List;
public record SourcePolicy(String name,double minHours,double maxHours,Integer maxRequestsPerDay,List<Double> allowedIntervalsHours){
 public SourcePolicy{allowedIntervalsHours=allowedIntervalsHours==null?List.of():List.copyOf(allowedIntervalsHours);}
}
