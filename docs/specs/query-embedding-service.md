# Spec — QueryEmbeddingService

**Package:** `com.test.rag.service.queryembedding`

## Responsibility
Embed a single user query string into a float vector for retrieval.
Thin wrapper over `EmbeddingModel` — kept separate from `EmbeddingService` so
query-time telemetry and future caching are isolated from ingestion-time concerns.

## Inputs / Outputs
    In      String userQuery
    Out     float[]           normalised query embedding

## Behaviour
- Use the same `EmbeddingModel` bean as `EmbeddingService` (same Spring singleton)
- Normalise output vector to unit length
- Log: query text truncated to 200 chars, latency (ms)

## Do Not
- Batch calls — this is always a single string at query time
- Call `EmbeddingService` directly — they share the bean but are independent services
