# embedding — EmbeddingService
**Spec:** `docs/specs/embedding-service.md`

## Responsibility
Embed a batch of `DocumentChunk` objects into float vectors using the OpenAI
`text-embedding-3-small` model via Spring AI's `EmbeddingModel` bean.

## Inputs / Outputs
    In      List<DocumentChunk>                      chunks from ChunkingService
    Out     List<EmbeddedChunk>                      each chunk paired with its normalised float[]

## Rules
- **Never** call the OpenAI HTTP API directly — always use the injected `EmbeddingModel` bean.
- Split input into batches of `ragProperties.getEmbeddingBatchSize()` (default 32).
- Normalise every vector to unit length before returning.
- Annotate `embed()` with `@Retryable` — let Spring Retry handle transient API failures.
- **Log per batch:** model name, input token count, latency (ms).
- On non-retryable failure throw `EmbeddingException` (unchecked).

## Dependencies
- `EmbeddingModel` bean (auto-configured by `spring-ai-starter-model-openai`)
- `RagProperties` from `com.test.rag.config`
- `DocumentChunk`, `EmbeddedChunk` from `com.test.rag.model`
- `EmbeddingException` from `com.test.rag.exception`
