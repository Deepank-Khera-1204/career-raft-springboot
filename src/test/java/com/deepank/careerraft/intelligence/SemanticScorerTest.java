package com.deepank.careerraft.intelligence;

import com.deepank.careerraft.career.CareerKnowledgeBase;
import com.deepank.careerraft.domain.JobAssessment;
import com.deepank.careerraft.domain.ScoreBreakdown;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticScorerTest {
    private static SemanticScorer scorer;

    @BeforeAll
    static void setup() {
        CareerKnowledgeBase kb = new CareerKnowledgeBase();
        scorer = new SemanticScorer(new EvidenceCatalog(kb));
    }

    @Test
    void semanticBonusIsBounded() {
        JobAssessment base = new JobAssessment(
                "job-1", true, List.of(), new ScoreBreakdown(60,0,0,0,0,0,0,0),
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of(),
                List.of(), null, null, null, null,
                JobAssessment.NextAction.REVIEW, "0.8", "not_run", null,
                0, 0, List.of(), List.of(), null, List.of(), null
        );
        var understanding = new SemanticModels.JobUnderstanding(
                "backend", "early-career",
                new SemanticModels.ExperienceRequirement(null, null, "not_stated"),
                List.of(),
                List.of(new SemanticModels.Requirement("Java backend", "high",
                        List.of("Java"), List.of("EXP-NW-001"))),
                List.of(), List.of(),
                List.of(new SemanticModels.SemanticEvidenceMatch("EXP-NW-001", 1.0, "verified"))
        );
        var result = scorer.augment(base, understanding, "FakeProvider");
        assertTrue(result.bonus() > 0);
        assertTrue(result.bonus() <= 10);
        assertTrue(result.assessment().semanticBonus() <= 10);
    }

    @Test
    void semanticExperienceRequirementCannotOverrideCeiling() {
        JobAssessment base = new JobAssessment(
                "job-2", true, List.of(), new ScoreBreakdown(80,0,0,0,0,0,0,0),
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of(),
                List.of(), null, null, null, null,
                JobAssessment.NextAction.GENERATE_PACKAGE, "0.8", "not_run", null,
                0, 0, List.of(), List.of(), null, List.of(), null
        );
        var understanding = new SemanticModels.JobUnderstanding(
                "backend", "early-career",
                new SemanticModels.ExperienceRequirement(3.0, 5.0, "required"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        var result = scorer.augment(base, understanding, "FakeProvider");
        assertFalse(result.assessment().hardPass());
        assertEquals(JobAssessment.NextAction.IGNORE, result.assessment().nextAction());
        assertEquals(0, result.bonus());
        assertTrue(result.assessment().hardFailReasons().stream().anyMatch(x -> x.contains("3.00")));
    }

    @Test
    void midSeniorDoesNotMatchExactSeniorExclusion() {
        JobAssessment base = new JobAssessment(
                "job-3", true, List.of(), new ScoreBreakdown(84,0,0,0,0,0,0,0),
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of(),
                List.of(), null, null, null, null,
                JobAssessment.NextAction.GENERATE_PACKAGE, "0.8", "not_run", null,
                0, 0, List.of(), List.of(), null, List.of(), null
        );
        var understanding = new SemanticModels.JobUnderstanding(
                "backend", "Mid-Senior",
                new SemanticModels.ExperienceRequirement(null, null, "not_stated"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        var result = scorer.augment(base, understanding, "FakeProvider");
        assertTrue(result.assessment().hardPass());
        assertTrue(result.assessment().hardFailReasons().stream().noneMatch(x -> x.contains("excluded seniority")));
    }

    @Test
    void hardFilteredJobGetsNoSemanticBonus() {
        JobAssessment base = new JobAssessment(
                "job-4", false, List.of("manager"), new ScoreBreakdown(90,0,0,0,0,0,0,0),
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of(),
                List.of(), null, null, null, null,
                JobAssessment.NextAction.IGNORE, "0.8", "not_run", null,
                0, 0, List.of(), List.of(), null, List.of(), null
        );
        var understanding = new SemanticModels.JobUnderstanding(
                "backend", "early-career",
                new SemanticModels.ExperienceRequirement(null, null, "not_stated"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        var result = scorer.augment(base, understanding, "FakeProvider");
        assertEquals(0, result.bonus());
        assertEquals("skipped", result.assessment().semanticStatus());
    }
}
