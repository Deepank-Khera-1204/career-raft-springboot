package com.deepank.careerraft.intelligence;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EvidenceMatcher {
    private final EvidenceCatalog catalog;
    private static final Map<String,Set<String>> ALIASES=Map.of(
      "spring boot",Set.of("spring boot","springboot"),
      "rest apis",Set.of("rest","rest api","rest apis","restful"),
      "postgresql",Set.of("postgres","postgresql"),
      "kubernetes",Set.of("kubernetes","k8s"),
      "continuous integration",Set.of("ci","ci/cd","cicd","continuous integration"),
      "microservices",Set.of("microservice","microservices"),
      "distributed systems",Set.of("distributed system","distributed systems","distributed computing"),
      "rate limiting",Set.of("rate limit","rate limiting","throttling"),
      "leader election",Set.of("leader election","election"));
    public EvidenceMatcher(EvidenceCatalog catalog){this.catalog=catalog;}
    public List<EvidenceMatch> match(String jd,int topN){
      return catalog.claims().stream().map(c->score(c,jd)).filter(m->m.score()>0).sorted(Comparator.comparingDouble(EvidenceMatch::score).reversed().thenComparing(EvidenceMatch::claimId)).limit(topN).toList();
    }
    public List<EvidenceMatch> projectRecommendations(String jd,int topN){return match(jd,Integer.MAX_VALUE).stream().filter(m->{try{return catalog.get(m.claimId()).type()==EvidenceType.PROJECT;}catch(Exception e){return false;}}).limit(topN).toList();}
    public Coverage technologyCoverage(String jd,List<String> skills){List<String> yes=new ArrayList<>(),no=new ArrayList<>(); for(String s:skills){if(ALIASES.getOrDefault(s.toLowerCase(Locale.ROOT),Set.of(s)).stream().anyMatch(v->contains(jd,v)))yes.add(s);else no.add(s);}return new Coverage(yes,no);}
    private EvidenceMatch score(EvidenceClaim claim,String jd){
      LinkedHashSet<String> terms=new LinkedHashSet<>();terms.addAll(claim.keywords());terms.addAll(claim.technologies());
      List<String> matched=terms.stream().filter(t->variants(t).stream().anyMatch(v->contains(jd,v))).toList();
      if(matched.isEmpty() && variants(claim.title()).stream().anyMatch(v->contains(jd,v)))matched=List.of(claim.title());
      if(matched.isEmpty())return new EvidenceMatch(claim.id(),0,List.of(),"");
      double ts=Math.min(1.0,matched.size()/(double)Math.max(1,Math.min(5,terms.size())));
      double bonus=switch(claim.type()){case EXPERIENCE->.30;case PROJECT->.25;case SKILL->.10;case EDUCATION,CERTIFICATE->.05;};
      return new EvidenceMatch(claim.id(),round(Math.min(1.0,ts*.70+bonus)),matched,"Matched "+matched.size()+" verified evidence terms.");
    }
    private static Set<String> variants(String term){String k=term==null?"":term.trim().toLowerCase(Locale.ROOT);return ALIASES.getOrDefault(k,Set.of(k));}
    private static boolean contains(String text,String term){if(text==null||term==null||term.isBlank())return false;String a=norm(text),b=norm(term);return Pattern.compile("(?<![a-z0-9])"+Pattern.quote(b)+"(?![a-z0-9])").matcher(a).find();}
    private static String norm(String s){return s.toLowerCase(Locale.ROOT).replace('–','-').replace('—','-').replaceAll("[^a-z0-9+#.]"," ").replaceAll("\\s+"," ").trim();}
    private static double round(double x){return Math.round(x*10000)/10000.0;}
    public record Coverage(List<String> matched,List<String> missing){}
}
