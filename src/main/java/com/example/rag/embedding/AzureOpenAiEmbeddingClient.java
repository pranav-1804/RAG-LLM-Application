package com.example.rag.embedding;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Embedding client backed by Azure OpenAI's embeddings REST API.
 *
 * <p>Calls {@code POST {endpoint}/openai/deployments/{deployment}/embeddings}
 * with the {@code api-key} header. Built by {@link EmbeddingConfig} when an
 * endpoint and key are present.
 */
public class AzureOpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient client;
    private final String deployment;
    private final String apiVersion;
    private final int dimensions;

    public AzureOpenAiEmbeddingClient(String endpoint, String apiKey, String deployment,
                                      String apiVersion, int dimensions) {
        this.deployment = deployment;
        this.apiVersion = apiVersion;
        this.dimensions = dimensions;
        this.client = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Float>> embedAll(List<String> texts) {
        Map<String, Object> body = Map.of(
                "input", texts,
                "dimensions", dimensions);

        Map<String, Object> response = client.post()
                .uri(uri -> uri.path("/openai/deployments/{deployment}/embeddings")
                        .queryParam("api-version", apiVersion)
                        .build(deployment))
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("data") instanceof List<?> data)) {
            throw new IllegalStateException("Unexpected embeddings response from Azure OpenAI");
        }

        return data.stream()
                .map(item -> ((Map<String, Object>) item).get("embedding"))
                .map(emb -> ((List<Number>) emb).stream().map(Number::floatValue).toList())
                .toList();
    }
}
