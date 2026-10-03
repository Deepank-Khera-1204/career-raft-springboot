package com.deepank.careerraft.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class OneShotRunner implements ApplicationRunner {
    private final DueSourceRunner runner;
    private final UrlTestService urlTestService;
    private final ConfigurableApplicationContext context;

    public OneShotRunner(
            DueSourceRunner runner,
            UrlTestService urlTestService,
            ConfigurableApplicationContext context) {
        this.runner = runner;
        this.urlTestService = urlTestService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String testUrl = System.getenv("CAREER_RAFT_TEST_URL");
        if (testUrl != null && !testUrl.isBlank()) {
            boolean useGemini = Boolean.parseBoolean(
                    System.getenv().getOrDefault("CAREER_RAFT_TEST_GEMINI", "false"));
            boolean searchLinkedIn = Boolean.parseBoolean(
                    System.getenv().getOrDefault("CAREER_RAFT_TEST_LINKEDIN", "false"));
            boolean sendEmail = Boolean.parseBoolean(
                    System.getenv().getOrDefault("CAREER_RAFT_TEST_EMAIL", "false"));
            runUrlTest(testUrl.trim(), useGemini, searchLinkedIn, sendEmail);
            return;
        }

        boolean enabled = Boolean.parseBoolean(
                System.getenv().getOrDefault("CAREER_RAFT_RUN_ON_START", "false"));
        if (!enabled) {
            return;
        }

        int exitCode = 1;
        try {
            DueSourceRunner.RunSummary result = runner.runDueSources();
            System.out.println("CAREER RAFT RUN: " + result.status()
                    + " run_id=" + result.runId()
                    + " fetched=" + result.metrics().jobsFetched()
                    + " new=" + result.metrics().jobsNew()
                    + " packages=" + result.metrics().packagesGenerated()
                    + " emails=" + result.metrics().emailsSent()
                    + " failed_jobs=" + result.metrics().jobsFailed());

            exitCode = "success".equalsIgnoreCase(result.status())
                    && result.metrics().jobsFailed() == 0 ? 0 : 1;
        } finally {
            context.close();
            System.exit(exitCode);
        }
    }

    private void runUrlTest(
            String url,
            boolean useGemini,
            boolean searchLinkedIn,
            boolean sendEmail) {
        try {
            UrlTestService.Result result =
                    urlTestService.test(url, useGemini, searchLinkedIn, sendEmail);
            System.out.println("CAREER RAFT URL TEST");
            System.out.println("url=" + result.url());
            System.out.println("title=" + result.job().title());
            System.out.println("company=" + result.job().company());
            System.out.println("score=" + result.score());
            System.out.println("hard_pass=" + result.hardPass());
            System.out.println("next_action=" + result.nextAction());
            System.out.println("gemini=" + useGemini);
            System.out.println("linkedin_referral_search=" + searchLinkedIn);
            System.out.println("email_requested=" + sendEmail);
            System.out.println("semantic_status=" + result.semanticStatus());
            System.out.println("semantic_provider=" + result.semanticProvider());
            System.out.println("semantic_alignment=" + result.semanticAlignment());
            System.out.println("referral_count=" + result.referralCount());
            System.out.println("email_sent=" + result.emailSent());
            if (!result.hardFailReasons().isEmpty()) {
                System.out.println("hard_fail_reasons=" + String.join(" | ", result.hardFailReasons()));
            }
            if (!result.matchedSkills().isEmpty()) {
                System.out.println("matched_skills=" + String.join(", ", result.matchedSkills()));
            }
            if (!result.missingSkills().isEmpty()) {
                System.out.println("missing_skills=" + String.join(", ", result.missingSkills()));
            }
            System.out.println("rationale=" + result.rationale());
            // A successful test is a successful fetch/parse/assessment, regardless
            // of whether the job itself passes the candidate's hard filter.
            System.exit(0);
        } catch (Exception e) {
            System.err.println("CAREER RAFT URL TEST FAILED: " + e.getMessage());
            System.exit(1);
        }
    }
}
