package com.deepank.careerraft.service;

import com.deepank.careerraft.discovery.*;
import com.deepank.careerraft.domain.Job;
import com.deepank.careerraft.domain.JobAssessment;
import com.deepank.careerraft.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class DueSourceRunner {
    private final SourceConfigLoader config;
    private final List<JobSource> adapters;
    private final JobRepository repository;
    private final CareerRaftService service;
    private final Random random = new Random();

    public DueSourceRunner(SourceConfigLoader config, List<JobSource> adapters,
                           JobRepository repository, CareerRaftService service) {
        this.config = config;
        this.adapters = List.copyOf(adapters);
        this.repository = repository;
        this.service = service;
    }

    public RunSummary runDueSources() {
        return run(false);
    }

    public RunSummary runAllSources() {
        return run(true);
    }

    private RunSummary run(boolean forceAll) {
        Instant now = Instant.now();
        String runId = "run-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        repository.startRunnerRun(runId, now);

        int checked = 0, due = 0, succeeded = 0, sourceFailures = 0;
        int fetched = 0, newlyInserted = 0, duplicates = 0, rejected = 0, failed = 0, packages = 0, referrals = 0, emails = 0, duplicateEmails = 0;
        List<String> errors = new ArrayList<>();
        List<CareerRaftService.AssessmentResult> results = new ArrayList<>();

        Map<String, JobSource> byProvider = adapters.stream()
                .collect(java.util.stream.Collectors.toMap(JobSource::provider, x -> x, (a, b) -> a));

        for (SourceDefinition definition : config.load()) {
            if (!definition.enabled()) continue;
            checked++;
            if (!forceAll && !repository.sourceIsDue(definition.name(), now)) continue;
            due++;
            if (!repository.requestBudgetAvailable(definition.name(), definition.maxRequestsPerDay(), now)) continue;

            JobSource adapter = byProvider.get(definition.provider());
            if (adapter == null) {
                sourceFailures++;
                errors.add(definition.name() + ": unsupported provider " + definition.provider());
                continue;
            }

            repository.markSourceAttempt(definition.name(), now);
            long started = System.nanoTime();

            try {
                List<Job> sourceJobs = new ArrayList<>(adapter.fetch(definition));
                sourceJobs.sort(Comparator.comparing(Job::postedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
                fetched += sourceJobs.size();

                for (Job job : sourceJobs) {
                    try {
                        if (!repository.insertJob(job)) {
                            duplicates++;
                            continue;
                        }
                        newlyInserted++;

                        if (job.description() == null || job.description().trim().length() < 100) {
                            repository.setScore(job.id(), 0, "REJECTED");
                            rejected++;
                            continue;
                        }

                        CareerRaftService.AssessmentResult result = service.assessInsertedJob(job, true);
                        results.add(result);
                        referrals += result.referrals().size();
                        emails += result.emailSent() ? 1 : 0;
                        duplicateEmails += result.duplicateEmail() ? 1 : 0;
                        if (result.assessment() != null
                                && result.applicationPackage() != null
                                && (result.assessment().nextAction() == JobAssessment.NextAction.GENERATE_PACKAGE
                                || result.assessment().nextAction() == JobAssessment.NextAction.GENERATE_PACKAGE_PRIORITY)) {
                            packages++;
                        }
                        if (result.assessment() != null
                                && result.assessment().nextAction() == JobAssessment.NextAction.IGNORE) {
                            rejected++;
                        }
                    } catch (Exception e) {
                        failed++;
                        repository.setScore(job.id(), 0, "FAILED");
                        errors.add(definition.name() + " / " + job.id() + ": " + e.getMessage());
                    }
                }

                repository.markSourceSuccess(definition.name(), now, nextRunAt(definition, now, 0));
                repository.recordSourceHealth(
                        definition.name(), now, "ok", sourceJobs.size(),
                        (System.nanoTime() - started) / 1_000_000, null
                );
                succeeded++;
            } catch (Exception e) {
                sourceFailures++;
                errors.add(definition.name() + ": " + e.getMessage());
                int previousFailures = repository.getSourceState(definition.name())
                        .map(JobRepository.SourceState::consecutiveFailures).orElse(0);
                double lower = Math.min(
                        definition.maxHours(),
                        definition.minHours() * Math.pow(2, Math.min(previousFailures, 3))
                );
                repository.markSourceFailure(definition.name(), now, nextRunAt(definition, now, lower));
                repository.recordSourceHealth(
                        definition.name(), now, "error", 0,
                        (System.nanoTime() - started) / 1_000_000, e.getMessage()
                );
            }
        }

        String status = sourceFailures > 0 && succeeded == 0
                ? "failed" : sourceFailures > 0 ? "partial" : "success";

        JobRepository.RunnerMetrics metrics = new JobRepository.RunnerMetrics(
                checked, due, succeeded, sourceFailures, fetched, newlyInserted,
                duplicates, rejected, failed, packages, referrals, emails, duplicateEmails
        );
        repository.finishRunnerRun(runId, Instant.now(), status, metrics, errors);
        return new RunSummary(runId, status, metrics, results, errors);
    }

    private Instant nextRunAt(SourceDefinition definition, Instant now, double overrideBaseHours) {
        double min = overrideBaseHours > 0 ? overrideBaseHours : definition.minHours();
        double max = Math.max(min, definition.maxHours());
        List<Double> allowed = definition.allowedIntervalsHours().stream()
                .filter(v -> v >= min && v <= max).toList();
        double base = allowed.isEmpty()
                ? min + random.nextDouble() * Math.max(0, max - min)
                : allowed.get(random.nextInt(allowed.size()));
        double jitter = definition.jitterMinutes() <= 0
                ? 0
                : (random.nextDouble() * 2 - 1) * definition.jitterMinutes() / 60.0;
        double hours = Math.max(min, Math.min(max, base + jitter));
        return now.plusMillis(Math.round(hours * 3_600_000));
    }

    public record RunSummary(String runId, String status,
                             JobRepository.RunnerMetrics metrics,
                             List<CareerRaftService.AssessmentResult> results,
                             List<String> errors) {}
}
