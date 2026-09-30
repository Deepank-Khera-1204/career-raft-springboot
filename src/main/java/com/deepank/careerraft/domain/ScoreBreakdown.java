package com.deepank.careerraft.domain;

public record ScoreBreakdown(
        double technicalStack,
        double roleSeniority,
        double qualifications,
        double projectEvidence,
        double locationWorkMode,
        double compensation,
        double domain,
        double growth
) {
    public double total() {
        return Math.round((technicalStack + roleSeniority + qualifications + projectEvidence
                + locationWorkMode + compensation + domain + growth) * 100.0) / 100.0;
    }
}
