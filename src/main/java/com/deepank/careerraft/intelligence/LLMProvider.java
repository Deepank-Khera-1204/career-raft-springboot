package com.deepank.careerraft.intelligence;
import java.util.Map;
public interface LLMProvider { Map<String,Object> generateJson(String prompt, Map<String,Object> responseSchema); String name(); }
