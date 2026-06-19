package com.example.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Chooses the embedding implementation based on {@code rag.embedding-provider}:
 * <ul>
 *   <li>{@code ollama} — local Ollama embedding model (e.g. nomic-embed-text)</li>
 *   <li>{@code azure}  — Azure OpenAI embeddings</li>
 *   <li>{@code auto} (default) — Azure if configured, otherwise Ollama</li>
 * </ul>
 */
@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    public EmbeddingClient embeddingClient(
            @Value("${rag.embedding-provider:auto}") String provider,
            // Azure OpenAI
            @Value("${azure.openai.endpoint:}") String azEndpoint,
            @Value("${azure.openai.api-key:}") String azApiKey,
            @Value("${azure.openai.embeddings-deployment:text-embedding-3-small}") String azDeployment,
            @Value("${azure.openai.api-version:2024-02-01}") String azApiVersion,
            @Value("${azure.openai.embedding-dimensions:1536}") int azDimensions,
            // Ollama
            @Value("${ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${ollama.embed-model:nomic-embed-text}") String ollamaModel,
            @Value("${ollama.embed-dimensions:768}") int ollamaDimensions) {

        String choice = provider.toLowerCase();

        boolean azureConfigured = StringUtils.hasText(azEndpoint) && StringUtils.hasText(azApiKey);
        if ("azure".equals(choice) || ("auto".equals(choice) && azureConfigured)) {
            log.info("Using Azure OpenAI embeddings (deployment={})", azDeployment);
            return new AzureOpenAiEmbeddingClient(azEndpoint, azApiKey, azDeployment, azApiVersion, azDimensions);
        }
            log.info("Using Ollama embeddings (model={}, dim={})", ollamaModel, ollamaDimensions);
            return new OllamaEmbeddingClient(ollamaUrl, ollamaModel, ollamaDimensions);
        


    }
}
