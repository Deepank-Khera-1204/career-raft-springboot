package com.deepank.careerraft.intelligence;

import com.deepank.careerraft.domain.JobAssessment;
import org.springframework.stereotype.Service;
import java.util.*;
import com.deepank.careerraft.config.RuntimePaths;

@Service
public class SemanticScorer {
    public static final String SEMANTIC_VERSION = "1.1";
    private final EvidenceCatalog catalog;
    private final double maxBonus;
    private final double minRelevance;
    private final double experienceCeiling;
    private final List<String> seniorityExclusions;
    private final double deepAnalysisThreshold;
    private final double packageGenerationThreshold;
    private final double priorityThreshold;
    private final double promotionAlignment;

    public SemanticScorer(EvidenceCatalog catalog) {
        this.catalog = catalog;
        Map<String,Object> config = loadConfig();
        Map<String,Object> semantic = map(config.get("semantic"));
        this.maxBonus = Math.max(0, Double.parseDouble(Objects.toString(semantic.getOrDefault("max_bonus", 10))));
        this.minRelevance = Math.max(0, Math.min(1, Double.parseDouble(Objects.toString(semantic.getOrDefault("min_relevance", .55)))));
        this.experienceCeiling = readExperienceCeiling();
        this.seniorityExclusions = readSeniorityExclusions();
        Map<String,Object> thresholds = map(config.get("thresholds"));
        this.deepAnalysisThreshold = number(thresholds.getOrDefault("deep_analysis", 70));
        this.packageGenerationThreshold = number(thresholds.getOrDefault("package_generation", 80));
        this.priorityThreshold = number(thresholds.getOrDefault("priority_email", 90));
        this.promotionAlignment = number(semantic.getOrDefault("semantic_package_promotion_alignment", 0.80));
    }

    public Augmentation augment(JobAssessment assessment, SemanticModels.JobUnderstanding understanding, String provider) {
        if (!assessment.hardPass()) return new Augmentation(copy(assessment,false,assessment.hardFailReasons(),JobAssessment.NextAction.IGNORE,"skipped",provider,0,0,List.of(),List.of("Semantic analysis skipped for a hard-filtered job."),null,SEMANTIC_VERSION),0,0);

        var exp=understanding.experienceRequirement();
        if ("required".equalsIgnoreCase(exp.status()) && exp.minimumYears()!=null && exp.minimumYears()>experienceCeiling) {
            String reason="Semantic JD gate: requires at least "+fmt(exp.minimumYears())+" years; configured experience ceiling is "+fmt(experienceCeiling)+" years.";
            List<String> reasons=new ArrayList<>(assessment.hardFailReasons()); reasons.add(reason);
            return new Augmentation(copy(assessment,false,reasons,JobAssessment.NextAction.IGNORE,"applied",provider,0,0,List.of(),List.of(reason),null,SEMANTIC_VERSION),0,0);
        }
        for(String excluded:seniorityExclusions) if(seniorityMatches(understanding.seniority(),excluded)){
            String reason="Semantic JD gate: detected excluded seniority '"+understanding.seniority()+"' (matched '"+excluded+"').";
            List<String> reasons=new ArrayList<>(assessment.hardFailReasons());reasons.add(reason);
            return new Augmentation(copy(assessment,false,reasons,JobAssessment.NextAction.IGNORE,"applied",provider,0,0,List.of(),List.of(reason),null,SEMANTIC_VERSION),0,0);
        }

        Map<String,Double> relevance=new HashMap<>();
        for(var m:understanding.evidenceMatches()) relevance.put(m.evidenceId(),m.relevance());
        double total=0,covered=0;
        Map<String,Double> weights=Map.of("high",1.0,"medium",.7,"low",.4);
        for(var req:understanding.requirements()){
            double w=weights.getOrDefault(req.importance().toLowerCase(Locale.ROOT),.5); total+=w;
            double best=req.evidenceIds().stream().mapToDouble(id->relevance.getOrDefault(id,0d)).max().orElse(0);
            covered+=w*best;
        }
        double reqFit=total>0?covered/total:0;
        List<Double> strong=understanding.evidenceMatches().stream().map(SemanticModels.SemanticEvidenceMatch::relevance).filter(x->x>=minRelevance).sorted(Comparator.reverseOrder()).limit(5).toList();
        double evidenceFit=strong.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double alignment=round(Math.min(1, .70*reqFit+.30*evidenceFit),4);
        double bonus=round(Math.min(maxBonus,alignment*maxBonus),2);
        double combined=Math.min(100,assessment.score().total()+bonus);
        JobAssessment.NextAction action=combined<deepAnalysisThreshold?JobAssessment.NextAction.IGNORE:combined<packageGenerationThreshold?JobAssessment.NextAction.REVIEW:combined<priorityThreshold?JobAssessment.NextAction.GENERATE_PACKAGE:JobAssessment.NextAction.GENERATE_PACKAGE_PRIORITY;
        double promotion=promotionAlignment;
        boolean promoted=action==JobAssessment.NextAction.REVIEW&&alignment>=promotion;
        if(promoted) action=JobAssessment.NextAction.GENERATE_PACKAGE;
        List<String> selected=understanding.evidenceMatches().stream().filter(m->m.relevance()>=minRelevance).map(SemanticModels.SemanticEvidenceMatch::evidenceId).toList();
        List<String> projects=selected.stream().filter(id->{try{return catalog.get(id).type()==EvidenceType.PROJECT;}catch(Exception e){return false;}}).limit(3).toList();
        List<String> rationale=List.of("Semantic alignment="+fmt(alignment)+"; bounded bonus="+fmt(bonus)+".","Validated evidence matches above threshold: "+selected.size(),"Semantic package promotion="+(promoted?"applied":"not applied")+" (alignment threshold=0.80).");
        JobAssessment updated=copy(assessment,true,assessment.hardFailReasons(),action,"applied",provider,alignment,bonus,selected,rationale,projects.isEmpty()?null:projects.get(0),SEMANTIC_VERSION);
        if(!projects.isEmpty()) updated=new JobAssessment(updated.jobId(),updated.hardPass(),updated.hardFailReasons(),updated.score(),updated.matchedSkills(),updated.missingSkills(),projects,updated.rationale(),updated.categoryExplanations(),updated.matchedDomains(),updated.matchedRole(),updated.requiredExperienceYears(),updated.parsedMinLpa(),updated.parsedMaxLpa(),updated.nextAction(),updated.scoringVersion(),updated.semanticStatus(),updated.semanticProvider(),updated.semanticAlignment(),updated.semanticBonus(),updated.semanticEvidenceIds(),updated.semanticRationale(),updated.semanticError(),updated.technicalConceptMatches(),updated.semanticVersion());
        return new Augmentation(updated,alignment,bonus);
    }

    private JobAssessment copy(JobAssessment a, boolean pass,List<String>fail,JobAssessment.NextAction action,String status,String provider,double alignment,double bonus,List<String>ids,List<String>rat,String ignored,String version){
        return new JobAssessment(a.jobId(),pass,fail,a.score(),a.matchedSkills(),a.missingSkills(),a.selectedProjects(),a.rationale(),a.categoryExplanations(),a.matchedDomains(),a.matchedRole(),a.requiredExperienceYears(),a.parsedMinLpa(),a.parsedMaxLpa(),action,a.scoringVersion(),status,provider,alignment,bonus,ids,rat,a.semanticError(),a.technicalConceptMatches(),version);
    }
    private double readExperienceCeiling(){Map<String,Object> p=map(catalog.knowledgeBase().profile().get("job_preferences"));return Double.parseDouble(Objects.toString(p.getOrDefault("experience_ceiling_years",2)));}
    private List<String> readSeniorityExclusions(){Map<String,Object>p=map(catalog.knowledgeBase().profile().get("job_preferences"));Object x=p.get("hard_seniority_exclusions");if(x instanceof List<?>l)return l.stream().map(String::valueOf).toList();return List.of();}
    private static boolean seniorityMatches(String value,String excluded){String a=normalize(value),b=normalize(excluded);if(a.isBlank()||b.isBlank())return false;Map<String,Set<String>>aliases=Map.of("senior",Set.of("senior","senior level","senior engineer","senior software engineer","senior software developer","senior backend engineer"),"staff",Set.of("staff","staff engineer","staff software engineer"),"principal",Set.of("principal","principal engineer","principal software engineer"),"lead",Set.of("lead","tech lead","technical lead","team lead","engineering lead"),"manager",Set.of("manager","engineering manager","software engineering manager"),"director",Set.of("director","engineering director","software engineering director"),"head of",Set.of("head of","head of engineering","head of technology"),"vice president",Set.of("vice president","vp"),"vp",Set.of("vp","vice president"));return aliases.getOrDefault(b,Set.of(b)).contains(a);}
    private static String normalize(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('-',' ').replace('_',' ').replaceAll("\\s+"," ").trim();}
    private static double number(Object x){try{return Double.parseDouble(String.valueOf(x));}catch(Exception e){return 0d;}}
    private static double round(double x,int n){double p=Math.pow(10,n);return Math.round(x*p)/p;}private static String fmt(double x){return String.format(Locale.ROOT,"%.2f",x);}
    private static Map<String,Object> loadConfig(){try{var mapper=new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();java.nio.file.Path p=RuntimePaths.config().resolve("scoring.yaml");if(!java.nio.file.Files.exists(p))return Map.of();return mapper.readValue(java.nio.file.Files.readString(p),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception e){return Map.of();}}
    @SuppressWarnings("unchecked")private static Map<String,Object>map(Object x){return x instanceof Map<?,?>m?(Map<String,Object>)(Map<?,?>)m:Map.of();}
    public record Augmentation(JobAssessment assessment,double alignment,double bonus){}
}
