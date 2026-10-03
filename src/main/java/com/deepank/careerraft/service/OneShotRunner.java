package com.deepank.careerraft.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class OneShotRunner implements ApplicationRunner {
    private final DueSourceRunner runner;
    private final ConfigurableApplicationContext context;

    public OneShotRunner(DueSourceRunner runner, ConfigurableApplicationContext context) {
        this.runner = runner;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
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
}
