package com.test.rag.service.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.rag.model.CrawlSiteSummary;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.DocumentSummary;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PgVectorStoreService implements VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PgVectorStoreService(VectorStore vectorStore, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsert(List<EmbeddedChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        List<Document> documents = chunks.stream().map(this::toDocument).toList();
        vectorStore.add(documents);
        log.info("Upserted chunks={} latencyMs={}", chunks.size(), System.currentTimeMillis() - start);
    }

    @Override
    public List<ScoredChunk> search(float[] queryEmbedding, int topK, BigDecimal threshold) {
        long start = System.currentTimeMillis();
        String vectorStr = toVectorString(queryEmbedding);

        String sql = "SELECT id, content, metadata::text, " +
                "1 - (embedding <=> CAST(? AS vector)) AS score " +
                "FROM vector_store " +
                "WHERE 1 - (embedding <=> CAST(? AS vector)) >= ? " +
                "ORDER BY embedding <=> CAST(? AS vector) " +
                "LIMIT ?";

        List<ScoredChunk> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String id = rs.getString("id");
            String content = rs.getString("content");
            Map<String, Object> metaRaw = parseMetadata(rs.getString("metadata"));
            BigDecimal score = BigDecimal.valueOf(rs.getDouble("score"));

            Map<String, String> meta = metaRaw.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));

            String chunkId = Objects.nonNull(metaRaw.get("chunkId")) ? String.valueOf(metaRaw.get("chunkId")) : id;
            int chunkIndex = parseIntOrZero(metaRaw.get("chunkIndex"));
            int tokenCount = parseIntOrZero(metaRaw.get("tokenCount"));

            DocumentChunk chunk = new DocumentChunk(chunkId, content, chunkIndex, tokenCount, meta);
            return new ScoredChunk(chunk, score);
        }, vectorStr, vectorStr, threshold.doubleValue(), vectorStr, topK);

        BigDecimal minScore = results.stream().map(ScoredChunk::similarityScore).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxScore = results.stream().map(ScoredChunk::similarityScore).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        log.info("Search topK={} returned={} scoreRange=[{},{}] latencyMs={}",
                topK, results.size(),
                minScore,
                maxScore,
                System.currentTimeMillis() - start);

        return results;
    }

    @Override
    @Transactional
    public boolean deleteBySource(String sourceId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vector_store WHERE metadata->>'source_id' = ?",
            Integer.class, sourceId);
        if (Objects.isNull(count) || count == 0) {
            return false;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression filter = b.eq("source_id", sourceId).build();
        vectorStore.delete(filter);
        log.info("deleteBySource: deleted {} chunks for sourceId='{}'", count, sourceId);
        return true;
    }

    @Override
    public List<DocumentSummary> listDocuments() {
        long start = System.currentTimeMillis();

        SearchRequest request = SearchRequest.builder()
                .query(" ")
                .topK(10_000)
                .similarityThreshold(0.0)
                .build();

        List<Document> allChunks = vectorStore.similaritySearch(request);

        // Group chunks by source_id to avoid collisions when two files share the same filename
        Map<String, List<Document>> bySourceId = allChunks.stream()
                .collect(Collectors.groupingBy(doc ->
                        String.valueOf(doc.getMetadata().getOrDefault("source_id", "unknown"))));

        List<DocumentSummary> summaries = bySourceId.entrySet().stream()
                .map(entry -> {
                    String groupSourceId = entry.getKey();
                    List<Document> chunks = entry.getValue();
                    // Use first chunk's metadata for document-level fields
                    Map<String, Object> meta = chunks.get(0).getMetadata();

                    int totalTokens = chunks.stream()
                            .mapToInt(d -> parseIntOrZero(d.getMetadata().get("tokenCount")))
                            .sum();

                    return new DocumentSummary(
                            nullableString(meta.get("filename")),
                            groupSourceId,
                            nullableString(meta.get("content-type")),
                            nullableString(meta.get("author")),
                            nullableString(meta.get("created-date")),
                            nullableString(meta.get("upload-timestamp")),
                            parseLongOrZero(meta.get("file-size-bytes")),
                            chunks.size(),
                            totalTokens
                    );
                })
                .sorted(Comparator.comparing(DocumentSummary::filename,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        log.info("listDocuments documents={} totalChunks={} latencyMs={}",
                summaries.size(), allChunks.size(), System.currentTimeMillis() - start);

        return summaries;
    }

    @Override
    public List<CrawlSiteSummary> listCrawledSites() {
        String sql = """
                SELECT
                    metadata->>'crawl-root-url' AS root_url,
                    COUNT(DISTINCT metadata->>'source_id') AS pages_ingested,
                    COUNT(*) AS total_chunks,
                    MAX(metadata->>'upload-timestamp') AS last_crawled_at
                FROM vector_store
                WHERE metadata->>'crawl-root-url' IS NOT NULL
                GROUP BY metadata->>'crawl-root-url'
                ORDER BY last_crawled_at DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CrawlSiteSummary(
                rs.getString("root_url"),
                rs.getInt("pages_ingested"),
                rs.getInt("total_chunks"),
                rs.getString("last_crawled_at")
        ));
    }

    @Override
    @Transactional
    public boolean deleteByCrawlRoot(String rootUrl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'crawl-root-url' = ?",
                Integer.class, rootUrl);
        if (Objects.isNull(count) || count == 0) {
            return false;
        }
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'crawl-root-url' = ?", rootUrl);
        log.info("deleteByCrawlRoot: deleted {} chunks for rootUrl='{}'", count, rootUrl);
        return true;
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (Objects.isNull(metadataJson) || metadataJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse metadata JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private Document toDocument(EmbeddedChunk embedded) {
        DocumentChunk chunk = embedded.chunk();
        Map<String, Object> meta = new HashMap<>(chunk.metadata());
        meta.put("chunkId", chunk.chunkId());
        meta.put("chunkIndex", String.valueOf(chunk.chunkIndex()));
        meta.put("tokenCount", String.valueOf(chunk.tokenCount()));

        // PgVectorStore requires a UUID; derive one deterministically from the SHA-256 chunkId
        String documentId = UUID.nameUUIDFromBytes(
                chunk.chunkId().getBytes(StandardCharsets.UTF_8)).toString();

        return Document.builder()
                .id(documentId)
                .text(chunk.content())
                .metadata(meta)
                .build();
    }

    private int parseIntOrZero(Object value) {
        if (Objects.isNull(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLongOrZero(Object value) {
        if (Objects.isNull(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String nullableString(Object value) {
        return Objects.nonNull(value) ? String.valueOf(value) : null;
    }
}
