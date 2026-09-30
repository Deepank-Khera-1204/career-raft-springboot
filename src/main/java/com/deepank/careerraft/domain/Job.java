package com.deepank.careerraft.domain;

import java.time.Instant;
import java.util.Map;

public record Job(
        String id,
        String source,
        String externalId,
        String company,
        String title,
        String url,
        String description,
        String location,
        String workMode,
        String employmentType,
        String salaryText,
        Instant postedAt,
        Instant discoveredAt,
        Map<String,Object> raw
) {
    public Job {
        description = description == null ? "" : description;
        discoveredAt = discoveredAt == null ? Instant.now() : discoveredAt;
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public Job(
            String id, String source, String externalId, String company, String title,
            String url, String description, String location, String workMode,
            String employmentType, String salaryText, Instant postedAt, Instant discoveredAt
    ) {
        this(id, source, externalId, company, title, url, description, location,
                workMode, employmentType, salaryText, postedAt, discoveredAt, Map.of());
    }
}
