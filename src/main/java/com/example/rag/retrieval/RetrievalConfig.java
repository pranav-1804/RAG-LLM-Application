package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import com.example.rag.store.HybridSearchStore;
import com.example.rag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the retrieval strategy from {@code rag.retrieval.mode}:

 *   {@code hybrid} (default) — vector + BM25 keyword search fused with RRF</li>
 *   {@code vector} — dense vector similarity only (the original behaviour)</li>
 *   {@code lexical} — BM25 keyword search only</li>

 *
 * The store choice is independent of the mode. With the in-memory store, lexical and
 * hybrid fuse the in-memory {@link LexicalIndex} (BM25) with vector search locally. When
 * the store implements {@link HybridSearchStore} (e.g. Cosmos DB), those modes are
 * delegated to the store's native server-side full-text + hybrid search instead.
 */
@Configuration
public class RetrievalConfig {

    private static final Logger log = LoggerFactory.getLogger(RetrievalConfig.class);

    @Bean
    public Retriever retriever(RagProperties props, VectorStore store, LexicalIndex lexicalIndex) {
        RagProperties.Retrieval r = props.getRetrieval();
        String mode = r.getMode() == null ? "hybrid" : r.getMode().toLowerCase();

        if (store instanceof HybridSearchStore hybridStore) {
            switch (mode) {
                case "vector" -> {
                    log.info("Retrieval mode: vector (native store)");
                    return new VectorRetriever(store);
                }
                case "lexical" -> {
                    log.info("Retrieval mode: lexical (native full-text)");
                    return new CosmosLexicalRetriever(hybridStore);
                }
                default -> {
                    log.info("Retrieval mode: hybrid (native full-text + vector, store-side RRF)");
                    return new CosmosHybridRetriever(hybridStore);
                }
            }
        }

        switch (mode) {
            case "vector" -> {
                log.info("Retrieval mode: vector");
                return new VectorRetriever(store);
            }
            case "lexical" -> {
                log.info("Retrieval mode: lexical (BM25)");
                return new LexicalRetriever(lexicalIndex);
            }
            default -> {
                log.info("Retrieval mode: hybrid (vector + BM25, RRF k={}, pool={})",
                        r.getRrfK(), r.getCandidatePool());
                return new HybridRetriever(store, lexicalIndex, r.getRrfK(), r.getCandidatePool());
            }
        }
    }
}
