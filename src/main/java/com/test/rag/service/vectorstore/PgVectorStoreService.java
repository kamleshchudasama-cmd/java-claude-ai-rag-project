package com.test.rag.service.vectorstore;

import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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

        double minScore = results.stream().mapToDouble(ScoredChunk::similarityScore).min().orElse(0.0);
        double maxScore = results.stream().mapToDouble(ScoredChunk::similarityScore).max().orElse(0.0);
        log.info("Search topK={} returned={} scoreRange=[{},{}] latencyMs={}",
                topK, results.size(),
                String.format("%.3f", minScore),
                String.format("%.3f", maxScore),
                System.currentTimeMillis() - start);

        return results;
    }

    @Override
    public void deleteBySource(String filename) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
                .query(" ")
                .topK(10_000)
                .similarityThreshold(0.0)
                .filterExpression(b.eq("filename", filename).build())
                .build();

        List<Document> found = vectorStore.similaritySearch(request);
        if (found.isEmpty()) {
            log.warn("deleteBySource: no chunks found for filename='{}'", filename);
            return;
        }

        List<String> ids = found.stream().map(Document::getId).toList();
        vectorStore.delete(ids);
        log.info("deleteBySource: deleted chunks={} filename='{}'", ids.size(), filename);
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
        double score   = Objects.nonNull(doc.getScore()) ? doc.getScore() : 0.0;

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
}
