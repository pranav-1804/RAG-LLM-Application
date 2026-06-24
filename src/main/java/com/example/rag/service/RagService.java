package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.llm.LlmClient;
import com.example.rag.llm.LlmRouter;
import com.example.rag.model.Chunk;
import com.example.rag.model.Dtos.ChatResponse;
import com.example.rag.model.Dtos.Citation;
import com.example.rag.model.ScoredChunk;
import com.example.rag.retrieval.LexicalIndex;
import com.example.rag.retrieval.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Orchestrates the RAG flow:
 * ingest  = chunk -> embed -> store
 * query   = embed question -> vector search -> build prompt -> generate
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String SYSTEM_PROMPT = """
            You are a retrieval-augmented assistant. Answer the user's question using ONLY the
            provided context. If the context does not contain the answer, say you don't know.
            Be concise and cite the source numbers (e.g. [1], [2]) you relied on.""";

    private final EmbeddingClient embeddings;
    private final com.example.rag.store.VectorStore store;
    private final LexicalIndex lexicalIndex;
    private final Retriever retriever;
    private final LlmRouter router;
    private final RagProperties props;
    private final Chunker chunker;

    public RagService(EmbeddingClient embeddings,
                      com.example.rag.store.VectorStore store,
                      LexicalIndex lexicalIndex,
                      Retriever retriever,
                      LlmRouter router,
                      RagProperties props,
                      Chunker chunker) {
        this.embeddings = embeddings;
        this.store = store;
        this.lexicalIndex = lexicalIndex;
        this.retriever = retriever;
        this.router = router;
        this.props = props;
        this.chunker = chunker;
    }

    /** Chunk, embed and store a document. Returns the number of chunks stored. */
    public int ingest(String text, String source) {
        String src = StringUtils.hasText(source) ? source : "doc-" + UUID.randomUUID();
        List<String> texts = chunker.chunk(text);

        if (texts.isEmpty()) {
            return 0;
        }

        List<List<Float>> vectors = embeddings.embedAll(texts);
        List<Chunk> chunks = IntStream.range(0, texts.size())
                .mapToObj(i -> new Chunk(
                        src + "#" + i, src, texts.get(i), vectors.get(i)))
                .toList();

        store.upsert(chunks);
        if (!(store instanceof com.example.rag.store.HybridSearchStore)) {
            lexicalIndex.index(chunks);
        }
        log.info("Ingested {} chunks from source '{}'", chunks.size(), src);
        return chunks.size();
    }

    
    public ChatResponse chat(String question, String provider, Integer topKOverride) {
        int topK = topKOverride != null && topKOverride > 0
                ? topKOverride : props.getRetrieval().getTopK();

        List<Float> queryEmbedding = embeddings.embed(question);
        List<ScoredChunk> hits = retriever.retrieve(question, queryEmbedding, topK);

        LlmClient llm = router.resolve(provider);
        String context = buildContext(hits);
        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question;

        String answer = hits.isEmpty()
                ? "I don't have any indexed documents yet, so I can't answer from context."
                : llm.complete(SYSTEM_PROMPT, userPrompt);

        List<Citation> citations = IntStream.range(0, hits.size())
                .mapToObj(i -> {
                    ScoredChunk h = hits.get(i);
                    return new Citation(h.id(), h.source(),
                            round(h.score()), preview(h.text()));
                })
                .toList();

        return new ChatResponse(answer, llm.name(), citations);
    }

    private String buildContext(List<ScoredChunk> hits) {
        return IntStream.range(0, hits.size())
                .mapToObj(i -> "[" + (i + 1) + "] (" + hits.get(i).source() + ") "
                        + hits.get(i).text())
                .collect(Collectors.joining("\n\n"));
    }

    private static String preview(String text) {
        return text.length() <= 160 ? text : text.substring(0, 160) + "…";
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
