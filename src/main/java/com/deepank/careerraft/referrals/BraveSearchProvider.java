package com.deepank.careerraft.referrals;
import com.deepank.careerraft.discovery.SimpleHttpClient;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class BraveSearchProvider{
 private final SimpleHttpClient http;
 public BraveSearchProvider(SimpleHttpClient h){http=h;}
 public List<ReferralModels.SearchResult> search(String query,int limit){
  String key=System.getenv("BRAVE_SEARCH_API_KEY");
  if(key==null||key.isBlank()) return List.of();
  limit=Math.max(1,Math.min(20,limit));
  String url="https://api.search.brave.com/res/v1/web/search?q="+URLEncoder.encode(query,StandardCharsets.UTF_8)+"&country=IN&search_lang=en&count="+limit;
  try{
   var r=http.get(url,"application/json",Map.of("X-Subscription-Token",key,"User-Agent","career-raft/1.0"));
   var n=new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.body());
   List<ReferralModels.SearchResult> out=new ArrayList<>();
   for(var x:n.path("web").path("results")){
    String t=x.path("title").asText(""),u=x.path("url").asText(""),s=x.path("description").asText("");
    if(!t.isBlank()&&!u.isBlank()) out.add(new ReferralModels.SearchResult(t.trim(),u.trim(),s.trim()));
   }
   return out;
  }catch(Exception e){throw new IllegalStateException("Referral search request failed",e);}
 }
}
