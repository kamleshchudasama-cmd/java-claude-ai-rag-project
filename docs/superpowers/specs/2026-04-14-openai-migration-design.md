# OpenAI Migration Design
**Date:** 2026-04-14
**Status:** Approved

## Goal
Replace Voyage AI (embeddings) and Gemini (generation) with OpenAI's `text-embedding-3-small` and `gpt-4o-mini`, using Spring AI abstractions throughout. No pom.xml changes required — `spring-ai-starter-model-openai` is already present.

---

## 1. Configuration

### `application.properties`
- Remove Gemini base-url override (`spring.ai.openai.base-url`, `spring.ai.openai.chat.completions-path`)
- Set `spring.ai.openai.api-key=${OPENAI_API_KEY}`
- Set `spring.ai.openai.chat.options.model=gpt-4o-mini`
- Add `spring.ai.openai.embedding.options.model=text-embedding-3-small`
- Update `spring.ai.vectorstore.pgvector.dimensions=1536` (was 1024)
- Reset `rag.embedding-request-delay-ms=0` (Voyage rate-limit workaround — no longer needed)
- Reset `rag.embedding-batch-size=32` (was 8 due to Voyage 3 RPM limit)

### Environment variables
| Remove | Add |
|---|---|
| `GOOGLE_API_KEY` | `OPENAI_API_KEY` |
| `VOYAGE_API_KEY` | — |

### `docker-compose.yml`
Remove `GOOGLE_API_KEY` and `VOYAGE_API_KEY`; add `OPENAI_API_KEY`.

---

## 2. Code Changes

### Delete
- `src/main/java/com/test/rag/config/VoyageEmbeddingModel.java`
  - Custom `@Primary EmbeddingModel` that called Voyage AI over raw HTTP
  - Spring AI's auto-configured `OpenAiEmbeddingModel` takes over as the `EmbeddingModel` bean

### Rename (class name + logger only, no logic changes)
| Old | New |
|---|---|
| `AnthropicEmbeddingService.java` | `OpenAiEmbeddingService.java` |
| `AnthropicQueryEmbeddingService.java` | `OpenAiQueryEmbeddingService.java` |
| `GeminiGenerationService.java` | `OpenAiGenerationService.java` |

### `OpenAiGenerationService` retry update
```java
@Retryable(
    retryFor = HttpClientErrorException.TooManyRequests.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 10_000)
)
```
(Was: maxAttempts=4, delay=31_000, multiplier=1.5 — Gemini-specific values)

### `SpringAiConfig.java`
No changes — `ChatClient` builder is provider-agnostic.

---

## 3. PGVector Re-index

Embedding dimensions change **1024 → 1536**. The `vector_store` table schema must be rebuilt:

1. Drop the existing table before restart (or set `spring.ai.vectorstore.pgvector.initialize-schema=true` and drop manually)
2. On next startup Spring AI recreates the table with 1536 dims
3. Re-ingest all previously ingested documents

---

## 4. CLAUDE.md Updates
- Stack table: replace Anthropic Embeddings / Voyage-3 / Gemini-2.5-flash with OpenAI / text-embedding-3-small / gpt-4o-mini
- Service contracts: update EmbeddingModel and ChatClient references to OpenAI
- Module map: update service class names

---

## 5. What Does NOT Change
- `pom.xml` — no dependency changes
- `PgVectorStoreService` — provider-agnostic, no changes
- `VectorRetrievalService` — no changes
- `ContextBuilderService` / `PromptContextBuilderService` — no changes
- `RagController` — no changes
- Chunking pipeline — no changes
- `RagProperties` — no changes
- Retry on `AnthropicEmbeddingService` (renamed) stays: `maxAttempts=3, delay=1000`
