package com.deepank.careerraft.referrals;
import java.util.List;
public final class ReferralModels {
 private ReferralModels(){}
 public record SearchResult(String title,String url,String snippet){}
 public record ReferralTarget(String name,String title,String company,String profileUrl,String relevance,List<String>matchedTerms,double confidence,String sourceQuery,String verificationNotes,String category){
  public ReferralTarget{matchedTerms=matchedTerms==null?List.of():List.copyOf(matchedTerms);}
 }
}
