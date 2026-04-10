# queryembedding — QueryEmbeddingService
**Spec:** `docs/specs/query-embedding-service.md`

## Responsibility
Embed a single user query string into a float vector for retrieval.
Thin wrapper over `EmbeddingModel` — deliberately separate from `EmbeddingService`
so query-time telemetry and future caching are isolated from ingestion concerns.

## Inputs / Outputs
    In      String userQuery                         raw user question
    Out     float[]                                  normalised query embedding

## Rules
- Use the same `EmbeddingModel` bean as `EmbeddingService` (same Spring singleton).
- Normalise the output vector to unit length.
- **Log:** query text truncated to 200 chars, latency (ms).
- **Do not** batch — this is always a single string at query time.
- **Do not** call `EmbeddingService` — they share the bean but are independent services.

## Dependencies
- `EmbeddingModel` bean (auto-configured by `spring-ai-starter-model-anthropic`)
