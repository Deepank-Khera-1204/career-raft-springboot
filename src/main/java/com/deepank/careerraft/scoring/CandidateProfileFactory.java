package com.deepank.careerraft.scoring;

import com.deepank.careerraft.career.CareerKnowledgeBase;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CandidateProfileFactory {
    private final CareerKnowledgeBase kb;

    public CandidateProfileFactory(CareerKnowledgeBase kb) {
        this.kb = kb;
    }

    public ScoringEngine.CandidateProfile build() {
        Map<String,Object> profile = kb.profile();
        Map<String,Object> prefs = map(profile.get("job_preferences"));
        Map<String,Object> nested = map(prefs.get("preferences"));
        Map<String,Object> salary = map(prefs.get("salary"));
        if (salary.isEmpty()) salary = map(nested.get("salary"));

        List<String> roles = strings(prefs.get("preferred_role_families"));
        List<String> locations = strings(prefs.get("preferred_locations"));
        List<String> domains = strings(prefs.get("preferred_domains"));
        List<String> exclusions = strings(prefs.get("hard_exclusions"));
        List<String> seniority = strings(prefs.get("hard_seniority_exclusions"));

        Map<String,Object> professional = map(profile.get("professional_profile"));
        double years = number(professional.getOrDefault("current_experience_years", 0));
        double ceiling = number(prefs.getOrDefault("experience_ceiling_years", years));
        double floor = number(salary.getOrDefault("minimum_lpa", 0));

        return new ScoringEngine.CandidateProfile(
                kb.directSkills(),
                roles,
                locations,
                domains,
                exclusions,
                seniority,
                years,
                floor,
                kb.education(),
                ceiling
        );
    }

    private static double number(Object value) {
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception e) { return 0; }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object value) {
        return value instanceof Map<?,?> m ? (Map<String,Object>)(Map<?,?>)m : Map.of();
    }
}
