package com.deepank.careerraft.intelligence;

import java.util.List;

public record EvidenceClaim(
        String id,
        EvidenceType type,
        String source,
        String title,
        String text,
        List<String> keywords,
        List<String> technologies,
        List<String> facts,
        boolean immutable,
        List<String> sourceIds
) {
    public EvidenceClaim {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
        facts = facts == null ? List.of() : List.copyOf(facts);
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }
}
