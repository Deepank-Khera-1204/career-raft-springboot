package com.deepank.careerraft.intelligence;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class TechnicalConceptMatcher {
  private final EvidenceCatalog catalog;
  private final List<TechnicalConcept> concepts=List.of(
    c("software_development",List.of("software development","software engineering","coding","write code","development code"),List.of("EXP-NW-001","EXP-NW-002","EXP-NW-011")),
    c("debugging_and_operations",List.of("debugging","debug","troubleshooting","production issues","production systems"),List.of("EXP-NW-006","EXP-NW-013")),
    c("scalability_and_reliability",List.of("scaling","scalability","scalable","reliability","performance"),List.of("EXP-NW-009","EXP-NW-010")),
    c("multi_service_engineering",List.of("multiple services","microservices","service architecture","service-oriented architecture"),List.of("EXP-NW-001","EXP-NW-002")),
    c("distributed_systems",List.of("distributed systems","distributed system","distributed computing","distributed services"),List.of("PROJ-RAFT-001","PROJ-RAFT-002","PROJ-RAFT-003","PROJ-RATE-001")),
    c("consensus",List.of("consensus","consensus algorithm","consensus protocols"),List.of("PROJ-RAFT-001","PROJ-RAFT-003")),
    c("leader_election",List.of("leader election","leader failover","leader failure"),List.of("PROJ-RAFT-001","PROJ-RAFT-002")),
    c("fault_tolerance",List.of("fault tolerant","fault-tolerant","fault tolerance","node failures","failure recovery"),List.of("PROJ-RAFT-001","PROJ-RAFT-002","PROJ-RAFT-003")),
    c("replicated_state",List.of("replicated state machine","replicated state machines","state machine replication","log replication"),List.of("PROJ-RAFT-001","PROJ-RAFT-003")),
    c("container_orchestration",List.of("container orchestration","container orchestration platform","orchestration of containers"),List.of("EXP-NW-006","PROJ-RATE-001")),
    c("cloud_platform",List.of("cloud platform","cloud computing","public cloud","cloud infrastructure"),List.of("EXP-NW-005","EXP-NW-006")),
    c("unit_testing",List.of("unit testing","unit tests","automated unit testing","automated testing"),List.of("EXP-NW-012","PROJ-RATE-002")),
    c("rest_api",List.of("rest api","rest apis","restful api","restful apis","restful services","http api","web api"),List.of("EXP-NW-011","PROJ-PAY-001")),
    c("microservices",List.of("microservices","microservice architecture","service-oriented architecture","distributed services"),List.of("EXP-NW-001","PROJ-PAY-001")),
    c("relational_database",List.of("relational database","relational databases","sql database","sql databases"),List.of("EXP-NW-011","EXP-NW-005","PROJ-PAY-001")),
    c("messaging",List.of("message broker","messaging system","event-driven architecture","event driven architecture"),List.of("EXP-NW-007")),
    c("api_gateway",List.of("api gateway","api gateways","api management","api proxy"),List.of("EXP-NW-001")),
    c("rate_limiting",List.of("rate limiting","rate limit","request throttling","throttling"),List.of("PROJ-RATE-001")),
    c("load_testing",List.of("load testing","load test","performance testing","stress testing"),List.of("EXP-NW-009","PROJ-RATE-002")),
    c("ci_cd",List.of("continuous integration","continuous delivery","continuous deployment","ci/cd","cicd"),List.of("PROJ-RATE-002","EXP-NW-006"))
  );
  public TechnicalConceptMatcher(EvidenceCatalog catalog){this.catalog=catalog;}
  public List<ConceptMatch> matches(String text){return concepts.stream().filter(c->c.signals().stream().anyMatch(s->contains(text,s))).map(c->new ConceptMatch(c,catalogEvidence(c))).filter(m->!m.evidence().isEmpty()).toList();}
  private List<EvidenceClaim> catalogEvidence(TechnicalConcept c){Map<String,EvidenceClaim> by=catalog.byId();return c.evidenceIds().stream().map(by::get).filter(Objects::nonNull).filter(e->e.type()==EvidenceType.EXPERIENCE||e.type()==EvidenceType.PROJECT).toList();}
  private static TechnicalConcept c(String id,List<String>s,List<String>e){return new TechnicalConcept(id,s,e);}
  private static boolean contains(String text,String signal){String a=norm(text),b=norm(signal);return !b.isBlank()&&Pattern.compile("(?<![a-z0-9])"+Pattern.quote(b)+"(?![a-z0-9])").matcher(a).find();}
  private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#.]"," ").replaceAll("\\s+"," ").trim();}
  public record ConceptMatch(TechnicalConcept concept,List<EvidenceClaim> evidence){}
}
