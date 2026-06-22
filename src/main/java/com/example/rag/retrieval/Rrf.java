package com.example.rag.retrieval;

import com.example.rag.model.ScoredChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion: merges several ranked result lists into one, using only
 * each chunk's <em>rank</em> within a list (not its raw score), which makes it robust
 * across incompatible score scales (e.g. cosine similarity vs BM25).
 *
 * <p>For a chunk appearing at 0-based rank {@code r} in a list, that list contributes
 * {@code 1 / (k + r + 1)} to the chunk's fused score; contributions sum across lists.
 */
final class Rrf {

    private Rrf() {
    }

    /**
     * @param lists ranked result lists (best first within each)
     * @param k     RRF constant (commonly 60); larger flattens the rank weighting
     * @param topK  maximum number of fused results to return
     * @return chunks ordered by fused score, best first, at most {@code topK}; the
     *         returned {@link ScoredChunk#score()} is the fused RRF score
     */
    static List<ScoredChunk> fuse(List<List<ScoredChunk>> lists, int k, int topK) {
        Map<String, Double> fusedScore = new LinkedHashMap<>();
        Map<String, ScoredChunk> byId = new LinkedHashMap<>();

        for (List<ScoredChunk> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                ScoredChunk c = list.get(rank);
                fusedScore.merge(c.id(), 1.0 / (k + rank + 1), Double::sum);
                byId.putIfAbsent(c.id(), c);
            }
        }

        List<ScoredChunk> fused = new ArrayList<>(fusedScore.size());
        for (Map.Entry<String, Double> e : fusedScore.entrySet()) {
            ScoredChunk c = byId.get(e.getKey());
            fused.add(new ScoredChunk(c.id(), c.source(), c.text(), e.getValue()));
        }

        fused.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return fused.size() > topK ? fused.subList(0, topK) : fused;
    }
}
