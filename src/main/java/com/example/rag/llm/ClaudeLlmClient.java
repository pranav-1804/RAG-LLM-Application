package com.example.rag.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Remote LLM via the Anthropic Messages API ({@code POST /v1/messages}).
 */
@Component
public class ClaudeLlmClient implements LlmClient {

    private final RestClient client;
    private final String model;
    private final int maxTokens;
    private final boolean configured;

    public ClaudeLlmClient(
            @Value("${claude.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${claude.api-key:}") String apiKey,
            @Value("${claude.model:claude-sonnet-4-6}") String model,
            @Value("${claude.anthropic-version:2023-06-01}") String anthropicVersion,
            @Value("${claude.max-tokens:1024}") int maxTokens) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.configured = StringUtils.hasText(apiKey);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", anthropicVersion)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public boolean isAvailable() {
        return configured;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String system, String user) {
        if (!configured) {
            throw new IllegalStateException(
                    "Claude is not configured. Set ANTHROPIC_API_KEY to route to the remote model.");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));

        Map<String, Object> response = client.post()
                .uri("/v1/messages")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("content") instanceof List<?> content) || content.isEmpty()) {
            throw new IllegalStateException("Unexpected response from Claude");
        }
        Map<String, Object> first = (Map<String, Object>) content.get(0);
        return String.valueOf(first.get("text"));
    }
}
