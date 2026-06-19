package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code rag.*} configuration block.
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** "memory" or "cosmos". */
    private String vectorStore = "memory";

    /** Default generation provider: "ollama" or "claude". */
    private String llmProvider = "ollama";

    private Retrieval retrieval = new Retrieval();
    private Chunking chunking = new Chunking();

    public static class Retrieval {
        private int topK = 4;
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    public static class Chunking {
        private int chunkSize = 800;
        private int overlap = 120;
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getOverlap() { return overlap; }
        public void setOverlap(int overlap) { this.overlap = overlap; }
    }

    public String getVectorStore() { return vectorStore; }
    public void setVectorStore(String vectorStore) { this.vectorStore = vectorStore; }
    public String getLlmProvider() { return llmProvider; }
    public void setLlmProvider(String llmProvider) { this.llmProvider = llmProvider; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    public Chunking getChunking() { return chunking; }
    public void setChunking(Chunking chunking) { this.chunking = chunking; }
}
