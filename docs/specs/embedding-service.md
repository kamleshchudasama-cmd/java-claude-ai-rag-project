# Spec: EmbeddingService

## Responsibility
Embed a batch of `DocumentChunk` objects into float vectors using the
Anthropic Embeddings API via Spring AI's `EmbeddingClient`. Produces
`EmbeddedChunk` objects ready for PGVector upsert.

---

## Interface
```java
public interface EmbeddingService {
    List<EmbeddedChunk> embed(List<DocumentChunk> chunks);
}
```

## Input / Output
```java
// Input: List<DocumentChunk> (from ChunkingService)

// Output:
public record EmbeddedChunk(
    DocumentChunk chunk,             // original chunk preserved
    float[] embedding                // normalized float vector (1024 dims)
)
```

---

## Behaviour
- Use Spring AI `EmbeddingClient` bean — never call Anthropic HTTP API directly
- Process in batches of `rag.embedding.batch-size` (default 32)
- Normalize each vector (L2 norm) before returning
- Retry on API failure: `@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))`
- Log per batch: model name, token count, latency
- Throw `EmbeddingException` (unchecked) after retries exhausted

## Defaults (always from RagProperties)
| Parameter | Default | Property Key |
|---|---|---|
| Batch size | 32 | `rag.embedding.batch-size` |
| Model | voyage-3 | `spring.ai.anthropic.embedding.model` |
| Dimensions | 1024 | `spring.ai.anthropic.embedding.dimensions` |

---

## Constraints
- Must use `EmbeddingClient` Spring AI bean — no raw HTTP
- Must normalize vectors — PGVector cosine search assumes unit vectors
- Must preserve original `DocumentChunk` in output — do not discard metadata
- Batch size must come from `RagProperties`

---

## Error Handling
| Scenario | Behaviour |
|---|---|
| API rate limit / 429 | `@Retryable` handles with backoff |
| API failure after retries | Throw `EmbeddingException` wrapping cause |
| Empty input list | Return empty list immediately (no API call) |

---

## Test Cases
- Mock `EmbeddingClient` → assert output count matches input count
- Assert all vectors are normalized (L2 norm ≈ 1.0)
- Assert original chunk metadata preserved in `EmbeddedChunk`
- 100 chunks with batch-size=32 → assert 4 batched API calls made
- `EmbeddingClient` throws → assert `EmbeddingException` raised after retries
- Empty list input → assert zero API calls made