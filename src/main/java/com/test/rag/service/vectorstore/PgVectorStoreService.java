package com.test.rag.service.vectorstore;

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

    public PgVectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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
    public List<ScoredChunk> search(String query, int topK, double threshold) {
        long start = System.currentTimeMillis();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);
        List<ScoredChunk> results = docs.stream().map(this::toScoredChunk).toList();

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
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression filter = b.eq("source_id", sourceId).build();
        vectorStore.delete(filter);
        log.info("deleteBySource: issued filter-based delete for sourceId='{}'", sourceId);
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

    private ScoredChunk toScoredChunk(Document doc) {
        Map<String, String> meta = doc.getMetadata().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())
                ));

        String chunkId = (String) doc.getMetadata().getOrDefault("chunkId", doc.getId());
        int chunkIndex = parseIntOrZero(doc.getMetadata().get("chunkIndex"));
        int tokenCount = parseIntOrZero(doc.getMetadata().get("tokenCount"));
        BigDecimal score = Objects.nonNull(doc.getScore()) ? BigDecimal.valueOf(doc.getScore()) : BigDecimal.ZERO;

        DocumentChunk chunk = new DocumentChunk(chunkId, doc.getText(), chunkIndex, tokenCount, meta);
        return new ScoredChunk(chunk, score);
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
