package com.deepank.careerraft.service;

import com.deepank.careerraft.discovery.HtmlJobParser;
import com.deepank.careerraft.discovery.SimpleHttpClient;
import com.deepank.careerraft.domain.Job;
import com.deepank.careerraft.domain.JobAssessment;
import com.deepank.careerraft.scoring.CandidateProfileFactory;
import com.deepank.careerraft.scoring.ScoringEngine;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Service
public class UrlTestService {
    private final SimpleHttpClient http;
    private final HtmlJobParser parser;
    private final ScoringEngine scorer;
    private final CandidateProfileFactory profiles;

    public UrlTestService(
            SimpleHttpClient http,
            HtmlJobParser parser,
            ScoringEngine scorer,
            CandidateProfileFactory profiles) {
        this.http = http;
        this.parser = parser;
        this.scorer = scorer;
        this.profiles = profiles;
    }

    public Result test(String url) {
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
        return new Result(
                url,
                job,
                assessment.hardPass(),
                assessment.score().total(),
                assessment.nextAction().name(),
                assessment.hardFailReasons(),
                assessment.matchedSkills(),
                assessment.missingSkills(),
                assessment.rationale()
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
            String rationale) {}
}
