package com.example.rag.store;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vector store backed by Azure Cosmos DB for NoSQL using its native
 * {@code VectorDistance()} search function.
 *
 * <p>Active when {@code rag.vector-store=cosmos}.
 *
 * <p>The target container is expected to be created with a vector embedding
 * policy and a vector index on {@code /embedding} (see README). The app connects,
 * upserts documents, and runs an ORDER BY VectorDistance query for retrieval.
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "cosmos")
public class CosmosVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(CosmosVectorStore.class);

    private final CosmosClient client;
    private final CosmosContainer container;

    public CosmosVectorStore(
            @Value("${cosmos.endpoint}") String endpoint,
            @Value("${cosmos.key}") String key,
            @Value("${cosmos.database}") String database,
            @Value("${cosmos.container}") String containerName) {

        this.client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(com.azure.cosmos.ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(false)
                .buildClient();

        this.container = client.getDatabase(database).getContainer(containerName);
    }

    @PostConstruct
    void ready() {
        log.info("CosmosVectorStore connected to container '{}'", container.getId());
    }

    @Override
    public void upsert(List<Chunk> chunks) {
        for (Chunk c : chunks) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.id());
            item.put("source", c.source());
            item.put("text", c.text());
            item.put("embedding", c.embedding());
            container.upsertItem(item, new PartitionKey(c.id()), null);
        }
    }

    @Override
    public List<ScoredChunk> search(List<Float> queryEmbedding, int topK) {
        // VectorDistance returns *distance* (smaller = closer); ORDER BY ascending.
        String sql = "SELECT TOP @k c.id, c.source, c.text, "
                + "VectorDistance(c.embedding, @embedding) AS distance "
                + "FROM c ORDER BY VectorDistance(c.embedding, @embedding)";

        SqlQuerySpec spec = new SqlQuerySpec(sql, List.of(
                new SqlParameter("@k", topK),
                new SqlParameter("@embedding", queryEmbedding)));

        CosmosPagedIterable<ResultRow> rows =
                container.queryItems(spec, new CosmosQueryRequestOptions(), ResultRow.class);

        List<ScoredChunk> results = new ArrayList<>();
        for (ResultRow r : rows) {
            // Convert distance to a similarity-style score (1 - distance) for consistency.
            results.add(new ScoredChunk(r.id, r.source, r.text, 1.0 - r.distance));
        }
        return results;
    }

    @Override
    public long count() {
        SqlQuerySpec spec = new SqlQuerySpec("SELECT VALUE COUNT(1) FROM c");
        CosmosPagedIterable<Integer> rows =
                container.queryItems(spec, new CosmosQueryRequestOptions(), Integer.class);
        for (Integer n : rows) {
            return n;
        }
        return 0;
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }

    /** Projection for query results. */
    public static class ResultRow {
        public String id;
        public String source;
        public String text;
        public double distance;
    }
}
