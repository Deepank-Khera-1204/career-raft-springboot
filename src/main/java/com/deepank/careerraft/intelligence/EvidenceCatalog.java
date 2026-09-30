package com.deepank.careerraft.intelligence;

import com.deepank.careerraft.career.CareerKnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class EvidenceCatalog {
    private final CareerKnowledgeBase knowledgeBase;
    private final List<EvidenceClaim> claims;

    public EvidenceCatalog(CareerKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
        this.claims = buildClaims(knowledgeBase);
    }

    public CareerKnowledgeBase knowledgeBase() { return knowledgeBase; }
    public List<EvidenceClaim> claims() { return claims; }
    public Map<String, EvidenceClaim> byId() { return claims.stream().collect(java.util.stream.Collectors.toMap(EvidenceClaim::id, c -> c, (a,b)->a, LinkedHashMap::new)); }
    public List<EvidenceClaim> claimsForType(EvidenceType type) { return claims.stream().filter(c -> c.type() == type).toList(); }
    public EvidenceClaim get(String id) {
        EvidenceClaim claim = byId().get(id);
        if (claim == null) throw new IllegalArgumentException("Unknown evidence claim: " + id);
        return claim;
    }

    private static List<EvidenceClaim> buildClaims(CareerKnowledgeBase kb) {
        List<EvidenceClaim> result = new ArrayList<>();
        for (Map<String,Object> item : kb.confirmedExperience()) {
            String text = Objects.toString(item.get("text"), "");
            List<String> keywords = new ArrayList<>();
            Object configured = item.get("keywords");
            if (configured instanceof List<?> list) list.forEach(v -> keywords.add(Objects.toString(v, "")));
            var matcher = Pattern.compile("[A-Za-z][A-Za-z0-9+#.-]{1,}").matcher(text);
            while (matcher.find()) keywords.add(matcher.group());
            result.add(new EvidenceClaim(Objects.toString(item.get("id")), EvidenceType.EXPERIENCE,
                    "data/experience.yaml", "Confirmed experience — " + item.get("id"), text,
                    unique(keywords), List.of(), List.of(text), false, stringList(item.get("source_ids"))));
        }
        for (Map<String,Object> project : kb.projects()) {
            List<String> tech = stringList(project.get("technologies"));
            List<String> facts = stringList(project.get("known_evidence"));
            String evidenceText = facts.isEmpty() ? Objects.toString(project.get("name"), "") : String.join("; ", facts);
            String id = Objects.toString(project.get("id"));
            result.add(new EvidenceClaim(id, EvidenceType.PROJECT, "data/projects.yaml", Objects.toString(project.get("name")), evidenceText,
                    unique(concat(List.of(Objects.toString(project.get("name")), ""), tech)), tech, facts, false, stringList(project.get("source_ids"))));
            Object rawClaims = project.get("claims");
            if (rawClaims instanceof List<?> list) for (Object raw : list) if (raw instanceof Map<?,?> rawMap) {
                Map<String,Object> claim = castMap(rawMap);
                if (!"confirmed".equals(Objects.toString(claim.get("evidence_status"), ""))) continue;
                List<String> kw = new ArrayList<>(List.of(Objects.toString(project.get("name"), "")));
                kw.addAll(tech); kw.addAll(stringList(claim.get("keywords")));
                result.add(new EvidenceClaim(Objects.toString(claim.get("id")), EvidenceType.PROJECT, "data/projects.yaml",
                        Objects.toString(project.get("name")), Objects.toString(claim.get("text")), unique(kw), tech,
                        List.of(Objects.toString(claim.get("text"))), false,
                        stringList(claim.get("source_ids")).isEmpty() ? stringList(project.get("source_ids")) : stringList(claim.get("source_ids"))));
            }
        }
        for (Map<String,Object> skill : kb.skills()) {
            String name = Objects.toString(skill.get("name"));
            result.add(new EvidenceClaim("SKILL::" + name, EvidenceType.SKILL, "data/skills.yaml", name, name,
                    List.of(name), List.of(name), List.of(), false, stringList(skill.get("source_ids"))));
        }
        for (Map<String,Object> education : kb.education()) {
            if (!"confirmed".equals(Objects.toString(education.get("evidence_status"), ""))) continue;
            String cgpa = Objects.toString(education.get("cgpa"), "");
            String details = Objects.toString(education.get("degree")) + "; CGPA " + cgpa + "; " + Objects.toString(education.get("institution"));
            result.add(new EvidenceClaim(Objects.toString(education.get("id")), EvidenceType.EDUCATION, "data/education.yaml",
                    Objects.toString(education.get("degree")), details, List.of(), List.of(), List.of("CGPA " + cgpa,
                    Objects.toString(education.get("start")) + " - " + Objects.toString(education.get("end"))), true, stringList(education.get("source_ids"))));
        }
        return List.copyOf(result);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(v -> Objects.toString(v, "")).filter(s -> !s.isBlank()).toList();
    }
    private static List<String> unique(List<String> values) { return new ArrayList<>(new LinkedHashSet<>(values)); }
    private static List<String> concat(List<String> a, List<String> b) { ArrayList<String> r=new ArrayList<>(a); r.removeIf(String::isBlank); r.addAll(b); return r; }
    @SuppressWarnings("unchecked") private static Map<String,Object> castMap(Map<?,?> m) { return (Map<String,Object>)(Map<?,?>)m; }
}
