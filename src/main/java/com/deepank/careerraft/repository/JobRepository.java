package com.deepank.careerraft.repository;

import com.deepank.careerraft.domain.Job;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JobRepository {
    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertJob(Job job) {
        int n = jdbc.update("""
                INSERT OR IGNORE INTO jobs
                (id,source,external_id,company,title,url,description,location,work_mode,
                 employment_type,salary_text,posted_at,discovered_at,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                job.id(), job.source(), job.externalId(), job.company(), job.title(), job.url(),
                job.description(), job.location(), job.workMode(), job.employmentType(),
                job.salaryText(), ts(job.postedAt()), ts(job.discoveredAt()), "DISCOVERED");
        return n == 1;
    }

    public void setScore(String jobId, double score, String status) {
        jdbc.update("UPDATE jobs SET score=?,status=? WHERE id=?", score, status, jobId);
    }

    public boolean sourceIsDue(String source, Instant when) {
        Optional<SourceState> state = getSourceState(source);
        return state.isEmpty() || state.get().nextRunAt() == null
                || !state.get().nextRunAt().isAfter(when);
    }

    public boolean requestBudgetAvailable(String source, int maxRequestsPerDay, Instant when) {
        if (maxRequestsPerDay <= 0) return false;
        String day = day(when);
        Optional<SourceState> state = getSourceState(source);
        return state.isEmpty() || !day.equals(Objects.toString(state.get().requestDay(), ""))
                || state.get().requestsToday() < maxRequestsPerDay;
    }

    public void markSourceAttempt(String source, Instant when) {
        String today = day(when);
        SourceState current = getSourceState(source).orElse(null);
        int count = current == null || !today.equals(current.requestDay())
                ? 1 : current.requestsToday() + 1;

        jdbc.update("""
                INSERT INTO source_state(source,last_attempted_at,requests_today,request_day)
                VALUES (?,?,?,?)
                ON CONFLICT(source) DO UPDATE SET
                    last_attempted_at=excluded.last_attempted_at,
                    requests_today=excluded.requests_today,
                    request_day=excluded.request_day
                """, source, ts(when), count, today);
    }

    public Optional<SourceState> getSourceState(String source) {
        return jdbc.query("""
                SELECT source,last_attempted_at,last_successful_at,next_run_at,
                       consecutive_failures,requests_today,request_day
                FROM source_state WHERE source=?
                """, (rs, n) -> new SourceState(
                rs.getString("source"),
                instant(rs.getTimestamp("last_attempted_at")),
                instant(rs.getTimestamp("last_successful_at")),
                instant(rs.getTimestamp("next_run_at")),
                rs.getInt("consecutive_failures"),
                rs.getInt("requests_today"),
                rs.getString("request_day")
        ), source).stream().findFirst();
    }

    public void markSourceSuccess(String source, Instant attemptedAt, Instant nextRunAt) {
        jdbc.update("""
                INSERT INTO source_state
                (source,last_attempted_at,last_successful_at,next_run_at,consecutive_failures)
                VALUES (?,?,?,?,0)
                ON CONFLICT(source) DO UPDATE SET
                    last_attempted_at=excluded.last_attempted_at,
                    last_successful_at=excluded.last_successful_at,
                    next_run_at=excluded.next_run_at,
                    consecutive_failures=0
                """, source, ts(attemptedAt), ts(attemptedAt), ts(nextRunAt));
    }

    public void markSourceFailure(String source, Instant attemptedAt, Instant nextRunAt) {
        jdbc.update("""
                INSERT INTO source_state
                (source,last_attempted_at,next_run_at,consecutive_failures)
                VALUES (?,?,?,1)
                ON CONFLICT(source) DO UPDATE SET
                    last_attempted_at=excluded.last_attempted_at,
                    next_run_at=excluded.next_run_at,
                    consecutive_failures=source_state.consecutive_failures+1
                """, source, ts(attemptedAt), ts(nextRunAt));
    }

    public void recordSourceHealth(String source, Instant checkedAt, String status,
                                   int jobsFound, long durationMs, String error) {
        jdbc.update("""
                INSERT INTO source_health
                (source,checked_at,status,jobs_found,duration_ms,error)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT(source) DO UPDATE SET
                    checked_at=excluded.checked_at,
                    status=excluded.status,
                    jobs_found=excluded.jobs_found,
                    duration_ms=excluded.duration_ms,
                    error=excluded.error
                """, source, ts(checkedAt), status, jobsFound, durationMs, error);
    }

    public Optional<SourceHealth> getSourceHealth(String source) {
        return jdbc.query("""
                SELECT source,checked_at,status,jobs_found,duration_ms,error
                FROM source_health WHERE source=?
                """, (rs, n) -> new SourceHealth(
                rs.getString("source"), instant(rs.getTimestamp("checked_at")),
                rs.getString("status"), rs.getInt("jobs_found"),
                rs.getLong("duration_ms"), rs.getString("error")
        ), source).stream().findFirst();
    }

    public boolean reserveReferralRequests(Instant when, int count, int dailyLimit) {
        if (count <= 0 || dailyLimit <= 0) return false;
        String today = day(when);
        List<Integer> rows = jdbc.query(
                "SELECT reserved_requests FROM referral_usage WHERE usage_day=?",
                (rs, n) -> rs.getInt(1), today);
        int used = rows.isEmpty() ? 0 : rows.getFirst();
        if (used + count > dailyLimit) return false;

        jdbc.update("""
                INSERT INTO referral_usage(usage_day,reserved_requests) VALUES (?,?)
                ON CONFLICT(usage_day) DO UPDATE SET
                    reserved_requests=referral_usage.reserved_requests+excluded.reserved_requests
                """, today, count);
        return true;
    }

    public boolean notificationSent(String jobId, String provider, String recipient) {
        List<Integer> rows = jdbc.query(
                "SELECT 1 FROM notification_deliveries WHERE job_id=? AND provider=? AND recipient=?",
                (rs, n) -> 1, jobId, provider, recipient);
        return !rows.isEmpty();
    }

    public void markNotificationSent(String jobId, String provider, String recipient,
                                      String subject, Instant when) {
        jdbc.update("""
                INSERT OR IGNORE INTO notification_deliveries
                (job_id,provider,recipient,subject,sent_at)
                VALUES (?,?,?,?,?)
                """, jobId, provider, recipient, subject, ts(when));
    }

    public void createApproval(String jobId, String token) {
        jdbc.update("""
                INSERT INTO approvals(job_id,token,state,created_at)
                VALUES (?,?,?,?)
                ON CONFLICT(job_id) DO UPDATE SET
                    token=excluded.token,state=excluded.state,created_at=excluded.created_at
                """, jobId, token, "PENDING", ts(Instant.now()));
    }

    public Optional<ApprovalRow> getApproval(String jobId) {
        return jdbc.query("""
                SELECT job_id,token,state,created_at
                FROM approvals WHERE job_id=?
                """, (rs, n) -> new ApprovalRow(
                rs.getString("job_id"),
                rs.getString("token"),
                rs.getString("state"),
                instant(rs.getTimestamp("created_at"))
        ), jobId).stream().findFirst();
    }

    public List<Map<String,Object>> latestRunnerRuns(int limit) {
        int bounded = Math.max(1, Math.min(100, limit));
        return jdbc.query("""
                SELECT run_id,started_at,finished_at,status,sources_checked,sources_due,
                       sources_succeeded,source_failures,jobs_fetched,jobs_new,jobs_duplicates,
                       jobs_rejected,jobs_failed,packages_generated,referrals_found,emails_sent,
                       duplicate_emails,errors_json
                FROM runner_runs ORDER BY started_at DESC LIMIT ?
                """, (rs, n) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("run_id", rs.getString("run_id"));
            row.put("started_at", rs.getTimestamp("started_at"));
            row.put("finished_at", rs.getTimestamp("finished_at"));
            row.put("status", rs.getString("status"));
            row.put("sources_checked", rs.getInt("sources_checked"));
            row.put("sources_due", rs.getInt("sources_due"));
            row.put("sources_succeeded", rs.getInt("sources_succeeded"));
            row.put("source_failures", rs.getInt("source_failures"));
            row.put("jobs_fetched", rs.getInt("jobs_fetched"));
            row.put("jobs_new", rs.getInt("jobs_new"));
            row.put("jobs_duplicates", rs.getInt("jobs_duplicates"));
            row.put("jobs_rejected", rs.getInt("jobs_rejected"));
            row.put("jobs_failed", rs.getInt("jobs_failed"));
            row.put("packages_generated", rs.getInt("packages_generated"));
            row.put("referrals_found", rs.getInt("referrals_found"));
            row.put("emails_sent", rs.getInt("emails_sent"));
            row.put("duplicate_emails", rs.getInt("duplicate_emails"));
            row.put("errors_json", rs.getString("errors_json"));
            return row;
        }, bounded);
    }

    public Optional<JobRow> findById(String id) {
        return jdbc.query("""
                SELECT id,source,external_id,company,title,url,description,location,work_mode,
                       employment_type,salary_text,posted_at,discovered_at,status,score
                FROM jobs WHERE id=?
                """, (rs, n) -> new JobRow(
                rs.getString("id"), rs.getString("source"), rs.getString("external_id"),
                rs.getString("company"), rs.getString("title"), rs.getString("url"),
                rs.getString("description"), rs.getString("location"), rs.getString("work_mode"),
                rs.getString("employment_type"), rs.getString("salary_text"),
                rs.getTimestamp("posted_at"), rs.getTimestamp("discovered_at"),
                rs.getString("status"), rs.getObject("score", Double.class)
        ), id).stream().findFirst();
    }

    public void startRunnerRun(String runId, Instant startedAt) {
        jdbc.update("""
                INSERT OR REPLACE INTO runner_runs(run_id,started_at,status)
                VALUES (?,?,?)
                """, runId, ts(startedAt), "running");
    }

    public void finishRunnerRun(String runId, Instant finishedAt, String status,
                                RunnerMetrics metrics, List<String> errors) {
        String errorJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .valueToTree(errors == null ? List.of() : errors).toString();
        jdbc.update("""
                UPDATE runner_runs SET
                    finished_at=?,status=?,sources_checked=?,sources_due=?,sources_succeeded=?,
                    source_failures=?,jobs_fetched=?,jobs_new=?,jobs_duplicates=?,jobs_rejected=?,
                    jobs_failed=?,packages_generated=?,referrals_found=?,emails_sent=?,
                    duplicate_emails=?,errors_json=?
                WHERE run_id=?
                """,
                ts(finishedAt), status, metrics.sourcesChecked(), metrics.sourcesDue(),
                metrics.sourcesSucceeded(), metrics.sourceFailures(), metrics.jobsFetched(),
                metrics.jobsNew(), metrics.jobsDuplicates(), metrics.jobsRejected(),
                metrics.jobsFailed(), metrics.packagesGenerated(), metrics.referralsFound(),
                metrics.emailsSent(), metrics.duplicateEmails(), errorJson, runId);
    }

    private static String day(Instant value) {
        return value.toString().substring(0, 10);
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record JobRow(String id,String source,String externalId,String company,String title,String url,
                         String description,String location,String workMode,String employmentType,
                         String salaryText,Timestamp postedAt,Timestamp discoveredAt,String status,Double score) {}
    public record ApprovalRow(String jobId,String token,String state,Instant createdAt) {}
    public record SourceState(String source,Instant lastAttemptedAt,Instant lastSuccessfulAt,
                              Instant nextRunAt,int consecutiveFailures,int requestsToday,String requestDay) {}
    public record SourceHealth(String source,Instant checkedAt,String status,int jobsFound,long durationMs,String error) {}
    public record RunnerMetrics(int sourcesChecked,int sourcesDue,int sourcesSucceeded,int sourceFailures,
                                int jobsFetched,int jobsNew,int jobsDuplicates,int jobsRejected,int jobsFailed,
                                int packagesGenerated,int referralsFound,int emailsSent,int duplicateEmails) {}
}
