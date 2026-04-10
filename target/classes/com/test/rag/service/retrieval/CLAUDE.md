# retrieval — RetrievalService
**Spec:** `docs/specs/retrieval-service.md`

## Responsibility
Orchestrate the query pipeline: embed the user query, search the vector store,
and return the top-k most relevant `ScoredChunk` objects.

## Inputs / Outputs
    In      String userQuery                         raw user question
    Out     List<ScoredChunk>                        ranked by similarity score, descending

## Rules
- Always call `QueryEmbeddingService.embed()` first, then `VectorStoreService.search()`.
- Read `topK` and `minSimilarity` exclusively from `RagProperties` — no hardcoded values.
  - `ragProperties.getTopK()`          default 5
  - `ragProperties.getMinSimilarity()` default 0.75
- If `ragProperties.isUseMmr()` is true, apply Maximal Marginal Relevance re-ranking for diversity.
- **Log:** query text, number of results returned, individual similarity scores.
- **Do not** call `EmbeddingService` (ingestion) — only `QueryEmbeddingService` (query-time).
- **Do not** import `VectorStore` directly — use `VectorStoreService` only.

## Dependencies
- `QueryEmbeddingService` from `com.test.rag.service.queryembedding`
- `VectorStoreService` from `com.test.rag.service.vectorstore`
- `RagProperties` from `com.test.rag.config`
- `ScoredChunk` from `com.test.rag.model`
