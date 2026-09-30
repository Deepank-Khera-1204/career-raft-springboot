package com.deepank.careerraft.scoring;

import com.deepank.careerraft.career.CareerKnowledgeBase;
import com.deepank.careerraft.domain.Job;
import com.deepank.careerraft.intelligence.EvidenceCatalog;
import com.deepank.careerraft.intelligence.EvidenceMatcher;
import com.deepank.careerraft.intelligence.TechnicalConceptMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoringEngineTest {
    private static ScoringEngine engine;

    private final ScoringEngine.CandidateProfile candidate = new ScoringEngine.CandidateProfile(
            List.of("Java", "Spring Boot", "REST APIs", "Kubernetes", "Redis", "PostgreSQL"),
            List.of("Backend Engineer", "Software Engineer"),
            List.of("Bangalore", "Remote India"),
            List.of("Payments", "Distributed Systems"),
            List.of("Mobile-only", "Internship", "Manual QA", "Sales", "Unpaid"),
            List.of("manager", "director", "senior", "staff", "lead"),
            1.25, 15.0,
            List.of(java.util.Map.of("degree", "B.Tech. in Computer Science and Engineering")),
            2.0
    );

    @BeforeAll
    static void setup() {
        CareerKnowledgeBase kb = new CareerKnowledgeBase();
        EvidenceCatalog catalog = new EvidenceCatalog(kb);
        engine = new ScoringEngine(
                new TechnicalConceptMatcher(catalog),
                new EvidenceMatcher(catalog)
        );
    }

    private Job job(String title, String desc) {
        return job(title, desc, "FULL_TIME", null);
    }

    private Job job(String title, String desc, String type, String salary) {
        return new Job(
                title.replace(' ', '-'),
                "test",
                title,
                "Acme",
                title,
                "https://example.com/job",
                desc,
                "Bangalore",
                "REMOTE",
                type,
                salary,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void structuredInternshipIsRejected() {
        var a = engine.assess(job("Software Engineer", "Java Spring Boot backend role.", "INTERNSHIP", "18-24 LPA"), candidate);
        assertFalse(a.hardPass());
        assertTrue(a.hardFailReasons().stream().anyMatch(x -> x.toLowerCase().contains("internship")));
    }

    @Test
    void fullTimeMentioningInternshipInBodyIsNotRejected() {
        var a = engine.assess(job(
                "Software Engineer",
                "Full-time software engineering role. You will mentor interns. Java Spring Boot REST APIs."
        ), candidate);
        assertTrue(a.hardPass());
    }

    @Test
    void managerTitleIsRejected() {
        var a = engine.assess(job("Engineering Manager", "Java Spring Boot backend role."), candidate);
        assertFalse(a.hardPass());
    }

    @Test
    void experienceCeilingIsTheHardGate() {
        var allowed = engine.assess(job(
                "Backend Engineer",
                "Java Spring Boot backend role requiring 1.5+ years of experience."
        ), candidate);
        assertTrue(allowed.hardPass());
        assertFalse(allowed.hardFailReasons().stream().anyMatch(x -> x.contains("configured candidate experience")));

        var rejected = engine.assess(job(
                "Backend Engineer",
                "Java Spring Boot backend role requiring 3+ years of experience."
        ), candidate);
        assertFalse(rejected.hardPass());
        assertTrue(rejected.hardFailReasons().stream().anyMatch(x -> x.contains("experience ceiling")));
    }

    @Test
    void salaryBelowFloorGetsZeroCompensation() {
        var a = engine.assess(job("Backend Engineer", "Java Spring Boot PostgreSQL", "FULL_TIME", "10-12 LPA"), candidate);
        assertEquals(0.0, a.score().compensation());
    }

    @Test
    void unknownSalaryGetsNeutralPartialCredit() {
        var a = engine.assess(job("Backend Engineer", "Java Spring Boot PostgreSQL"), candidate);
        assertEquals(5.0, a.score().compensation());
    }

    @Test
    void frontendRoleIsAccepted() {
        var a = engine.assess(job(
                "Frontend Engineer",
                "Build React web applications with JavaScript, HTML, CSS and automated testing."
        ), candidate);
        assertTrue(a.hardPass());
    }

    @Test
    void fullStackRoleIsAccepted() {
        var a = engine.assess(job(
                "Full Stack Engineer",
                "Build React frontends and Java Spring Boot backend services."
        ), candidate);
        assertTrue(a.hardPass());
    }

    @Test
    void mandatoryMastersRejectsBachelorsCandidate() {
        var a = engine.assess(job(
                "Software Engineer",
                "Minimum qualifications: Master's degree in Computer Science required."
        ), candidate);
        assertFalse(a.hardPass());
        assertTrue(a.hardFailReasons().stream().anyMatch(x -> x.toLowerCase().contains("master")));
    }

    @Test
    void mandatoryBachelorsInComputerScienceAcceptsBtech() {
        var a = engine.assess(job(
                "Software Engineer",
                "Minimum qualifications: Bachelor's degree in Computer Science required."
        ), candidate);
        assertTrue(a.hardPass());
    }

    @Test
    void unrelatedMandatoryBachelorsFieldRejects() {
        var a = engine.assess(job(
                "Software Engineer",
                "Minimum qualifications: Bachelor's degree in Mechanical Engineering required."
        ), candidate);
        assertFalse(a.hardPass());
        assertTrue(a.hardFailReasons().stream().anyMatch(x -> x.toLowerCase().contains("field")));
    }

    @Test
    void softwareEngineerGetsBoundedTechnicalPriorWithoutInventingLanguage() {
        var a = engine.assess(job(
                "Software Engineer",
                "Develops software, writes code, tests, debugs production issues and supports scaling for reliability."
        ), candidate);
        assertTrue(a.hardPass());
        assertTrue(a.score().technicalStack() >= 5.0);
        assertTrue(a.score().technicalStack() < 25.0);
        assertTrue(a.matchedSkills().isEmpty());
        assertTrue(a.categoryExplanations().get("technical_stack").contains("role-family 5.00"));
    }

    @Test
    void unitTestingDoesNotBecomeJUnitDirectSkillMatch() {
        var a = engine.assess(job(
                "Software Engineer",
                "Develops software and performs unit testing."
        ), candidate);
        assertTrue(a.hardPass());
        assertFalse(a.matchedSkills().contains("JUnit"));
        assertTrue(a.technicalConceptMatches().stream()
                .anyMatch(x -> "unit_testing".equals(String.valueOf(x.get("concept_id")))));
    }

    @Test
    void leaderElectionMapsToVerifiedRaftEvidenceWithoutCreatingDirectSkill() {
        var a = engine.assess(job(
                "Software Engineer",
                "Build distributed fault-tolerant services with leader election and consensus."
        ), candidate);
        assertTrue(a.hardPass());
        assertTrue(a.matchedSkills().isEmpty());

        var leaderElection = a.technicalConceptMatches().stream()
                .filter(x -> "leader_election".equals(String.valueOf(x.get("concept_id"))))
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(leaderElection.get("evidence_ids")).contains("PROJ-RAFT-001"));

        var consensus = a.technicalConceptMatches().stream()
                .filter(x -> "consensus".equals(String.valueOf(x.get("concept_id"))))
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(consensus.get("evidence_ids")).contains("PROJ-RAFT-003"));
    }

    @Test
    void genericLanguageDoesNotInflateTechnicalEvidence() {
        var a = engine.assess(job(
                "Software Engineer",
                "Work with cross-functional teams, stakeholders and product managers."
        ), candidate);
        assertEquals(5.0, a.score().technicalStack());
        assertTrue(a.technicalConceptMatches().isEmpty());
    }

    @Test
    void salesBodyMentionDoesNotRejectEngineeringRole() {
        var a = engine.assess(job(
                "Software Engineer",
                "Build Java backend services. Collaborate with Sales and Product teams."
        ), candidate);
        assertTrue(a.hardPass());
    }

    @Test
    void salesTitleIsRejected() {
        var a = engine.assess(job("Sales Engineer", "Build Java backend services."), candidate);
        assertFalse(a.hardPass());
    }

    @Test
    void unpaidTitleIsRejected() {
        var a = engine.assess(job("Unpaid Software Intern", "Java Spring Boot backend role."), candidate);
        assertFalse(a.hardPass());
    }

    @Test
    void relevantJobGetsTransparentBreakdownAndPackageAction() {
        var a = engine.assess(job(
                "Backend Engineer",
                "Java Spring Boot Redis PostgreSQL Payments distributed systems",
                "FULL_TIME",
                "18-24 LPA"
        ), candidate);
        assertTrue(a.hardPass());
        assertTrue(a.score().total() > 70.0);
        assertFalse(a.categoryExplanations().isEmpty());
        assertNotNull(a.categoryExplanations().get("technical_stack"));
        assertFalse(a.selectedProjects().isEmpty());
        assertTrue(a.nextAction() == com.deepank.careerraft.domain.JobAssessment.NextAction.REVIEW
                || a.nextAction() == com.deepank.careerraft.domain.JobAssessment.NextAction.GENERATE_PACKAGE
                || a.nextAction() == com.deepank.careerraft.domain.JobAssessment.NextAction.GENERATE_PACKAGE_PRIORITY);
    }
}
