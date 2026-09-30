package com.deepank.careerraft.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Component
public class SimpleHttpClient {
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public SimpleHttpClient(ObjectMapper mapper) { this.mapper = mapper; }

    public HttpResponseData get(String url, String accept) {
        return get(url, accept, Map.of());
    }

    public HttpResponseData get(String url, String accept, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", accept)
                .header("User-Agent", "Career-Raft/1.0");
        headers.forEach(builder::header);
        return send(builder.GET().build());
    }
    public HttpResponseData postJson(String url, Object payload) { try { String body=mapper.writeValueAsString(payload); return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept","application/json").header("Content-Type","application/json").header("User-Agent","Career-Raft/1.0").POST(HttpRequest.BodyPublishers.ofString(body)).build()); } catch(Exception e){throw new HttpClientException("Unable to encode JSON request",e);} }
    public HttpResponseData postForm(String url, String formBody) { return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept","application/json,text/plain,*/*").header("Content-Type","application/x-www-form-urlencoded;charset=UTF-8").header("User-Agent","Career-Raft/1.0").POST(HttpRequest.BodyPublishers.ofString(formBody)).build()); }
    public String getText(String url,String accept){HttpResponseData r=get(url,accept);return r.body();}
    public Map<String,Object> getJson(String url){try{return mapper.readValue(getText(url,"application/json"),new TypeReference<>(){});}catch(Exception e){throw new HttpClientException("Invalid JSON response from "+url,e);}}
    public Map<String,Object> postJsonObject(String url,Object payload){try{return mapper.readValue(postJson(url,payload).body(),new TypeReference<>(){});}catch(Exception e){throw new HttpClientException("Invalid JSON response from "+url,e);}}
    private HttpResponseData send(HttpRequest request){try{HttpResponse<String> r=client.send(request,HttpResponse.BodyHandlers.ofString());if(r.statusCode()>=400)throw new HttpClientException("HTTP "+r.statusCode()+" from "+request.uri());return new HttpResponseData(r.statusCode(),r.body(),r.headers().map());}catch(IOException|InterruptedException e){Thread.currentThread().interrupt();throw new HttpClientException("Network error from "+request.uri(),e);}}
    public record HttpResponseData(int status,String body,Map<String,java.util.List<String>> headers){}
    public static class HttpClientException extends RuntimeException { public HttpClientException(String m){super(m);} public HttpClientException(String m,Throwable t){super(m,t);} }
}
