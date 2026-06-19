package com.example.rag.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Local LLM via Ollama's {@code /api/chat} endpoint.
 */
@Component
public class OllamaLlmClient implements LlmClient {

    private final RestClient client;
    private final String model;

    public OllamaLlmClient(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3.1}") String model) {
        this.model = model;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        // Local server assumed reachable; the call itself surfaces errors clearly.
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        Map<String, Object> response = client.post()
                .uri("/api/chat")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("message") instanceof Map<?, ?> msg)) {
            throw new IllegalStateException("Unexpected response from Ollama");
        }
        return String.valueOf(((Map<String, Object>) msg).get("content"));
    }
}
