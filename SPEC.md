# RAG Service Component Spec

---

## 1. DocumentLoaderService
**Responsibility:** Accept a file path or `MultipartFile`, parse raw content via
Apache Tika, return a `ParsedDocument` (text + metadata).

**Inputs:** `Path filePath` or `MultipartFile`
**Outputs:** `ParsedDocument { String content, Map<String,String> metadata }`

**Behaviour:**
- Support PDF, DOCX, HTML, TXT via `AutoDetectParser`
- Extract metadata: filename, content-type, author, created-date
- Throw `DocumentParseException` (unchecked) on parse failure
- Strip boilerplate (headers/footers) via Tika's `BodyContentHandler`

**Do not:** perform chunking — that is `ChunkingService`'s job.

---

## 2. ChunkingService
**Responsibility:** Split a `ParsedDocument` into overlapping `DocumentChunk`
objects ready for embedding.

**Inputs:** `ParsedDocument`
**Outputs:** `List<DocumentChunk>`

**Behaviour:**
- Recursive split: paragraph → sentence → word boundary
- Default: 512 tokens, 50 token overlap (from `RagProperties`)
- Discard chunks under 100 tokens
- Preserve source metadata on every chunk (filename, page hint if available)
- Assign a deterministic `chunkId` = `SHA-256(sourceFile + chunkIndex)`

**Do not:** call any external API — pure text processing only.

---

## 3. EmbeddingService
**Responsibility:** Embed a batch of `DocumentChunk` objects using Anthropic
Embeddings API via Spring AI's `EmbeddingClient`.

**Inputs:** `List<DocumentChunk>`
**Outputs:** `List<EmbeddedChunk>` (chunk + float[] embedding)

**Behaviour:**
- Batch calls to respect API rate limits (configurable batch size in `RagProperties`)
- Normalize vectors before returning
- Log: model name, input token count, latency per batch
- Throw `EmbeddingException` on API failure with retry via `@Retryable`

**Do not:** call Anthropic HTTP API directly — use `EmbeddingClient` bean only.

---

## 4. VectorStoreService
**Responsibility:** Single gateway for all PGVector reads and writes via Spring
AI's `VectorStore` abstraction.

**Inputs (write):** `List<EmbeddedChunk>`
**Inputs (read):** `float[] queryEmbedding`, `int topK`, `double threshold`
**Outputs (read):** `List<ScoredChunk>`

**Behaviour:**
- Upsert chunks (insert or update on `chunkId` conflict)
- Read: cosine similarity search, filter by `similarity >= threshold`
- Expose `deleteBySource(String filename)` for re-ingestion
- No raw SQL anywhere else in the codebase — this class owns the DB

**Do not:** import `VectorStore` or `JdbcTemplate` outside this service.

---

## 5. QueryEmbeddingService
**Responsibility:** Embed a single user query string for retrieval.

**Inputs:** `String userQuery`
**Outputs:** `float[] queryEmbedding`

**Behaviour:**
- Thin wrapper over `EmbeddingClient` (same bean as `EmbeddingService`)
- Normalize output vector
- Log query text (truncated to 200 chars), latency

**Rationale:** Kept separate from `EmbeddingService` so query-time telemetry
and caching (future) are isolated from ingestion-time concerns.

---

## 6. RetrievalService
**Responsibility:** Orchestrate query embedding → vector search → return top-k
scored chunks.

**Inputs:** `String userQuery`
**Outputs:** `List<ScoredChunk>` (chunk + similarity score)

**Behaviour:**
- Call `QueryEmbeddingService` then `VectorStoreService`
- Default top-k = 5, min similarity = 0.75 (from `RagProperties`)
- Log: query, number of results, similarity scores
- Optionally apply MMR (Maximal Marginal Relevance) for diversity (flag in props)

---

## 7. ContextBuilderService
**Responsibility:** Assemble retrieved chunks into a grounded prompt string
ready for `GenerationService`.

**Inputs:** `String userQuery`, `List<ScoredChunk>`
**Outputs:** `BuiltContext { String systemPrompt, String userMessage, List<Citation> citations }`

**Behaviour:**
- Use prompt template from `resources/prompts/rag-system.st` (Spring AI StringTemplate)
- Inject chunks as numbered `[1]...[N]` references in the context block
- Build `Citation` list: `{ int ref, String filename, int chunkIndex, double score }`
- Truncate context to stay within token budget (configurable in `RagProperties`)

**Do not:** call any AI API — pure string assembly.

---

## 8. GenerationService
**Responsibility:** Call Gemini-2.5-flash via Spring AI `ChatClient` with the
built context, return a `RagResponse` with answer + citations.

**Inputs:** `BuiltContext`
**Outputs:** `RagResponse { String answer, List<Citation> citations, int totalTokens }`

**Behaviour:**
- Use `ChatClient` bean (Spring AI) — never raw Google SDK
- Set temperature, max tokens from `RagProperties`
- Parse model reply to extract inline citation markers `[1]`..`[N]`
- Map markers back to `Citation` objects from `BuiltContext`
- Log: total prompt tokens, completion tokens, latency

**Do not:** call Gemini API directly — always through Spring AI abstraction.