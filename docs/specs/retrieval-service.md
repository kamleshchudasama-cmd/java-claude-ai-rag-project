# Spec — RetrievalService

**Package:** `com.test.rag.service.retrieval`

## Responsibility
Orchestrate query embedding → vector search → return top-k scored chunks.

## Inputs / Outputs
    In      String userQuery
    Out     List<ScoredChunk>
                chunk               DocumentChunk
                similarityScore     double

## Behaviour
- Call `QueryEmbeddingService.embed()` first, then `VectorStoreService.search()`
- top-k: 5 (from `RagProperties.topK`)
- min similarity: 0.75 (from `RagProperties.minSimilarity`)
- If `RagProperties.useMmr` is true, apply Maximal Marginal Relevance re-ranking for diversity
- Log: query text, number of results, individual similarity scores

## Do Not
- Call `EmbeddingService` (ingestion path) — use `QueryEmbeddingService` only
- Import `VectorStore` directly — go through `VectorStoreService`
- Hardcode top-k or similarity threshold — always read from `RagProperties`
