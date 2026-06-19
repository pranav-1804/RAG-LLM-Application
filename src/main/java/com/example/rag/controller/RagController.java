package com.example.rag.controller;

import com.example.rag.model.Dtos.ChatRequest;
import com.example.rag.model.Dtos.ChatResponse;
import com.example.rag.model.Dtos.IngestRequest;
import com.example.rag.model.Dtos.IngestResponse;
import com.example.rag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST surface for the RAG assistant.
 */
@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService rag;

    public RagController(RagService rag) {
        this.rag = rag;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /** Ingest a document: chunk -> embed -> store. */
    @PostMapping("/ingest")
    public IngestResponse ingest(@Valid @RequestBody IngestRequest req) {
        String source = StringUtils.hasText(req.source()) ? req.source() : "untitled";
        int n = rag.ingest(req.text(), source);
        return new IngestResponse(source, n);
    }

    /** Ask a question; answer is grounded in retrieved chunks. */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest req) {
        return rag.chat(req.question(), req.provider(), req.topK());
    }
}
