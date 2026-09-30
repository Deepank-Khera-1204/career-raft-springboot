package com.deepank.careerraft.domain;

import java.util.List;
import java.util.Map;

public record JobAssessment(
        String jobId,
        boolean hardPass,
        List<String> hardFailReasons,
        ScoreBreakdown score,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> selectedProjects,
        List<String> rationale,
        Map<String, String> categoryExplanations,
        List<String> matchedDomains,
        String matchedRole,
        Double requiredExperienceYears,
        Double parsedMinLpa,
        Double parsedMaxLpa,
        NextAction nextAction,
        String scoringVersion,
        String semanticStatus,
        String semanticProvider,
        double semanticAlignment,
        double semanticBonus,
        List<String> semanticEvidenceIds,
        List<String> semanticRationale,
        String semanticError,
        List<Map<String,Object>> technicalConceptMatches,
        String semanticVersion
) {
    public enum NextAction { IGNORE, REVIEW, GENERATE_PACKAGE, GENERATE_PACKAGE_PRIORITY }

    public JobAssessment {
        hardFailReasons = hardFailReasons == null ? List.of() : List.copyOf(hardFailReasons);
        matchedSkills = matchedSkills == null ? List.of() : List.copyOf(matchedSkills);
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
        selectedProjects = selectedProjects == null ? List.of() : List.copyOf(selectedProjects);
        rationale = rationale == null ? List.of() : List.copyOf(rationale);
        categoryExplanations = categoryExplanations == null ? Map.of() : Map.copyOf(categoryExplanations);
        matchedDomains = matchedDomains == null ? List.of() : List.copyOf(matchedDomains);
        semanticEvidenceIds = semanticEvidenceIds == null ? List.of() : List.copyOf(semanticEvidenceIds);
        semanticRationale = semanticRationale == null ? List.of() : List.copyOf(semanticRationale);
        technicalConceptMatches = technicalConceptMatches == null ? List.of() : List.copyOf(technicalConceptMatches);
    }

    public double combinedScore() {
        return Math.round(Math.min(100.0, score.total() + semanticBonus) * 100.0) / 100.0;
    }
}
