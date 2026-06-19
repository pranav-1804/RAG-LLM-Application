package com.example.rag.llm;

import com.example.rag.config.RagProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks an {@link LlmClient} by name, falling back to the configured default.
 * This is the seam that makes "local vs remote" a per-request decision.
 */
@Component
public class LlmRouter {

    private final Map<String, LlmClient> byName;
    private final String defaultProvider;

    public LlmRouter(List<LlmClient> clients, RagProperties props) {
        this.byName = clients.stream()
                .collect(Collectors.toMap(c -> c.name().toLowerCase(), Function.identity()));
        this.defaultProvider = props.getLlmProvider().toLowerCase();
    }

    /** Resolve a provider, using the request value when present else the default. */
    public LlmClient resolve(String requested) {
        String key = StringUtils.hasText(requested) ? requested.toLowerCase() : defaultProvider;
        LlmClient client = byName.get(key);
        if (client == null) {
            throw new IllegalArgumentException(
                    "Unknown LLM provider '" + key + "'. Available: " + byName.keySet());
        }
        if (!client.isAvailable()) {
            throw new IllegalStateException("LLM provider '" + key + "' is not configured.");
        }
        return client;
    }
}
