package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.embedding.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the chunking strategy from {@code rag.chunking.strategy}:
 * 
 *   {@code semantic} (default) — boundaries where meaning shifts (uses embeddings)</li>
 *   {@code sentence} — pack whole sentences up to a size budget (no embeddings)</li>
 *   {@code fixed} — fixed-size character windows with overlap</li>
 * 
 */
@Configuration
public class ChunkingConfig {

    private static final Logger log = LoggerFactory.getLogger(ChunkingConfig.class);

    @Bean
    public Chunker chunker(RagProperties props, EmbeddingClient embeddings) {
        RagProperties.Chunking c = props.getChunking();
        String strategy = c.getStrategy() == null ? "semantic" : c.getStrategy().toLowerCase();

        switch (strategy) {
            case "fixed" -> {
                log.info("Chunking strategy: fixed (size={}, overlap={})", c.getChunkSize(), c.getOverlap());
                return new FixedSizeChunker(c.getChunkSize(), c.getOverlap());
            }
            case "sentence" -> {
                log.info("Chunking strategy: sentence (maxChars={})", c.getChunkSize());
                return new SentenceChunker(c.getChunkSize());
            }
            default -> {
                log.info("Chunking strategy: semantic (threshold={}, maxChars={})",
                        c.getSimilarityThreshold(), c.getChunkSize());
                return new SemanticChunker(embeddings, c.getSimilarityThreshold(), c.getChunkSize());
            }
        }
    }
}
