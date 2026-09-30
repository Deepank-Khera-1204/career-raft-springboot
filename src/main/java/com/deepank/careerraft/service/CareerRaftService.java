package com.deepank.careerraft.service;

import com.deepank.careerraft.discovery.*;
import com.deepank.careerraft.domain.*;
import com.deepank.careerraft.documents.ApplicationPackageBuilder;
import com.deepank.careerraft.notifications.EmailDeliveryService;
import com.deepank.careerraft.referrals.ReferralModels;
import com.deepank.careerraft.referrals.ReferralService;
import com.deepank.careerraft.intelligence.*;
import com.deepank.careerraft.repository.JobRepository;
import com.deepank.careerraft.scoring.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CareerRaftService {
    private final DiscoveryService discovery;
    private final ScoringEngine scorer;
    private final CandidateProfileFactory profiles;
    private final JobRepository jobs;
    private final AISettings aiSettings;
    private final SemanticJobAnalyzer semanticAnalyzer;
    private final SemanticScorer semanticScorer;
    private final ApplicationPackageBuilder packageBuilder;
    private final ReferralService referralService;
    private final EmailDeliveryService emailDeliveryService;
    private final AtomicInteger semanticCalls = new AtomicInteger();

    public CareerRaftService(
            DiscoveryService discovery,
            ScoringEngine scorer,
            CandidateProfileFactory profiles,
            JobRepository jobs,
            AISettings aiSettings,
            SemanticJobAnalyzer semanticAnalyzer,
            SemanticScorer semanticScorer,
            ApplicationPackageBuilder packageBuilder,
            ReferralService referralService,
            EmailDeliveryService emailDeliveryService) {
        this.discovery = discovery;
        this.scorer = scorer;
        this.profiles = profiles;
        this.jobs = jobs;
        this.aiSettings = aiSettings;
        this.semanticAnalyzer = semanticAnalyzer;
        this.semanticScorer = semanticScorer;
        this.packageBuilder = packageBuilder;
        this.referralService = referralService;
        this.emailDeliveryService = emailDeliveryService;
    }

    public DiscoveryResult discover() { return discovery.discover(); }

    public AssessmentResult assess(Job job) {
        boolean inserted = jobs.insertJob(job);
        return assessInsertedJob(job, inserted);
    }

    public AssessmentResult assessInsertedJob(Job job, boolean inserted) {
        JobAssessment assessment = assessJob(job);
        jobs.setScore(job.id(), assessment.combinedScore(),
                assessment.hardPass() ? "SCORED" : "REJECTED");

        ApplicationPackageBuilder.ApplicationPackage applicationPackage = null;
        List<ReferralModels.ReferralTarget> referrals = List.of();
        boolean emailSent = false;
        boolean duplicateEmail = false;
        if (assessment.hardPass()
                && (assessment.nextAction() == JobAssessment.NextAction.GENERATE_PACKAGE
                || assessment.nextAction() == JobAssessment.NextAction.GENERATE_PACKAGE_PRIORITY)) {
            try {
                applicationPackage = packageBuilder.build(job, assessment);
                jobs.setScore(job.id(), assessment.combinedScore(), "PACKAGE_READY");
                if (Boolean.parseBoolean(System.getenv().getOrDefault("CR_ENABLE_LINKEDIN", "true"))) { try { referrals = referralService.research(job.company(), job.title(), 10); } catch (Exception ignored) { } }
                String recipient = System.getenv("CR_EMAIL_TO");
                boolean sendEmail = Boolean.parseBoolean(System.getenv().getOrDefault("CR_SEND_EMAIL", "true"));
                if (sendEmail && recipient != null && !recipient.isBlank() && !jobs.notificationSent(job.id(), "smtp", recipient)) {
                    emailDeliveryService.sendPackage(job, assessment, referrals, applicationPackage, recipient);
                    jobs.markNotificationSent(job.id(), "smtp", recipient,
                            "Aizen-sama — " + job.company() + " · " + job.title(), java.time.Instant.now());
                    emailSent = true;
                } else if (sendEmail && recipient != null && !recipient.isBlank()) {
                    duplicateEmail = true;
                }
            } catch (Exception ignored) {
                // Assessment remains valid when downstream packaging/notification tooling is unavailable.
            }
        }

        return new AssessmentResult(job, assessment, inserted, applicationPackage, referrals, emailSent, duplicateEmail);
    }

    public BatchAssessmentResult discoverAndAssess() {
        semanticCalls.set(0);
        DiscoveryResult discovered = discovery.discover();
        List<AssessmentResult> results = new ArrayList<>();
        for (Job job : discovered.jobs()) {
            try {
                results.add(assess(job));
            } catch (Exception e) {
                results.add(new AssessmentResult(job, null, false, null, List.of(), false, false));
            }
        }
        return new BatchAssessmentResult(discovered, results);
    }

    private JobAssessment assessJob(Job job) {
        JobAssessment assessment = scorer.assess(job, profiles.build());

        if (!aiSettings.enabled
                || !assessment.hardPass()
                || assessment.score().total() < aiSettings.minDeterministicScoreForLlm
                || semanticCalls.get() >= aiSettings.maxCallsPerRun) {
            return assessment;
        }

        if (semanticCalls.incrementAndGet() > aiSettings.maxCallsPerRun) {
            semanticCalls.decrementAndGet();
            return assessment;
        }

        try {
            var result = semanticAnalyzer.analyze(job.description(), job.title());
            return semanticScorer.augment(
                    assessment, result.understanding(), result.usedProvider()).assessment();
        } catch (Exception e) {
            return withSemanticFailure(assessment, e.getMessage());
        }
    }

    private JobAssessment withSemanticFailure(JobAssessment a, String error) {
        return new JobAssessment(
                a.jobId(), a.hardPass(), a.hardFailReasons(), a.score(), a.matchedSkills(),
                a.missingSkills(), a.selectedProjects(), a.rationale(), a.categoryExplanations(),
                a.matchedDomains(), a.matchedRole(), a.requiredExperienceYears(),
                a.parsedMinLpa(), a.parsedMaxLpa(), a.nextAction(), a.scoringVersion(),
                "failed", null, 0, 0, List.of(), List.of(), error,
                a.technicalConceptMatches(), a.semanticVersion()
        );
    }

    public record AssessmentResult(
            Job job,
            JobAssessment assessment,
            boolean inserted,
            ApplicationPackageBuilder.ApplicationPackage applicationPackage,
            List<ReferralModels.ReferralTarget> referrals,
            boolean emailSent,
            boolean duplicateEmail) {
        public AssessmentResult {
            referrals = referrals == null ? List.of() : List.copyOf(referrals);
        }
    }

    public record BatchAssessmentResult(
            DiscoveryResult discovery,
            List<AssessmentResult> assessments) {}
}
