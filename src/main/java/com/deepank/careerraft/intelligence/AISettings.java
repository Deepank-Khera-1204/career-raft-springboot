package com.deepank.careerraft.intelligence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.deepank.careerraft.config.RuntimePaths;import com.fasterxml.jackson.databind.ObjectMapper;import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;import org.springframework.stereotype.Component;import java.nio.file.*;import java.util.*;
@Component public class AISettings{
 public final boolean enabled;public final String provider,model,apiKeyEnv,thinkingLevel;public final int maxCallsPerRun,maxOutputTokens;public final double minDeterministicScoreForLlm;
 public AISettings(){Map<String,Object>m=load();enabled=Boolean.parseBoolean(String.valueOf(m.getOrDefault("enabled",false)));provider=String.valueOf(m.getOrDefault("provider","gemini"));model=String.valueOf(m.getOrDefault("model","gemini-3.5-flash-lite"));apiKeyEnv=String.valueOf(m.getOrDefault("api_key_env","GEMINI_API_KEY"));maxCallsPerRun=Integer.parseInt(String.valueOf(m.getOrDefault("max_calls_per_run",10)));maxOutputTokens=Integer.parseInt(String.valueOf(m.getOrDefault("max_output_tokens",1800)));thinkingLevel=String.valueOf(m.getOrDefault("thinking_level","low"));minDeterministicScoreForLlm=Double.parseDouble(String.valueOf(m.getOrDefault("min_deterministic_score_for_llm",45)));}
 private Map<String,Object>load(){Path p=RuntimePaths.config().resolve("ai.yaml");if(!Files.exists(p))return Map.of("enabled",false);try{return new ObjectMapper(new YAMLFactory()).readValue(Files.readString(p),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Unable to load config/ai.yaml",e);}}
 public String apiKey(){String k=System.getenv(apiKeyEnv);if(k==null||k.isBlank())throw new IllegalStateException("LLM is enabled but "+apiKeyEnv+" is not set.");return k;}
}
