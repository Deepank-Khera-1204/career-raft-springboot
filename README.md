# Career Raft — Java / Spring Boot

Java 21 + Spring Boot implementation of Career Raft.

## Run locally

From the repository root:

    mvn test
    mvn spring-boot:run

The service listens on port 8080.

Career Raft automatically resolves the repository root from the current directory. You can also set:

    CAREER_RAFT_ROOT=/absolute/path/to/career-raft

## API

    GET  /api/career-raft/health
    POST /api/career-raft/discover
    POST /api/career-raft/run-due
    POST /api/career-raft/assess
    POST /api/career-raft/jobs/{jobId}/approval
    POST /api/career-raft/approval/parse
    GET  /api/career-raft/jobs/{jobId}/approval
    GET  /api/career-raft/sources/{source}/health
    GET  /api/career-raft/runs

Spring health endpoints are also exposed at:

    /actuator/health
    /actuator/health/liveness
    /actuator/health/readiness

## Configuration

Job source configuration is read from config/sources.yaml.

Career evidence is read from:

    data/profile.yaml
    data/experience.yaml
    data/projects.yaml
    data/skills.yaml
    data/education.yaml
    data/certifications.yaml
    data/sources.yaml

Scoring and semantic configuration are read from:

    config/scoring.yaml
    config/ai.yaml

Optional integrations:

    GEMINI_API_KEY
    BRAVE_SEARCH_API_KEY

Email delivery:

    CR_SEND_EMAIL
    CR_EMAIL_TO
    CR_SMTP_HOST
    CR_SMTP_PORT
    CR_SMTP_USERNAME
    CR_SMTP_PASSWORD
    CR_EMAIL_FROM
    CR_SMTP_USE_TLS

Referral research:

    CR_ENABLE_LINKEDIN

Application submission remains manual-only. Career Raft prepares packages and approvals; it does not submit applications automatically.

## Docker

Build from the repository root:

    mvn package -DskipTests
    docker build -f Dockerfile -t career-raft-java .
    docker run --rm -p 8080:8080 career-raft-java
