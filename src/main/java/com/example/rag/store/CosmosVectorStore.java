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
import java.util.StringJoiner;

/**
 * Vector store backed by Azure Cosmos DB for NoSQL using its native
 * {@code VectorDistance()} search function.
 *
 * <p>Active when {@code rag.vector-store=cosmos}.
 *
 * The target container is expected to be created with a vector embedding
 * policy and a vector index on {@code /embedding} (see README). For the hybrid and
 * lexical retrieval modes it must <em>also</em> have a full-text policy and a full-text
 * index on {@code /text}; the app then issues Cosmos's native {@code FullTextScore} /
 * {@code RRF} queries server-side instead of using the in-memory BM25 index. The app
 * connects, upserts documents, and runs ORDER BY VectorDistance / ORDER BY RANK queries
 * for retrieval.
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "cosmos")
public class CosmosVectorStore implements VectorStore, HybridSearchStore {

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
    public List<ScoredChunk> hybridSearch(String queryText, List<Float> queryEmbedding, int topK) {
        List<String> terms = tokenize(queryText);
        if (terms.isEmpty()) {
            return search(queryEmbedding, topK);
        }

        List<SqlParameter> params = new ArrayList<>();
        params.add(new SqlParameter("@k", topK));
        params.add(new SqlParameter("@embedding", queryEmbedding));
        String termPlaceholders = bindTerms(terms, params);

        String sql = "SELECT TOP @k c.id, c.source, c.text FROM c "
                + "ORDER BY RANK RRF(VectorDistance(c.embedding, @embedding), "
                + "FullTextScore(c.text, " + termPlaceholders + "))";

        return rankedQuery(new SqlQuerySpec(sql, params));
    }

    @Override
    public List<ScoredChunk> lexicalSearch(String queryText, int topK) {
        List<String> terms = tokenize(queryText);
        if (terms.isEmpty()) {
            return List.of();
        }

        List<SqlParameter> params = new ArrayList<>();
        params.add(new SqlParameter("@k", topK));
        String termPlaceholders = bindTerms(terms, params);

        String sql = "SELECT TOP @k c.id, c.source, c.text FROM c "
                + "ORDER BY RANK FullTextScore(c.text, " + termPlaceholders + ")";

        return rankedQuery(new SqlQuerySpec(sql, params));
    }

    private List<ScoredChunk> rankedQuery(SqlQuerySpec spec) {
        CosmosPagedIterable<RankRow> rows =
                container.queryItems(spec, new CosmosQueryRequestOptions(), RankRow.class);

        List<ScoredChunk> results = new ArrayList<>();
        int position = 0;
        for (RankRow r : rows) {
            results.add(new ScoredChunk(r.id, r.source, r.text, 1.0 / (position + 1)));
            position++;
        }
        return results;
    }

    private static String bindTerms(List<String> terms, List<SqlParameter> params) {
        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < terms.size(); i++) {
            String name = "@t" + i;
            placeholders.add(name);
            params.add(new SqlParameter(name, terms.get(i)));
        }
        return placeholders.toString();
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
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

    
    public static class ResultRow {
        public String id;
        public String source;
        public String text;
        public double distance;
    }

    
    public static class RankRow {
        public String id;
        public String source;
        public String text;
    }
}
