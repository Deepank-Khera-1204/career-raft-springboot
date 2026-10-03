package com.deepank.careerraft.referrals;
import org.springframework.stereotype.Service;
import java.net.*;
import java.util.*;

@Service
public class PublicWebReferralResearcher{
 private final TavilySearchProvider search;
 public PublicWebReferralResearcher(TavilySearchProvider s){search=s;}
 private static final List<List<String>> LANES=List.of(
  List.of("engineering manager","hiring manager","engineering lead","tech lead"),
  List.of("recruiter","talent acquisition","technical recruiter","talent partner"),
  List.of("backend engineer","software engineer","java engineer","senior software engineer")
 );
 public List<ReferralModels.ReferralTarget> findTargets(String company,String role,int limit){
  Map<String,ReferralModels.ReferralTarget> collected=new HashMap<>();
  for(String q:queries(company,role)) for(var r:search.search(q,20)){
   var t=candidate(company,role,q,r);
   if(t!=null&&t.confidence()>=.35){
    String key=normalise(t.profileUrl()).toLowerCase(Locale.ROOT).replaceFirst("^www\\.","");
    if(!collected.containsKey(key)||t.confidence()>collected.get(key).confidence()) collected.put(key,t);
   }
  }
  return collected.values().stream()
   .sorted(Comparator.comparingDouble(ReferralModels.ReferralTarget::confidence).reversed()
   .thenComparing(ReferralModels.ReferralTarget::name,String.CASE_INSENSITIVE_ORDER))
   .limit(Math.max(1,limit)).toList();
 }
 private List<String> queries(String company,String role){
  return List.of(
   "site:linkedin.com/in \""+company+"\" \""+role+"\"",
   "site:linkedin.com/in \""+company+"\" (\"Engineering Manager\" OR \"Hiring Manager\" recruiter OR \"Talent Acquisition\")",
   "site:linkedin.com/in \""+company+"\" (\"Backend Engineer\" OR \"Software Engineer\" OR \"Java Engineer\" OR \"Engineering Lead\")"
  );
 }
 private ReferralModels.ReferralTarget candidate(String company,String role,String q,ReferralModels.SearchResult r){
  if(!isProfile(r.url()))return null;
  String[] p=extract(r.title());
  if(p[0].isBlank())return null;
  String combined=(r.title()+" "+r.snippet()).toLowerCase(Locale.ROOT);
  List<String> companyHits=tokens(company).stream().filter(combined::contains).toList();
  List<String> roleHits=tokens(role).stream().filter(combined::contains).toList();
  List<String> laneHits=LANES.stream().flatMap(List::stream).filter(x->combined.contains(x.toLowerCase(Locale.ROOT))).distinct().toList();
  String cat=LANES.get(0).stream().anyMatch(x->combined.contains(x.toLowerCase(Locale.ROOT)))?"hiring":
      LANES.get(1).stream().anyMatch(x->combined.contains(x.toLowerCase(Locale.ROOT)))?"recruiting":"team";
  double score=.10+(companyHits.isEmpty()?0:.40)+Math.min(.30,.10*roleHits.size())+(laneHits.isEmpty()?0:.20);
  if(companyHits.isEmpty()&&roleHits.isEmpty()&&laneHits.isEmpty())return null;
  String reason=!laneHits.isEmpty()?"Potential "+laneHits.getFirst()+" contact for a "+role+" role at "+company+".":
      !roleHits.isEmpty()?"Profile metadata matches the target "+role+" role at "+company+".":
      "Public profile discovered for "+company+"; role relevance needs manual verification.";
  List<String> terms=new ArrayList<>(); terms.addAll(companyHits);terms.addAll(roleHits);terms.addAll(laneHits);
  return new ReferralModels.ReferralTarget(p[0],p[1].isBlank()?null:p[1],company,normalise(r.url()),reason,
      new ArrayList<>(new LinkedHashSet<>(terms)),Math.min(1,Math.round(score*100)/100d),q,
      "Matched public search metadata; verify current employer/title before contacting.",cat);
 }
 private String[] extract(String title){
  String clean=title.replaceFirst("(?i)\\s*\\|\\s*LinkedIn\\s*$","").trim();
  String[] parts=clean.split("\\s+-\\s+",2);
  if(parts.length==0||parts[0].length()>80||Set.of("linkedin","profile","people").contains(parts[0].toLowerCase(Locale.ROOT)))return new String[]{"",""};
  return new String[]{parts[0],parts.length>1?parts[1]:""};
 }
 private Set<String> tokens(String s){Set<String>out=new LinkedHashSet<>();for(String x:s.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+"))if(x.length()>=3)out.add(x);return out;}
 private boolean isProfile(String u){try{URI p=URI.create(u);return p.getHost()!=null&&p.getHost().toLowerCase(Locale.ROOT).endsWith("linkedin.com")&&p.getPath().toLowerCase(Locale.ROOT).contains("/in/");}catch(Exception e){return false;}}
 private String normalise(String u){try{URI p=URI.create(u);String host=p.getHost();if("linkedin.com".equalsIgnoreCase(host))host="www.linkedin.com";return new URI(p.getScheme().toLowerCase(Locale.ROOT),host,p.getPath().replaceAll("/$",""),null,null).toString();}catch(Exception e){return u;}}
}
