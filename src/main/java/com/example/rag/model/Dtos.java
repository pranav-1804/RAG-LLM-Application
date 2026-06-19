package com.example.rag.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class Dtos {
    private Dtos() {}

    /** Body for POST /api/ingest. */
    public record IngestRequest(
            @NotBlank String text,
            String source
    ) {}

    /** Response for POST /api/ingest. */
    public record IngestResponse(String source, int chunksStored) {}

    /** Body for POST /api/chat. */
    public record ChatRequest(
            @NotBlank String question,
            String provider,   // "ollama" or "claude"; null -> configured default
            Integer topK       // optional override for retrieval depth
    ) {}

    /** Response for POST /api/chat. */
    public record ChatResponse(
            String answer,
            String provider,
            List<Citation> sources
    ) {}

    public record Citation(String id, String source, double score, String preview) {}
}
