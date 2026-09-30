package com.deepank.careerraft.scoring;

import com.deepank.careerraft.domain.Job;
import com.deepank.careerraft.domain.JobAssessment;
import com.deepank.careerraft.domain.ScoreBreakdown;
import com.deepank.careerraft.intelligence.EvidenceClaim;
import com.deepank.careerraft.intelligence.EvidenceType;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import com.deepank.careerraft.config.RuntimePaths;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ScoringEngine {
    public static final String SCORING_VERSION = "0.8";

    private static final Map<String, List<String>> TECHNICAL_SKILL_ALIASES = Map.ofEntries(
            Map.entry("java", List.of("java")),
            Map.entry("c++", List.of("c++", "cpp")),
            Map.entry("spring boot", List.of("spring boot", "springboot")),
            Map.entry("rest apis", List.of("rest api", "rest apis", "restful api", "restful apis", "restful")),
            Map.entry("microservices", List.of("microservice", "microservices", "service-oriented")),
            Map.entry("postgresql", List.of("postgres", "postgresql")),
            Map.entry("kubernetes", List.of("kubernetes", "k8s")),
            Map.entry("docker", List.of("docker", "containerized", "containerisation", "containerization")),
            Map.entry("aws", List.of("aws", "amazon web services")),
            Map.entry("kafka", List.of("kafka", "event streaming")),
            Map.entry("junit", List.of("junit")),
            Map.entry("redis", List.of("redis"))
    );

    private static final Map<String, String[]> DEGREE_PATTERNS = Map.of(
            "doctorate", new String[]{"\\bph\\.?d\\.?\\b", "\\bdoctorate\\b", "\\bdoctoral\\b"},
            "master", new String[]{"\\bmaster(?:'s|s)?\\b", "\\bm\\.?s\\.?\\b", "\\bm\\.?tech\\.?\\b", "\\bm\\.?e\\.?\\b", "\\bmba\\b"},
            "bachelor", new String[]{"\\bbachelor(?:'s|s)?\\b", "\\bb\\.?tech\\.?\\b", "\\bb\\.?e\\.?\\b", "\\bb\\.?s\\.?\\b", "\\bundergraduate degree\\b"}
    );

    private static final Pattern REQUIRED_EDUCATION =
            Pattern.compile("\\b(?:required|required qualification|mandatory|must have|must possess|candidate must|requires|minimum qualifications?|basic qualifications?|minimum requirement)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFERRED_EDUCATION =
            Pattern.compile("\\b(?:preferred|preferably|nice to have|desired|bonus|plus)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_SPLIT = Pattern.compile("\\s+(?:or|and)\\s+|[,/;&]");

    private final Map<String, Double> weights;
    private final Map<String, Double> thresholds;
    private final double unknownSalaryFraction;
    private final double unknownLocationFraction;
    private final com.deepank.careerraft.intelligence.TechnicalConceptMatcher conceptMatcher;
    private final com.deepank.careerraft.intelligence.EvidenceMatcher evidenceMatcher;

    public ScoringEngine(
            com.deepank.careerraft.intelligence.TechnicalConceptMatcher conceptMatcher,
            com.deepank.careerraft.intelligence.EvidenceMatcher evidenceMatcher) {
        this.conceptMatcher = conceptMatcher;
        this.evidenceMatcher = evidenceMatcher;
        Map<String, Object> root = loadScoringConfig();
        this.weights = doubles(map(root.get("weights")));
        this.thresholds = doubles(map(root.get("thresholds")));
        Map<String, Object> rules = map(root.get("rules"));
        this.unknownSalaryFraction = Double.parseDouble(Objects.toString(rules.getOrDefault("unknown_salary_score_fraction", 0.5)));
        this.unknownLocationFraction = Double.parseDouble(Objects.toString(rules.getOrDefault("unknown_location_score_fraction", 0.5)));
    }

    public HardFilterResult hardFilter(Job job, CandidateProfile candidate) {
        List<String> reasons = new ArrayList<>();
        String title = Objects.toString(job.title(), "");

        for (String exclusion : candidate.hardExclusions()) {
            String key = normalize(exclusion);
            if ("internship".equals(key)) {
                String employment = normalize(job.employmentType());
                if (employment.contains("intern") || contains(title, "internship") || contains(title, "intern")) {
                    reasons.add("Job title/structured employment type identifies the role as an internship.");
                }
                continue;
            }

            Map<String, List<String>> titleAliases = Map.of(
                    "mobile-only", List.of("mobile engineer", "mobile developer", "android engineer", "android developer", "ios engineer", "ios developer"),
                    "manual qa", List.of("manual qa", "manual tester", "manual test engineer", "quality assurance tester", "qa tester"),
                    "sales", List.of("sales engineer", "sales developer", "sales representative", "sales manager", "sales"),
                    "unpaid", List.of("unpaid intern", "unpaid internship", "unpaid role", "unpaid position")
            );
            List<String> aliases = titleAliases.getOrDefault(key, List.of(exclusion));
            if (aliases.stream().anyMatch(alias -> contains(title, alias))) {
                reasons.add("Job title identifies excluded category: " + exclusion);
            }
        }

        Double requiredExperience = JobTextParser.parseRequiredExperience(job.description());
        if (requiredExperience != null && requiredExperience > candidate.experienceCeilingYears()) {
            reasons.add("Requires at least " + fmt(requiredExperience)
                    + " years; configured experience ceiling is "
                    + fmt(candidate.experienceCeilingYears()) + " years.");
        }

        reasons.addAll(educationHardFailures(title, Objects.toString(job.description(), ""), candidate.education()));

        for (String level : candidate.hardSeniorityExclusions()) {
            if (contains(title, level)) {
                reasons.add("Contains excluded seniority level: " + level);
            }
        }

        return new HardFilterResult(reasons.isEmpty(), reasons);
    }

    public JobAssessment assess(Job job, CandidateProfile candidate) {
        HardFilterResult filter = hardFilter(job, candidate);
        String text = Objects.toString(job.title(), "") + System.lineSeparator() + Objects.toString(job.description(), "");

        List<String> matched = technicalHits(text, candidate.coreSkills());
        List<String> missing = candidate.coreSkills().stream()
                .filter(skill -> !matched.contains(skill))
                .toList();

        double directComponent = Math.min(15.0,
                15.0 * matched.size() / Math.max(4, Math.min(candidate.coreSkills().size(), 8)));

        double roleFamilyComponent = 0.0;
        List<String> roleSignals = List.of(
                "software engineer", "software development engineer",
                "software developer", "application engineer", "systems engineer"
        );
        if (roleSignals.stream().anyMatch(signal -> contains(job.title(), signal))) {
            roleFamilyComponent = 5.0;
        } else if (contains(text, "software development") || contains(text, "software engineering")) {
            roleFamilyComponent = 3.0;
        }

        List<Map<String, Object>> conceptMatches = conceptMatcher.matches(text).stream().map(match -> {
            List<String> evidenceIds = match.evidence().stream().map(EvidenceClaim::id).toList();
            return Map.<String, Object>of(
                    "concept_id", match.concept().id(),
                    "signals", match.concept().signals(),
                    "evidence_ids", evidenceIds,
                    "evidence", match.evidence().stream().map(e -> Map.of("id", e.id(), "text", e.text())).toList()
            );
        }).toList();

        int conceptCount = conceptMatches.size();
        double conceptComponent = Math.min(5.0,
                5.0 * conceptCount / Math.max(3, Math.min(6, conceptCount == 0 ? 3 : conceptCount)));

        double evidenceComponent = conceptMatches.isEmpty()
                ? 0.0
                : Math.min(5.0, 5.0 * conceptMatches.size() / 4.0);

        double technical = Math.min(weight("technical_stack"),
                directComponent + roleFamilyComponent + conceptComponent + evidenceComponent);

        RoleScore roleScore = roleScore(job.title(), candidate.preferredRoles());
        Double requiredExperience = JobTextParser.parseRequiredExperience(job.description());

        double qualificationExperienceFit;
        String qualificationReason;
        if (requiredExperience == null) {
            qualificationExperienceFit = 1.0;
            qualificationReason = "No explicit minimum experience requirement detected.";
        } else if (requiredExperience <= candidate.experienceYears()) {
            qualificationExperienceFit = 1.0;
            qualificationReason = "Detected minimum experience requirement of " + fmt(requiredExperience)
                    + " years, within configured " + fmt(candidate.experienceYears()) + " years.";
        } else {
            qualificationExperienceFit = 0.0;
            qualificationReason = "Detected minimum experience requirement of " + fmt(requiredExperience)
                    + " years, above configured " + fmt(candidate.experienceYears()) + " years.";
        }

        double qualificationDegreeFit = 1.0;
        String degreeReason = "No unsupported degree assumption was needed.";
        if (!candidate.education().isEmpty()) {
            degreeReason = "Configured education evidence was considered for mandatory qualification gates.";
        }
        double qualifications = weight("qualifications") *
                (0.70 * qualificationExperienceFit + 0.30 * qualificationDegreeFit);

        List<String> selectedProjects = List.of();
        double projectFit = 0.0;
        String projectReason;
        List<com.deepank.careerraft.intelligence.EvidenceMatch> projectMatches =
                evidenceMatcher.projectRecommendations(job.description(), 3);
        if (!projectMatches.isEmpty()) {
            selectedProjects = projectMatches.stream()
                    .map(com.deepank.careerraft.intelligence.EvidenceMatch::claimId)
                    .toList();
            projectFit = Math.min(1.0,
                    projectMatches.stream().mapToDouble(com.deepank.careerraft.intelligence.EvidenceMatch::score).sum()
                            / (projectMatches.size() * 0.75));
            projectReason = "Evidence matcher found " + projectMatches.size()
                    + " relevant verified project claims.";
        } else {
            projectReason = "No project evidence overlap found in the verified catalog.";
        }
        double projects = weight("project_evidence") * projectFit;

        String locationText = (Objects.toString(job.location(), "") + " "
                + Objects.toString(job.workMode(), "")).trim();
        double location;
        String locationReason;
        if (candidate.preferredLocations().stream().anyMatch(value -> contains(locationText, value))) {
            location = weight("location_work_mode");
            locationReason = "Location/work mode matches a configured preference.";
        } else if (contains(job.workMode(), "remote")) {
            location = weight("location_work_mode") * 0.80;
            locationReason = "Remote work mode is compatible with the configured Remote India preference.";
        } else if (contains(job.location(), "india")) {
            location = weight("location_work_mode") * 0.60;
            locationReason = "India location detected, but it is not one of the explicitly preferred locations.";
        } else {
            location = weight("location_work_mode") * unknownLocationFraction;
            locationReason = "Location/work mode is unknown; a neutral partial score is used rather than assuming incompatibility.";
        }

        List<String> matchedDomains = candidate.preferredDomains().stream()
                .filter(domain -> contains(text, domain))
                .toList();
        double domain = weight("domain") * matchedDomains.size() / Math.max(1, candidate.preferredDomains().size());

        double[] salary = JobTextParser.parseLpaRange(job.salaryText());
        double salaryFloor = candidate.minimumLpa();
        double compensation;
        String compensationReason;
        if (Double.isNaN(salary[0])) {
            compensation = weight("compensation") * unknownSalaryFraction;
            compensationReason = "No salary range detected; compensation receives a neutral partial score.";
        } else if (salary[1] < salaryFloor) {
            compensation = 0.0;
            compensationReason = "Published maximum " + fmt(salary[1]) + " LPA is below configured minimum "
                    + fmt(salaryFloor) + " LPA.";
        } else if (salary[0] >= salaryFloor) {
            compensation = weight("compensation");
            compensationReason = "Published range starts at " + fmt(salary[0])
                    + " LPA, meeting the configured " + fmt(salaryFloor) + " LPA floor.";
        } else {
            compensation = weight("compensation") * 0.5;
            compensationReason = "Published range overlaps the configured " + fmt(salaryFloor)
                    + " LPA floor but starts below it.";
        }

        double growthFit = roleScore.matchedRole() != null ? 1.0
                : (!matched.isEmpty() || !matchedDomains.isEmpty() ? 0.75 : 0.5);
        double growth = weight("growth") * growthFit;
        String growthReason = "Career-alignment proxy uses preferred-role/domain/skill overlap; it is not a prediction of future career growth.";

        ScoreBreakdown breakdown = new ScoreBreakdown(
                round(technical), round(roleScore.points()), round(qualifications), round(projects),
                round(location), round(compensation), round(domain), round(growth)
        );

        double deep = threshold("deep_analysis", 70);
        double packageThreshold = threshold("package_generation", 80);
        double priority = threshold("priority_email", 90);

        JobAssessment.NextAction action;
        if (!filter.pass() || breakdown.total() < deep) {
            action = JobAssessment.NextAction.IGNORE;
        } else if (breakdown.total() < packageThreshold) {
            action = JobAssessment.NextAction.REVIEW;
        } else if (breakdown.total() < priority) {
            action = JobAssessment.NextAction.GENERATE_PACKAGE;
        } else {
            action = JobAssessment.NextAction.GENERATE_PACKAGE_PRIORITY;
        }

        String conceptSummary = conceptMatches.stream()
                .map(match -> match.get("concept_id") + " -> "
                        + String.join(", ", castList(match.get("evidence_ids"))))
                .collect(java.util.stream.Collectors.joining("; "));
        if (conceptSummary.isBlank()) conceptSummary = "none";

        String technicalReason = matched.size() + "/" + candidate.coreSkills().size()
                + " direct core skills matched: " + String.join(", ", matched.isEmpty() ? List.of("none") : matched)
                + ". Verified concepts: " + conceptSummary
                + ". Technical points = direct " + fmt(directComponent)
                + " + role-family " + fmt(roleFamilyComponent)
                + " + concept " + fmt(conceptComponent)
                + " + evidence " + fmt(evidenceComponent) + ".";

        Map<String, String> explanations = new LinkedHashMap<>();
        explanations.put("technical_stack", technicalReason);
        explanations.put("role_seniority", roleScore.reason());
        explanations.put("qualifications", qualificationReason + " " + degreeReason);
        explanations.put("project_evidence", projectReason);
        explanations.put("location_work_mode", locationReason);
        explanations.put("compensation", compensationReason);
        explanations.put("domain", "Matched preferred domains: "
                + (matchedDomains.isEmpty() ? "none" : String.join(", ", matchedDomains)) + ".");
        explanations.put("growth", growthReason);

        List<String> rationale = List.of(
                technicalReason, roleScore.reason(),
                qualificationReason + " " + degreeReason, projectReason,
                locationReason, compensationReason,
                explanations.get("domain"), growthReason
        );

        return new JobAssessment(
                job.id(), filter.pass(), filter.reasons(), breakdown,
                matched, missing, selectedProjects, rationale, explanations,
                matchedDomains, roleScore.matchedRole(), requiredExperience,
                Double.isNaN(salary[0]) ? null : salary[0],
                Double.isNaN(salary[1]) ? null : salary[1],
                action, SCORING_VERSION, "not_run", null, 0.0, 0.0,
                List.of(), List.of(), null, conceptMatches, null
        );
    }

    private RoleScore roleScore(String title, List<String> preferredRoles) {
        for (String role : preferredRoles) {
            if (contains(title, role)) {
                return new RoleScore(weight("role_seniority"), role,
                        "Exact/contained preferred role match: " + role + ".");
            }
        }
        List<String> backendSignals = List.of("backend", "software engineer", "java", "spring", "platform", "server");
        if (backendSignals.stream().anyMatch(signal -> contains(title, signal))) {
            return new RoleScore(round(weight("role_seniority") * 0.80), null,
                    "Backend/software engineering signal present, but title is not an exact configured role.");
        }
        if (contains(title, "engineer") || contains(title, "developer")) {
            return new RoleScore(round(weight("role_seniority") * 0.45), null,
                    "Engineering/developer title is relevant but not specifically aligned to the preferred role list.");
        }
        return new RoleScore(0, null, "Title is outside the preferred engineering role family.");
    }

    private List<String> technicalHits(String text, List<String> skills) {
        return skills.stream()
                .filter(skill -> TECHNICAL_SKILL_ALIASES
                        .getOrDefault(skill.toLowerCase(Locale.ROOT), List.of(skill))
                        .stream().anyMatch(alias -> contains(text, alias)))
                .toList();
    }

    private List<String> educationHardFailures(String title, String description,
                                                List<Map<String, Object>> education) {
        String candidateLevel = candidateEducationLevel(education);
        Map<String, Integer> rank = Map.of("bachelor", 1, "master", 2, "doctorate", 3);
        List<String> failures = new ArrayList<>();

        String titleLevel = degreeLevel(title);
        if (titleLevel != null
                && rank.getOrDefault(titleLevel, 0) > rank.getOrDefault(candidateLevel, 0)) {
            failures.add("Job title requires " + titleLevel
                    + "; configured candidate education is "
                    + (candidateLevel == null ? "not recorded" : candidateLevel) + ".");
            return failures;
        }

        String text = title + "\n" + description;
        for (String clause : text.split("[.!?;\n]")) {
            if (!REQUIRED_EDUCATION.matcher(clause).find()) continue;
            List<String> levels = new ArrayList<>();
            for (String level : List.of("doctorate", "master", "bachelor")) {
                if (degreeMentioned(level, clause)) levels.add(level);
            }
            if (levels.isEmpty()) continue;
            if (PREFERRED_EDUCATION.matcher(clause).find()
                    && !Pattern.compile("\\b(?:required|required qualification|must|minimum|basic)\\b",
                    Pattern.CASE_INSENSITIVE).matcher(clause).find()) continue;

            int candidateRank = rank.getOrDefault(candidateLevel, 0);
            boolean alternatives = Pattern.compile("\\b(?:or|either)\\b", Pattern.CASE_INSENSITIVE).matcher(clause).find();
            boolean satisfied = alternatives
                    ? levels.stream().anyMatch(level -> candidateRank >= rank.get(level))
                    : levels.stream().allMatch(level -> candidateRank >= rank.get(level));

            if (!satisfied) {
                String highest = levels.stream().max(Comparator.comparingInt(rank::get)).orElse("bachelor");
                failures.add("Mandatory education requirement exceeds configured candidate education: requires "
                        + highest + "; candidate has "
                        + (candidateLevel == null ? "no recorded degree" : candidateLevel) + ".");
                continue;
            }

            if (levels.contains("bachelor") && candidateRank >= rank.get("bachelor")
                    && !educationFieldCompatible(clause, education)) {
                failures.add("Mandatory bachelor's degree field is not compatible with the configured Computer Science/Engineering education.");
            }
        }
        return failures;
    }

    private String candidateEducationLevel(List<Map<String, Object>> education) {
        int best = 0;
        String result = null;
        Map<String, Integer> rank = Map.of("bachelor", 1, "master", 2, "doctorate", 3);
        for (Map<String, Object> item : education) {
            String level = degreeLevel(Objects.toString(item.get("degree"), ""));
            if (level != null && rank.get(level) > best) {
                best = rank.get(level);
                result = level;
            }
        }
        return result;
    }

    private boolean educationFieldCompatible(String clause, List<Map<String, Object>> education) {
        String lower = clause.toLowerCase(Locale.ROOT);
        if (List.of("related field", "related technical field", "equivalent degree",
                "equivalent qualification", "any stem").stream().anyMatch(lower::contains)) {
            return true;
        }

        Set<String> aliases = new LinkedHashSet<>();
        for (Map<String, Object> item : education) {
            String degree = Objects.toString(item.get("degree"), "").toLowerCase(Locale.ROOT);
            if (degree.contains("computer science")) aliases.addAll(List.of("computer science", "computing"));
            if (degree.contains("computer engineering")) aliases.add("computer engineering");
            if (degree.contains("software engineering")) aliases.add("software engineering");
            if (degree.contains("information technology")) aliases.add("information technology");
        }
        if (aliases.isEmpty()) return false;

        var fieldMatch = Pattern.compile("\\b(?:degree|bachelor(?:'s|s)?|master(?:'s|s)?)\\s+(?:in|of)\\s+([^.;:\\n]+)",
                Pattern.CASE_INSENSITIVE).matcher(lower);
        if (!fieldMatch.find()) return true;

        List<String> alternatives = Arrays.stream(FIELD_SPLIT.split(fieldMatch.group(1)))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        Set<String> generic = Set.of("engineering", "computer-related", "technical field", "stem field", "technology");
        return alternatives.stream().anyMatch(alt -> generic.contains(alt)
                || aliases.stream().anyMatch(alt::contains));
    }

    private static String degreeLevel(String text) {
        for (String level : List.of("doctorate", "master", "bachelor")) {
            if (degreeMentioned(level, text)) return level;
        }
        return null;
    }

    private static boolean degreeMentioned(String level, String text) {
        String[] patterns = DEGREE_PATTERNS.get(level);
        return Arrays.stream(patterns).anyMatch(pattern ->
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text == null ? "" : text).find());
    }

    private double weight(String name) {
        return weights.getOrDefault(name, defaultWeight(name));
    }

    private double threshold(String name, double fallback) {
        return thresholds.getOrDefault(name, fallback);
    }

    private static Map<String, Object> loadScoringConfig() {
        try {
            Path path = RuntimePaths.config().resolve("scoring.yaml");
            if (!Files.exists(path)) return Map.of();
            return new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                    .readValue(Files.readString(path),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load config/scoring.yaml", e);
        }
    }

    private static Map<String, Double> doubles(Map<String, Object> values) {
        Map<String, Double> out = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            try { out.put(key, Double.parseDouble(String.valueOf(value))); }
            catch (Exception ignored) { }
        });
        return out;
    }

    private static double defaultWeight(String name) {
        return Map.of("technical_stack", 25d, "role_seniority", 15d, "qualifications", 15d,
                "project_evidence", 15d, "location_work_mode", 10d, "compensation", 10d,
                "domain", 5d, "growth", 5d).getOrDefault(name, 0d);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) (Map<?, ?>) m : Map.of();
    }

    private static List<String> castList(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static boolean contains(String text, String term) {
        if (text == null || term == null || term.isBlank()) return false;
        String normalizedText = normalize(text);
        String normalizedTerm = normalize(term);
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(normalizedTerm) + "(?![a-z0-9])")
                .matcher(normalizedText).find();
    }

    private static String normalize(String value) {
        return value == null ? ""
                : value.toLowerCase(Locale.ROOT)
                .replace('–', '-').replace('—', '-')
                .replaceAll("[^a-z0-9+#.]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record HardFilterResult(boolean pass, List<String> reasons) {}

    public record RoleScore(double points, String matchedRole, String reason) {}

    public record CandidateProfile(
            List<String> coreSkills,
            List<String> preferredRoles,
            List<String> preferredLocations,
            List<String> preferredDomains,
            List<String> hardExclusions,
            List<String> hardSeniorityExclusions,
            double experienceYears,
            double minimumLpa,
            List<Map<String, Object>> education,
            double experienceCeilingYears
    ) {
        public CandidateProfile {
            coreSkills = List.copyOf(coreSkills);
            preferredRoles = List.copyOf(preferredRoles);
            preferredLocations = List.copyOf(preferredLocations);
            preferredDomains = List.copyOf(preferredDomains);
            hardExclusions = List.copyOf(hardExclusions);
            hardSeniorityExclusions = List.copyOf(hardSeniorityExclusions);
            education = education == null ? List.of() : List.copyOf(education);
        }

        public CandidateProfile(
                List<String> coreSkills, List<String> preferredRoles,
                List<String> preferredLocations, List<String> preferredDomains,
                List<String> hardExclusions, List<String> hardSeniorityExclusions,
                double experienceYears, double minimumLpa) {
            this(coreSkills, preferredRoles, preferredLocations, preferredDomains,
                    hardExclusions, hardSeniorityExclusions, experienceYears, minimumLpa,
                    List.of(), experienceYears);
        }
    }
}
