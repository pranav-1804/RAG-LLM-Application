package com.example.rag.retrieval;

import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zero-dependency in-memory lexical index using classic BM25 (Okapi) scoring.
 * Like {@link com.example.rag.store.InMemoryVectorStore} it needs no external service,
 * so the hybrid retrieval path runs fully offline.
 *
 * <p>Always available as a bean; only consulted when {@code rag.retrieval.mode} is
 * {@code lexical} or {@code hybrid} (and the vector store is in-memory).
 */
@Component
public class InMemoryBm25Index implements LexicalIndex {

    /** BM25 term-frequency saturation. */
    private static final double K1 = 1.2;
    /** BM25 length-normalisation. */
    private static final double B = 0.75;

    /** A handful of high-frequency words that add noise to lexical matching. */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "is", "are", "was", "were",
            "for", "on", "with", "as", "by", "at", "it", "this", "that", "be", "do", "how");

    private record Doc(String id, String source, String text,
                       Map<String, Integer> termFreq, int length) {
    }

    private final Map<String, Doc> docs = new LinkedHashMap<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private long totalLength = 0;

    @Override
    public synchronized void index(List<Chunk> chunks) {
        for (Chunk c : chunks) {
            // Replacing an existing id: roll back its old contribution to the stats first.
            Doc previous = docs.remove(c.id());
            if (previous != null) {
                totalLength -= previous.length();
                for (String term : previous.termFreq().keySet()) {
                    docFreq.merge(term, -1, Integer::sum);
                    docFreq.remove(term, 0);
                }
            }

            List<String> tokens = tokenize(c.text());
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
            for (String term : tf.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
            docs.put(c.id(), new Doc(c.id(), c.source(), c.text(), tf, tokens.size()));
            totalLength += tokens.size();
        }
    }

    @Override
    public synchronized List<ScoredChunk> search(String queryText, int topK) {
        if (docs.isEmpty()) {
            return List.of();
        }
        List<String> queryTerms = tokenize(queryText);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        int n = docs.size();
        double avgdl = (double) totalLength / n;

        List<ScoredChunk> scored = new ArrayList<>(n);
        for (Doc d : docs.values()) {
            double score = 0;
            for (String term : queryTerms) {
                Integer f = d.termFreq().get(term);
                if (f == null) {
                    continue;
                }
                int df = docFreq.getOrDefault(term, 0);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                double denom = f + K1 * (1 - B + B * d.length() / avgdl);
                score += idf * (f * (K1 + 1)) / denom;
            }
            if (score > 0) {
                scored.add(new ScoredChunk(d.id(), d.source(), d.text(), score));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
            if (!token.isEmpty() && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
