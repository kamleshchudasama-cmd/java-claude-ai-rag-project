# vectorstore — VectorStoreService
**Spec:** `docs/specs/vector-store-service.md`

## Responsibility
Single gateway for all PGVector reads and writes via Spring AI's `VectorStore` abstraction.
This is the **only** class in the codebase that may import `VectorStore` or execute SQL.

## Methods
    upsert(List<EmbeddedChunk>)
        In      List<EmbeddedChunk>
        Out     void — insert or update on chunkId conflict

    search(float[] queryEmbedding, int topK, double threshold)
        In      float[] queryEmbedding, int topK, double threshold
        Out     List<ScoredChunk>

    deleteBySource(String filename)
        In      String filename
        Out     void — remove all chunks for a document

## Rules
- Upsert must be idempotent on `chunkId` — re-ingesting a file must not create duplicates.
- `search` must filter results to `similarity >= threshold` before returning.
- **Do not** import `VectorStore`, `JdbcTemplate`, or any datasource outside this class.
- **Do not** call embedding or generation services — this class is pure storage I/O.
- Index: HNSW    Distance: COSINE_DISTANCE    Dimensions: 1024 (configured in `application.properties`).

## Dependencies
- `VectorStore` bean (auto-configured by `spring-ai-starter-vector-store-pgvector`)
- `EmbeddedChunk`, `ScoredChunk` from `com.test.rag.model`
