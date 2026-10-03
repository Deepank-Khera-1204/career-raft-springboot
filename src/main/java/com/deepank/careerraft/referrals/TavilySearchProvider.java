package com.deepank.careerraft.referrals;

import com.deepank.careerraft.discovery.SimpleHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TavilySearchProvider {
    private static final String SEARCH_URL = "https://api.tavily.com/search";

    private final SimpleHttpClient http;
    private final ObjectMapper mapper;

    public TavilySearchProvider(SimpleHttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    public List<ReferralModels.SearchResult> search(String query, int limit) {
        String key = System.getenv("TAVILY_API_KEY");
        if (key == null || key.isBlank()) {
            return List.of();
        }

        int maxResults = Math.max(1, Math.min(20, limit));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("search_depth", "basic");
        payload.put("topic", "general");
        payload.put("max_results", maxResults);
        payload.put("include_answer", false);
        payload.put("include_raw_content", false);
        payload.put("include_images", false);
        payload.put("include_domains", List.of("linkedin.com"));

        try {
            var response = http.postJson(
                    SEARCH_URL,
                    payload,
                    Map.of("Authorization", "Bearer " + key)
            );
            var root = mapper.readTree(response.body());

            List<ReferralModels.SearchResult> results = new ArrayList<>();
            for (var result : root.path("results")) {
                String title = result.path("title").asText("");
                String url = result.path("url").asText("");
                String content = result.path("content").asText("");

                if (!title.isBlank() && !url.isBlank()) {
                    results.add(new ReferralModels.SearchResult(
                            title.trim(),
                            url.trim(),
                            content.trim()
                    ));
                }
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Tavily referral search request failed", e);
        }
    }
}
