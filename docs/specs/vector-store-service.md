# Spec: VectorStoreService

## Responsibility
Single gateway for all PGVector reads and writes. Stores embedded chunks,
performs cosine similarity search, and supports deletion by source document.
All other services must go through this class — no direct DB access elsewhere.

---

## Interface
```java
public interface VectorStoreService {
    void upsert(List<EmbeddedChunk> chunks);
    List<ScoredChunk> search(float[] queryEmbedding, int topK, double threshold);
    void deleteBySource(String filename);
}
```

## Input / Output
```java
// upsert input: List<EmbeddedChunk> (from EmbeddingService)

// search output:
public record ScoredChunk(
    DocumentChunk chunk,             // original chunk with metadata
    double similarityScore           // cosine similarity 0.0–1.0
)
```

---

## Behaviour

### upsert()
- Map `EmbeddedChunk` → Spring AI `Document` (content + metadata + embedding)
- Use `VectorStore.add(List<Document>)` for batch insert
- On `chunkId` conflict → update existing record (upsert semantics)
- Log: number of chunks upserted, latency

### search()
- Use `VectorStore.similaritySearch(SearchRequest)` with cosine distance
- Filter results: `similarityScore >= threshold`
- Return at most `topK` results ordered by score descending
- Log: top-k requested, results returned, score range

### deleteBySource()
- Delete all chunks where `metadata.filename == filename`
- Used before re-ingesting an updated document
- Log: filename, count of deleted chunks

---

## Constraints
- Only class in the codebase that imports `VectorStore`
- No raw SQL, no `JdbcTemplate`, no `@Query` anywhere in this class
- `VectorStore` bean injected via constructor — never field injection
- Must use Spring AI `SearchRequest` builder for all queries

## PGVector Config (applied via application.properties)
```properties
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.dimensions=1024
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.index-type=HNSW
```

---

## Error Handling
| Scenario | Behaviour |
|---|---|
| DB connection failure | Let Spring exception propagate — no swallowing |
| Empty list to upsert | Return immediately, no DB call |
| Search with no results | Return empty list (not null) |
| deleteBySource — file not found | Log warning, no exception |

---

## Test Cases (requires live PGVector via Docker)
- Upsert 10 chunks → `check_pgvector` tool confirms 10 rows
- Search with matching query embedding → returns ≤ topK results
- All returned scores ≥ threshold
- `deleteBySource("test.pdf")` → confirm rows removed
- Upsert same chunkId twice → assert row count unchanged (upsert not duplicate)
- Search on empty table → returns empty list, no exception