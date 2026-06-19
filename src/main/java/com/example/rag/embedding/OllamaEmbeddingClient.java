package com.example.rag.embedding;

import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Embedding client backed by a local Ollama embedding model via {@code POST /api/embed}.
 *
 * <p>Request:  {@code {"model": "nomic-embed-text", "input": ["a", "b"]}}
 * <p>Response: {@code {"embeddings": [[...], [...]]}}
 *
 * <p>Lets the whole pipeline run locally and free — no Azure or API keys.
 */
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient client;
    private final String model;
    private final int dimensions;

    public OllamaEmbeddingClient(String baseUrl, String model, int dimensions) {
        this.model = model;
        this.dimensions = dimensions;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Float>> embedAll(List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts);

        Map<String, Object> response = client.post()
                .uri("/api/embed")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("embeddings") instanceof List<?> embeddings)) {
            throw new IllegalStateException(
                    "Unexpected response from Ollama /api/embed. Is the model '" + model + "' pulled?");
        }

        return embeddings.stream()
                .map(emb -> ((List<Number>) emb).stream().map(Number::floatValue).toList())
                .toList();
    }
}
