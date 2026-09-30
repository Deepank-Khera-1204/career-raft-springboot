package com.deepank.careerraft.controller;

import com.deepank.careerraft.application.ApprovalCommand;
import com.deepank.careerraft.application.ApprovalParser;
import com.deepank.careerraft.application.ApprovalService;
import com.deepank.careerraft.discovery.DiscoveryResult;
import com.deepank.careerraft.career.RepositoryMiningService;
import com.deepank.careerraft.domain.Job;
import com.deepank.careerraft.repository.JobRepository;
import com.deepank.careerraft.service.CareerRaftService;
import com.deepank.careerraft.service.DueSourceRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/career-raft")
public class CareerRaftController {
    private final CareerRaftService service;
    private final DueSourceRunner runner;
    private final ApprovalService approvals;
    private final JobRepository repository;
    private final com.deepank.careerraft.intelligence.SemanticJobAnalyzer semanticAnalyzer;
    private final RepositoryMiningService repositoryMiningService;
    private final com.deepank.careerraft.discovery.SourceConfigLoader sourceConfigLoader;
    private final java.util.List<com.deepank.careerraft.discovery.JobSource> jobSources;

    public CareerRaftController(CareerRaftService service, DueSourceRunner runner,
                                ApprovalService approvals, JobRepository repository,
                                com.deepank.careerraft.intelligence.SemanticJobAnalyzer semanticAnalyzer,
                                RepositoryMiningService repositoryMiningService,
                                com.deepank.careerraft.discovery.SourceConfigLoader sourceConfigLoader,
                                java.util.List<com.deepank.careerraft.discovery.JobSource> jobSources) {
        this.service = service;
        this.runner = runner;
        this.approvals = approvals;
        this.repository = repository;
        this.semanticAnalyzer = semanticAnalyzer;
        this.repositoryMiningService = repositoryMiningService;
        this.sourceConfigLoader = sourceConfigLoader;
        this.jobSources = java.util.List.copyOf(jobSources);
    }

    @PostMapping("/discover")
    public ResponseEntity<DiscoveryResult> discover() {
        return ResponseEntity.ok(service.discover());
    }

    @PostMapping("/run")
    public ResponseEntity<DueSourceRunner.RunSummary> runAllSources() {
        return ResponseEntity.ok(runner.runAllSources());
    }

    @PostMapping("/run-due")
    public ResponseEntity<DueSourceRunner.RunSummary> runDueSources() {
        return ResponseEntity.ok(runner.runDueSources());
    }

    @PostMapping("/assess")
    public ResponseEntity<CareerRaftService.AssessmentResult> assess(@RequestBody Job job) {
        return ResponseEntity.ok(service.assess(job));
    }

    @PostMapping("/jobs/{jobId}/approval")
    public ResponseEntity<ApprovalCommand> createApproval(@PathVariable String jobId) {
        return ResponseEntity.ok(approvals.request(jobId));
    }

    @PostMapping("/approval/parse")
    public ResponseEntity<?> parseApproval(@RequestBody String text) {
        ApprovalCommand parsed = ApprovalParser.parse(text);
        return parsed == null
                ? ResponseEntity.badRequest().body(Map.of("valid", false))
                : ResponseEntity.ok(parsed);
    }

    @GetMapping("/jobs/{jobId}/approval")
    public ResponseEntity<?> getApproval(@PathVariable String jobId) {
        return approvals.get(jobId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Job job) {
        try {
            return ResponseEntity.ok(semanticAnalyzer.analyze(job.description(), job.title()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mine-repository")
    public ResponseEntity<?> mineRepository(@RequestBody Map<String,String> request) {
        String repositoryName = request.get("repository");
        String provider = request.getOrDefault("provider", "github");
        if (repositoryName == null || repositoryName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repository is required"));
        }
        String tokenEnv = "gitlab".equalsIgnoreCase(provider) ? "GITLAB_TOKEN" : "GITHUB_TOKEN";
        String token = System.getenv(tokenEnv);
        try {
            return ResponseEntity.ok(repositoryMiningService.mine(repositoryName, provider, token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/check-sources")
    public ResponseEntity<?> checkSources() {
        List<Map<String,Object>> results = new ArrayList<>();
        for (var definition : sourceConfigLoader.load()) {
            if (!definition.enabled()) continue;
            var source = jobSources.stream()
                    .filter(x -> x.provider().equals(definition.provider()))
                    .findFirst().orElse(null);
            Instant started = Instant.now();
            if (source == null) {
                results.add(Map.of("source", definition.name(), "status", "error",
                        "error", "unsupported provider", "jobs", 0));
                continue;
            }
            try {
                int count = source.fetch(definition).size();
                repository.recordSourceHealth(definition.name(), started, "ok", count,
                        java.time.Duration.between(started, Instant.now()).toMillis(), null);
                results.add(Map.of("source", definition.name(), "status", "ok", "jobs", count));
            } catch (Exception e) {
                repository.recordSourceHealth(definition.name(), started, "error", 0,
                        java.time.Duration.between(started, Instant.now()).toMillis(), e.getMessage());
                results.add(Map.of("source", definition.name(), "status", "error",
                        "error", e.getMessage(), "jobs", 0));
            }
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/runs")
    public ResponseEntity<?> runs(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(repository.latestRunnerRuns(limit));
    }

    @GetMapping("/sources/{source}/health")
    public ResponseEntity<?> sourceHealth(@PathVariable String source) {
        return repository.getSourceHealth(source)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("service", "career-raft", "status", "UP"));
    }
}
