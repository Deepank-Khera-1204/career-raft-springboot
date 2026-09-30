package com.deepank.careerraft.intelligence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component public class GeminiProvider implements LLMProvider {
    private final AISettings settings;
    private final ObjectMapper mapper=new ObjectMapper();
    private final HttpClient client=HttpClient.newHttpClient();
    public GeminiProvider(AISettings settings){this.settings=settings;}
    public String name(){return "GeminiProvider";}
    public Map<String,Object> generateJson(String prompt,Map<String,Object>schema){
        if(!settings.enabled)throw new IllegalStateException("Gemini provider is disabled");
        Map<String,Object>payload=new LinkedHashMap<>();
        payload.put("model",settings.model);payload.put("input",prompt);payload.put("store",false);
        payload.put("response_format",Map.of("type","text","mime_type","application/json","schema",schema));
        payload.put("generation_config",Map.of("max_output_tokens",settings.maxOutputTokens,"thinking_level",settings.thinkingLevel));
        try{
            HttpRequest req=HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .timeout(Duration.ofSeconds(45)).header("Content-Type","application/json")
                    .header("x-goog-api-key",settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String>res=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(res.statusCode()>=400)throw new IllegalStateException("Gemini API returned HTTP "+res.statusCode()+": "+res.body());
            Map<String,Object>outer=mapper.readValue(res.body(),new TypeReference<>(){});
            if(!"completed".equals(String.valueOf(outer.get("status"))))throw new IllegalStateException("Gemini interaction did not complete: "+outer.get("errors"));
            StringBuilder output=new StringBuilder();
            Object steps=outer.get("steps");
            if(steps instanceof List<?> list)for(Object raw:list)if(raw instanceof Map<?,?> step){
                if(!"model_output".equals(String.valueOf(step.get("type"))))continue;
                Object content=step.get("content");
                if(content instanceof List<?> parts)for(Object part:parts)if(part instanceof Map<?,?> block){
                    if("text".equals(String.valueOf(block.get("type"))))output.append(Objects.toString(block.get("text"),""));
                }
            }
            if(output.isEmpty())throw new IllegalStateException("Gemini response did not contain model output text.");
            return mapper.readValue(output.toString(),new TypeReference<>(){});
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Gemini API request interrupted",e);}
        catch(Exception e){if(e instanceof IllegalStateException)throw (IllegalStateException)e;throw new IllegalStateException("Gemini request failed",e);}
    }
}
