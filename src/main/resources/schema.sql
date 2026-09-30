CREATE TABLE IF NOT EXISTS jobs (
 id TEXT PRIMARY KEY, source TEXT NOT NULL, external_id TEXT NOT NULL, company TEXT NOT NULL,
 title TEXT NOT NULL, url TEXT NOT NULL, description TEXT NOT NULL, location TEXT, work_mode TEXT,
 employment_type TEXT, salary_text TEXT, posted_at TIMESTAMP, discovered_at TIMESTAMP NOT NULL,
 status TEXT NOT NULL DEFAULT 'DISCOVERED', score REAL, UNIQUE(source, external_id)
);
CREATE INDEX IF NOT EXISTS idx_jobs_score ON jobs(score DESC);
CREATE INDEX IF NOT EXISTS idx_jobs_source ON jobs(source);

CREATE TABLE IF NOT EXISTS approvals (
 job_id TEXT PRIMARY KEY, token TEXT NOT NULL, state TEXT NOT NULL, created_at TIMESTAMP NOT NULL,
 FOREIGN KEY(job_id) REFERENCES jobs(id)
);

CREATE TABLE IF NOT EXISTS source_state (
 source TEXT PRIMARY KEY, last_attempted_at TIMESTAMP, last_successful_at TIMESTAMP,
 next_run_at TIMESTAMP, consecutive_failures INTEGER NOT NULL DEFAULT 0,
 requests_today INTEGER NOT NULL DEFAULT 0, request_day TEXT
);

CREATE TABLE IF NOT EXISTS notification_deliveries (
 job_id TEXT NOT NULL, provider TEXT NOT NULL, recipient TEXT NOT NULL,
 subject TEXT NOT NULL, sent_at TIMESTAMP NOT NULL,
 PRIMARY KEY(job_id, provider, recipient),
 FOREIGN KEY(job_id) REFERENCES jobs(id)
);

CREATE TABLE IF NOT EXISTS referral_usage (
 usage_day TEXT PRIMARY KEY, reserved_requests INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS source_health (
 source TEXT PRIMARY KEY, checked_at TIMESTAMP NOT NULL, status TEXT NOT NULL,
 jobs_found INTEGER NOT NULL DEFAULT 0, duration_ms INTEGER NOT NULL DEFAULT 0, error TEXT
);

CREATE TABLE IF NOT EXISTS runner_runs (
 run_id TEXT PRIMARY KEY, started_at TIMESTAMP NOT NULL, finished_at TIMESTAMP,
 status TEXT NOT NULL, sources_checked INTEGER NOT NULL DEFAULT 0, sources_due INTEGER NOT NULL DEFAULT 0,
 sources_succeeded INTEGER NOT NULL DEFAULT 0, source_failures INTEGER NOT NULL DEFAULT 0,
 jobs_fetched INTEGER NOT NULL DEFAULT 0, jobs_new INTEGER NOT NULL DEFAULT 0,
 jobs_duplicates INTEGER NOT NULL DEFAULT 0, jobs_rejected INTEGER NOT NULL DEFAULT 0,
 jobs_failed INTEGER NOT NULL DEFAULT 0, packages_generated INTEGER NOT NULL DEFAULT 0,
 referrals_found INTEGER NOT NULL DEFAULT 0, emails_sent INTEGER NOT NULL DEFAULT 0,
 duplicate_emails INTEGER NOT NULL DEFAULT 0, errors_json TEXT NOT NULL DEFAULT '[]'
);
