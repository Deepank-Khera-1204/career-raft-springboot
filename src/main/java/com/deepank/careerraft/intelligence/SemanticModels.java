package com.deepank.careerraft.intelligence;

import java.util.List;
public final class SemanticModels {
 private SemanticModels(){}
 public record Requirement(String concept,String importance,List<String> evidenceSignals,List<String> evidenceIds){public Requirement{evidenceSignals=evidenceSignals==null?List.of():List.copyOf(evidenceSignals);evidenceIds=evidenceIds==null?List.of():List.copyOf(evidenceIds);}}
 public record SemanticEvidenceMatch(String evidenceId,double relevance,String rationale){}
 public record ExperienceRequirement(Double minimumYears,Double maximumYears,String status){}
 public record JobUnderstanding(String roleFamily,String seniority,ExperienceRequirement experienceRequirement,List<String> domainSignals,List<Requirement> requirements,List<String> responsibilities,List<String> keywords,List<SemanticEvidenceMatch> evidenceMatches){public JobUnderstanding{domainSignals=domainSignals==null?List.of():List.copyOf(domainSignals);requirements=requirements==null?List.of():List.copyOf(requirements);responsibilities=responsibilities==null?List.of():List.copyOf(responsibilities);keywords=keywords==null?List.of():List.copyOf(keywords);evidenceMatches=evidenceMatches==null?List.of():List.copyOf(evidenceMatches);}}
 public record SemanticAnalysisResult(JobUnderstanding understanding,String usedProvider){}
}
