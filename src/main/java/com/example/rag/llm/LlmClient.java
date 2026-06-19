package com.example.rag.llm;

/**
 * A chat-completion provider.
 */
public interface LlmClient {

    /** Identifier used for routing, e.g. "ollama" or "claude". */
    String name();

    /** Whether this provider has the config it needs to run. */
    boolean isAvailable();

    /**
     * Generate an answer.
     *
     * @param system system / instruction prompt
     * @param user   user prompt (already includes retrieved context)
     */
    String complete(String system, String user);
}
