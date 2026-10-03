package com.deepank.careerraft.service;

import com.deepank.careerraft.discovery.HtmlJobParser;
import com.deepank.careerraft.discovery.SimpleHttpClient;
import com.deepank.careerraft.domain.Job;
import com.deepank.careerraft.domain.JobAssessment;
import com.deepank.careerraft.scoring.CandidateProfileFactory;
import com.deepank.careerraft.scoring.ScoringEngine;
import com.deepank.careerraft.documents.ApplicationPackageBuilder;
import com.deepank.careerraft.notifications.EmailDeliveryService;
import com.deepank.careerraft.referrals.PublicWebReferralResearcher;
import com.deepank.careerraft.referrals.ReferralModels;
import com.deepank.careerraft.intelligence.SemanticJobAnalyzer;
import com.deepank.careerraft.intelligence.SemanticScorer;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class UrlTestService {
    private final SimpleHttpClient http;
    private final HtmlJobParser parser;
    private final ScoringEngine scorer;
    private final CandidateProfileFactory profiles;
    private final SemanticJobAnalyzer semanticAnalyzer;
    private final SemanticScorer semanticScorer;
    private final PublicWebReferralResearcher referralResearcher;
    private final ApplicationPackageBuilder packageBuilder;
    private final EmailDeliveryService emailDeliveryService;

    public UrlTestService(
            SimpleHttpClient http,
            HtmlJobParser parser,
            ScoringEngine scorer,
            CandidateProfileFactory profiles,
            SemanticJobAnalyzer semanticAnalyzer,
            SemanticScorer semanticScorer,
            PublicWebReferralResearcher referralResearcher,
            ApplicationPackageBuilder packageBuilder,
            EmailDeliveryService emailDeliveryService) {
        this.http = http;
        this.parser = parser;
        this.scorer = scorer;
        this.profiles = profiles;
        this.semanticAnalyzer = semanticAnalyzer;
        this.semanticScorer = semanticScorer;
        this.referralResearcher = referralResearcher;
        this.packageBuilder = packageBuilder;
        this.emailDeliveryService = emailDeliveryService;
    }

    public Result test(String url, boolean useGemini, boolean searchLinkedIn, boolean sendEmail) {
        URI uri = URI.create(url);
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only http/https URLs are supported");
        }

        String html = http.getText(url, "text/html,application/xhtml+xml,*/*");
        Map<String, String> parsed = parser.extract(html);
        if (parsed == null || parsed.getOrDefault("description", "").isBlank()) {
            throw new IllegalArgumentException("Could not extract a job description from URL");
        }

        String title = parsed.getOrDefault("title", "").isBlank()
                ? "Unknown title"
                : parsed.get("title");
        String company = parsed.getOrDefault("company", "").isBlank()
                ? uri.getHost()
                : parsed.get("company");

        Job job = new Job(
                "url-test",
                "url_test",
                url,
                company,
                title,
                url,
                parsed.getOrDefault("description", ""),
                parsed.getOrDefault("location", ""),
                null,
                parsed.getOrDefault("employment_type", ""),
                parsed.getOrDefault("salary_text", ""),
                null,
                Instant.now()
        );

        JobAssessment assessment = scorer.assess(job, profiles.build());

        if (useGemini) {
            String apiKey = System.getenv("GEMINI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("GEMINI_API_KEY is required when Gemini semantic analysis is enabled");
            }
            var semantic = semanticAnalyzer.analyze(job.description(), job.title());
            assessment = semanticScorer.augment(
                    assessment, semantic.understanding(), semantic.usedProvider()).assessment();
        }

        List<ReferralModels.ReferralTarget> referrals = List.of();
        if (searchLinkedIn) {
            String apiKey = System.getenv("BRAVE_SEARCH_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("BRAVE_SEARCH_API_KEY is required when LinkedIn referral search is enabled");
            }
            referrals = referralResearcher.findTargets(job.company(), job.title(), 10);
        }

        boolean emailSent = false;
        if (sendEmail) {
            if (!assessment.hardPass()
                    || (assessment.nextAction() != JobAssessment.NextAction.GENERATE_PACKAGE
                    && assessment.nextAction() != JobAssessment.NextAction.GENERATE_PACKAGE_PRIORITY)) {
                throw new IllegalArgumentException(
                        "Email requested, but this URL did not qualify for application-package generation");
            }
            String recipient = System.getenv("CR_EMAIL_TO");
            if (recipient == null || recipient.isBlank()) {
                throw new IllegalArgumentException("CR_EMAIL_TO is required when email delivery is enabled");
            }
            try {
                var applicationPackage = packageBuilder.build(job, assessment);
                emailDeliveryService.sendPackage(
                        job, assessment, referrals, applicationPackage, recipient);
                emailSent = true;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to generate or send the application package for URL: " + url, e);
            }
        }

        return new Result(
                url,
                job,
                assessment.hardPass(),
                assessment.score().total(),
                assessment.nextAction().name(),
                assessment.hardFailReasons(),
                assessment.matchedSkills(),
                assessment.missingSkills(),
                String.join(" | ", assessment.rationale()),
                assessment.semanticStatus(),
                assessment.semanticProvider(),
                assessment.semanticAlignment(),
                referrals.size(),
                emailSent
        );
    }

    public record Result(
            String url,
            Job job,
            boolean hardPass,
            double score,
            String nextAction,
            java.util.List<String> hardFailReasons,
            java.util.List<String> matchedSkills,
            java.util.List<String> missingSkills,
            String rationale,
            String semanticStatus,
            String semanticProvider,
            double semanticAlignment,
            int referralCount,
            boolean emailSent) {}
}
