package com.deepank.careerraft.intelligence;

import com.deepank.careerraft.career.CareerKnowledgeBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceMatcherTest {
    private static EvidenceMatcher matcher;

    @BeforeAll
    static void setup() {
        CareerKnowledgeBase kb = new CareerKnowledgeBase();
        matcher = new EvidenceMatcher(new EvidenceCatalog(kb));
    }

    @Test
    void ciDoesNotMatchInsideWords() {
        var coverage=matcher.technologyCoverage(
                "This is a specific software engineering role.",
                java.util.List.of("continuous integration"));
        assertTrue(coverage.missing().contains("continuous integration"));
        assertFalse(coverage.matched().contains("continuous integration"));
    }

    @Test
    void ciCdMatchesAsItsOwnSignal() {
        var coverage=matcher.technologyCoverage(
                "CI/CD pipeline ownership.",
                java.util.List.of("continuous integration"));
        assertTrue(coverage.matched().contains("continuous integration"));
    }
}
