package com.deepank.careerraft.intelligence;

import java.util.List;

public record EvidenceMatch(String claimId, double score, List<String> matchedTerms, String reason) {
    public EvidenceMatch {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
        reason = reason == null ? "" : reason;
    }
}
