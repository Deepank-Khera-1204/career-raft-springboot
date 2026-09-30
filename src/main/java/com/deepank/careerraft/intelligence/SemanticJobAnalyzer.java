package com.deepank.careerraft.intelligence;

import com.deepank.careerraft.career.CareerKnowledgeBase;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SemanticJobAnalyzer {
    private final LLMProvider provider;
    private final CareerKnowledgeBase kb;
    private final TechnicalConceptMatcher concepts;
    private final EvidenceCatalog catalog;

    public SemanticJobAnalyzer(
            LLMProvider provider,
            CareerKnowledgeBase kb,
            TechnicalConceptMatcher concepts,
            EvidenceCatalog catalog) {
        this.provider = provider;
        this.kb = kb;
        this.concepts = concepts;
        this.catalog = catalog;
    }

    public SemanticModels.SemanticAnalysisResult analyze(String description, String title) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Job description is required for semantic analysis.");
        }
        Map<String, Object> raw = provider.generateJson(buildPrompt(description, title), schema());
        return new SemanticModels.SemanticAnalysisResult(parse(raw), provider.name());
    }

    private Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("role_family", stringSchema());
        properties.put("seniority", stringSchema());
        properties.put("experience_requirement", experienceSchema());
        properties.put("domain_signals", arraySchema());
        properties.put("requirements", requirementsSchema());
        properties.put("responsibilities", arraySchema());
        properties.put("keywords", arraySchema());
        properties.put("evidence_matches", evidenceMatchesSchema());

        schema.put("properties", properties);
        schema.put("required", List.of(
                "role_family", "seniority", "experience_requirement",
                "domain_signals", "requirements", "responsibilities",
                "keywords", "evidence_matches"
        ));
        return schema;
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> arraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private Map<String, Object> experienceSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("minimum_years", Map.of("type", List.of("number", "null")));
        properties.put("maximum_years", Map.of("type", List.of("number", "null")));
        properties.put("status", Map.of(
                "type", "string",
                "enum", List.of("required", "preferred", "not_stated")
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("minimum_years", "maximum_years", "status")
        );
    }

    private Map<String, Object> requirementsSchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("concept", stringSchema());
        itemProperties.put("importance", stringSchema());
        itemProperties.put("evidence_signals", arraySchema());
        itemProperties.put("evidence_ids", arraySchema());
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", itemProperties,
                        "required", List.of(
                                "concept", "importance",
                                "evidence_signals", "evidence_ids"
                        )
                )
        );
    }

    private Map<String, Object> evidenceMatchesSchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("evidence_id", stringSchema());
        itemProperties.put("relevance", Map.of("type", "number"));
        itemProperties.put("rationale", stringSchema());
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", itemProperties,
                        "required", List.of(
                                "evidence_id", "relevance", "rationale"
                        )
                )
        );
    }

    private String buildPrompt(String description, String title) {
        StringBuilder evidence = new StringBuilder();
        for (EvidenceClaim claim : catalog.claims()) {
            evidence.append(Map.of(
                    "id", claim.id(),
                    "type", claim.type().name().toLowerCase(Locale.ROOT),
                    "title", claim.title(),
                    "text", claim.text(),
                    "keywords", claim.keywords(),
                    "technologies", claim.technologies()
            )).append('\n');
        }

        StringBuilder conceptContext = new StringBuilder();
        for (TechnicalConceptMatcher.ConceptMatch match :
                concepts.matches(title + "\n" + description)) {
            conceptContext.append(Map.of(
                    "id", match.concept().id(),
                    "signals", match.concept().signals(),
                    "verified_evidence_ids", match.concept().evidenceIds()
            )).append('\n');
        }

        return """
                Analyze this job posting against the candidate Career Knowledge Base.
                This is evidence-backed matching, not resume invention.
                Understand requirements by meaning, not only exact words.
                Match only IDs present in candidate evidence.
                A concept mapping never creates a candidate claim.
                For each requirement identify the smallest supporting evidence IDs
                and use an empty list when unsupported.
                Do not infer employer, technology, metric, responsibility, seniority,
                qualification or domain experience without evidence.
                Experience requirement must be required, preferred, or not_stated
                and never invented. Return only JSON matching the schema.

                JOB TITLE:
                %s

                JOB DESCRIPTION:
                %s

                TECHNICAL CONCEPT MAP:
                %s

                CANDIDATE EVIDENCE:
                %s
                """.formatted(title, description, conceptContext, evidence);
    }

    private SemanticModels.JobUnderstanding parse(Map<String, Object> raw) {
        Set<String> allowed = new HashSet<>(
                kb.directSkills().stream().map(s -> "SKILL::" + s).toList()
        );
        allowed.addAll(kb.confirmedExperience().stream()
                .map(x -> Objects.toString(x.get("id"))).toList());

        for (Map<String, Object> project : kb.projects()) {
            allowed.add(Objects.toString(project.get("id")));
            for (Object candidate : listObjects(project.get("claims"))) {
                if (candidate instanceof Map<?, ?> m
                        && "confirmed".equals(Objects.toString(m.get("evidence_status")))) {
                    allowed.add(Objects.toString(m.get("id")));
                }
            }
        }

        List<SemanticModels.SemanticEvidenceMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object candidate : listObjects(raw.get("evidence_matches"))) {
            if (!(candidate instanceof Map<?, ?> m)) continue;
            String id = Objects.toString(m.get("evidence_id"), "").trim();
            String rationale = Objects.toString(m.get("rationale"), "").trim();
            double relevance = number(m.get("relevance"));
            if (allowed.contains(id) && seen.add(id) && !rationale.isBlank()) {
                matches.add(new SemanticModels.SemanticEvidenceMatch(
                        id,
                        Math.max(0, Math.min(1, relevance)),
                        rationale
                ));
            }
        }

        List<SemanticModels.Requirement> requirements = new ArrayList<>();
        for (Object candidate : listObjects(raw.get("requirements"))) {
            if (!(candidate instanceof Map<?, ?> m)) continue;
            String concept = Objects.toString(m.get("concept"), "").trim();
            if (concept.isBlank()) continue;

            List<String> ids = stringList(m.get("evidence_ids")).stream()
                    .filter(allowed::contains)
                    .filter(id -> matches.stream()
                            .anyMatch(x -> x.evidenceId().equals(id)))
                    .toList();

            requirements.add(new SemanticModels.Requirement(
                    concept,
                    Objects.toString(m.get("importance"), "").trim(),
                    stringList(m.get("evidence_signals")),
                    ids
            ));
        }

        Map<String, Object> experience = map(raw.get("experience_requirement"));
        Double minimumYears = nullable(experience.get("minimum_years"));
        Double maximumYears = nullable(experience.get("maximum_years"));

        if (minimumYears == null) {
            maximumYears = null;
        } else if (maximumYears != null && maximumYears < minimumYears) {
            maximumYears = null;
        }

        String status = Objects.toString(
                experience.getOrDefault("status", "not_stated")
        ).trim().toLowerCase(Locale.ROOT);

        if (!Set.of("required", "preferred", "not_stated").contains(status)) {
            status = "not_stated";
        }

        matches.sort(
                Comparator.comparingDouble(
                        SemanticModels.SemanticEvidenceMatch::relevance
                ).reversed()
                .thenComparing(SemanticModels.SemanticEvidenceMatch::evidenceId)
        );

        return new SemanticModels.JobUnderstanding(
                Objects.toString(raw.get("role_family"), "").trim(),
                Objects.toString(raw.get("seniority"), "").trim(),
                new SemanticModels.ExperienceRequirement(
                        minimumYears, maximumYears, status
                ),
                stringList(raw.get("domain_signals")),
                requirements,
                stringList(raw.get("responsibilities")),
                stringList(raw.get("keywords")),
                matches
        );
    }

    private static double number(Object value) {
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private static Double nullable(Object value) {
        try {
            double number = Double.parseDouble(String.valueOf(value));
            return number >= 0 ? number : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) (Map<?, ?>) map
                : Map.of();
    }

    private static List<?> listObjects(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
