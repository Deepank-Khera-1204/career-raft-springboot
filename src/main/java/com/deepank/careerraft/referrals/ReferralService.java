package com.deepank.careerraft.referrals;
import com.deepank.careerraft.repository.JobRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
@Service public class ReferralService{
 private final PublicWebReferralResearcher researcher;private final JobRepository repository;
 public ReferralService(PublicWebReferralResearcher r,JobRepository repo){researcher=r;repository=repo;}
 public List<ReferralModels.ReferralTarget> research(String company,String role,int limit){
  if(System.getenv("BRAVE_SEARCH_API_KEY")==null)return List.of();
  if(!repository.reserveReferralRequests(Instant.now(),3,30))return List.of();
  return researcher.findTargets(company,role,Math.max(1,limit));
 }
}
