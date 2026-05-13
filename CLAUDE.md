# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview — Java RAG System
A Retrieval-Augmented Generation pipeline built on Spring Boot + Spring AI.
Ingests multi-format documents (PDF, DOCX, HTML) via Apache Tika, embeds chunks
using OpenAI Embeddings API, stores vectors in PGVector, retrieves via cosine
similarity, and generates cited answers via gpt-4o-mini through Spring AI.

---

## Stack & Versions
    Framework           Spring Boot                  3.3.4
    AI Abstraction      Spring AI                    1.0.0
    Vector Store        PGVector on PostgreSQL        pg 16, pgvector 0.7.x
    Document Parsing    Apache Tika                  2.9.2
    Embeddings          OpenAI text-embedding-3-small  via Spring AI (1536 dims)
    Generation          OpenAI gpt-4o-mini             via Spring AI
    Build               Maven                        3.9.x
    Language            Java                         21

---

## Maven Build Commands
```bash
mvn clean install                         # full build
mvn spring-boot:run                       # run locally
mvn test                                  # all tests
mvn test -Dtest=ClassName                 # single test class
mvn test -Dtest=ClassName#methodName      # single test method
mvn dependency:tree                       # inspect dependency graph
mvn versions:display-dependency-updates   # check for updates
```

---

## PGVector Setup
Preferred: use `docker-compose.yml` (port **5433** on host):
```bash
docker compose up -d
```

Manual (also port 5433):
```bash
docker run -d \
  --name pgvector \
  -e POSTGRES_USER=rag \
  -e POSTGRES_PASSWORD=rag \
  -e POSTGRES_DB=ragdb \
  -p 5433:5432 \
  pgvector/pgvector:pg16
```

Spring AI auto-creates the `vector_store` table on startup when
`spring.ai.vectorstore.pgvector.initialize-schema=true`.

---

## Environment Variables (never hardcode values)
```bash
OPENAI_API_KEY=...
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ragdb
SPRING_DATASOURCE_USERNAME=rag
SPRING_DATASOURCE_PASSWORD=rag
```
Referenced in `application.properties` as `${OPENAI_API_KEY}`, etc.

---

## Spring AI Config Keys (application.properties)
```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.embedding.options.model=text-embedding-3-small
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.dimensions=1536
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

---

## Module Map
Root package: `com.test.rag`

```
src/main/java/com/test/rag/
├── config/
│   ├── SpringAiConfig.java               # explicit beans: ChatClient, AutoDetectParser (EmbeddingModel + VectorStore auto-configured by starters)
│   ├── RagProperties.java                # @ConfigurationProperties — all RAG tuning params
│   └── WebConfig.java                    # CORS — allows http://localhost:4200 on /api/**
├── controller/
│   ├── RagController.java                # REST endpoints (see REST API below)
│   └── GlobalExceptionHandler.java       # @RestControllerAdvice — catches RuntimeException → HTTP 500
├── exception/
│   ├── DocumentParseException.java
│   ├── ChunkingException.java
│   ├── EmbeddingException.java
│   └── GenerationException.java
├── model/                                # all are Java records (immutable)
│   ├── ParsedDocument.java               # content, metadata Map, sourceId (SHA-256)
│   ├── DocumentChunk.java                # chunkId (SHA-256), content, index, tokens, metadata
│   ├── EmbeddedChunk.java                # chunk + float[] embedding (normalized)
│   ├── ScoredChunk.java                  # chunk + double similarityScore
│   ├── BuiltContext.java                 # systemPrompt, userMessage, List<Citation>
│   ├── Citation.java                     # ref [N], filename, chunkIndex, score, chunkText
│   ├── RagResponse.java                  # answer, citations, totalTokens
│   └── DocumentSummary.java              # aggregated document metadata for listing
└── service/                              # each sub-package has an interface + implementation
    ├── loader/
    │   ├── DocumentLoaderService.java
    │   └── TikaDocumentLoaderService.java  # load(Path) + load(MultipartFile); sourceId = SHA-256(name+size)
    ├── chunking/
    │   ├── ChunkingService.java
    │   └── RecursiveChunkingService.java   # paragraph → sentence → word; chunkId = SHA-256(sourceId+index)
    ├── embedding/
    │   ├── EmbeddingService.java
    │   ├── OpenAiEmbeddingService.java     # splits chunks into batches, delegates to EmbeddingBatchProcessor
    │   └── EmbeddingBatchProcessor.java    # @Service — holds @Retryable(3×, 1 s delay); L2-normalizes vectors
    ├── vectorstore/
    │   ├── VectorStoreService.java
    │   └── PgVectorStoreService.java       # upsert / search / deleteBySource(@Transactional) / listDocuments
    ├── queryembedding/
    │   ├── QueryEmbeddingService.java
    │   └── OpenAiQueryEmbeddingService.java  # single-query embed, L2-normalized
    ├── retrieval/
    │   ├── RetrievalService.java
    │   └── VectorRetrievalService.java     # calls QueryEmbeddingService → VectorStoreService.search(); honours retrievalEnabled kill-switch
    ├── context/
    │   ├── ContextBuilderService.java
    │   └── PromptContextBuilderService.java  # loads prompts/rag-system.st, sorts by score, truncates to maxContextTokens
    └── generation/
        ├── GenerationService.java
        └── OpenAiGenerationService.java    # ChatClient call, parses inline [N] citations, @Retryable(3×, 10 s delay)
```

Each `service/<sub-package>/` directory contains a `CLAUDE.md` with the contract,
invariants, and expected test cases for that specific service.

---

## Pipeline Flows

**Ingestion** (`POST /api/rag/ingest`):
```
MultipartFile
  → TikaDocumentLoaderService.load()        → ParsedDocument
  → RecursiveChunkingService.chunk()        → List<DocumentChunk>
  → OpenAiEmbeddingService.embed()          → List<EmbeddedChunk>
  → PgVectorStoreService.upsert()           → PGVector (idempotent via chunkId)
```

**Query** (`POST /api/rag/query?q=<question>`):
```
String question
  → OpenAiQueryEmbeddingService.embed()     → float[]
  → PgVectorStoreService.search()           → List<ScoredChunk>
  → PromptContextBuilderService.build()     → BuiltContext (numbered citations)
  → OpenAiGenerationService.generate()      → RagResponse (answer + citations)
```

---

## REST API
```
POST   /api/rag/ingest              # multipart/form-data, field: file (≤50 MB)
POST   /api/rag/query?q=<question>  # query param "q" → RagResponse JSON
GET    /api/rag/documents           # → List<DocumentSummary>
DELETE /api/rag/documents/{id}      # id = sourceId (SHA-256 hex); 204 or 404
```

---

## RagProperties — All Tunable Parameters
All values live in `RagProperties.java` and are bound from `application.properties`
under the `rag.*` prefix. **Never hardcode these in service classes.**

| Property | Default | Purpose |
|---|---|---|
| `chunkSize` | 512 | Max tokens per chunk |
| `chunkOverlap` | 50 | Token overlap between consecutive chunks |
| `minChunkSize` | 50 | Discard chunks smaller than this |
| `embeddingBatchSize` | 32 | Chunks per OpenAI embedding request |
| `embeddingRequestDelayMs` | 0 | Delay between embedding batches (rate-limit buffer) |
| `retrievalEnabled` | true | Kill-switch for retrieval step |
| `topK` | 5 | Chunks returned from vector search |
| `minSimilarity` | 0.75 (class default) / 0.3 (application.properties) | Cosine similarity threshold (BigDecimal) |
| `useMmr` | false | Enable Maximal Marginal Relevance re-ranking |
| `temperature` | 0.2 | LLM temperature (BigDecimal) |
| `maxOutputTokens` | 2048 | LLM max response length |
| `maxContextTokens` | 4096 | Max tokens assembled into prompt context |
| `maxContentChars` | 500,000 | Max document size accepted by loader |

---

## Retry Policies
- **`EmbeddingBatchProcessor.embedBatch()`**: `@Retryable`, maxAttempts=3, delay=1 000 ms (`TooManyRequests` / `ResourceAccessException`)
- **`OpenAiGenerationService.generate()`**: `@Retryable`, maxAttempts=3, delay=10 000 ms (`TooManyRequests` / `ResourceAccessException`)
- Requires `spring-retry` + `spring-boot-starter-aop` on the classpath.
- `@Retryable` must be on a **Spring-managed bean method called via proxy** — it will not intercept calls within the same class.

---

## Service Contracts (invariants — do not change without updating this file)

1. **Never call the OpenAI API directly — always go through Spring AI abstraction.**
   Use `ChatClient` bean for generation, `EmbeddingModel` bean for embeddings.

2. `VectorStoreService` is the single point of contact with PGVector.
   No other service imports `VectorStore`, `JdbcTemplate`, or runs SQL directly.
   `PgVectorStoreService` itself uses both `VectorStore` (add/delete) and `JdbcTemplate`
   for the cosine-similarity `search()` — Spring AI's `SearchRequest` does not expose
   per-row similarity scores, so raw SQL is required there.

3. `RagProperties` is the single source of truth for all tuneable params.
   Never use `@Value` for RAG params outside this class.

4. All embedding calls must log: model name, input token count, latency.

5. All retrieval calls must log: query text, top-k count, similarity scores.

6. `GenerationService` must include source document metadata in every response
   (`RagResponse.citations`).

---

## Key Dependencies
- `spring-ai-bom:1.0.0` (managed via `<dependencyManagement>`)
- `spring-ai-starter-model-openai` — ChatClient + EmbeddingModel
- `spring-ai-starter-vector-store-pgvector` — VectorStore (PGVector)
- `spring-retry` + `spring-boot-starter-aop` — required for `@Retryable` on service impls
- `tika-parsers-standard-package:2.9.2` — multi-format document parsing

`RagApplication` carries `@EnableRetry` — without it `@Retryable` annotations are silently ignored.

---

## Angular UI (`angular-ui/`)
Angular 18 + Angular Material SPA that talks to the Spring Boot backend.

**Dev commands** (run from `angular-ui/`):
```bash
npm install        # first-time setup
npm start          # dev server → http://localhost:4200
npm test           # Karma/Jasmine unit tests
npm run build      # production build → dist/
```

**Structure:**
```
angular-ui/src/app/
├── core/
│   ├── models.ts              # TypeScript mirrors of RagResponse, DocumentSummary, Citation
│   └── rag-api.service.ts     # single HTTP client for all four backend endpoints
├── features/
│   ├── query/                 # chat interface + ChatService (conversation state)
│   ├── ingest/                # file upload form
│   └── documents/             # document list + delete (DocumentsService)
└── shared/
    ├── citation-card/         # renders a single Citation
    └── confirm-dialog/        # reusable confirmation dialog (Angular Material)
```

Routes: `/query` (default), `/ingest`, `/documents` — all lazy-loaded standalone components.

`environment.development.ts` sets `apiBaseUrl` to `http://localhost:8080`. The backend's `WebConfig` allows CORS from `http://localhost:4200` only.

**Rules for Angular work:**
- Always use separate `.html` / `.ts` / `.scss` files — never inline templates or styles.
- `RagApiService` is the only class that may call `HttpClient` — feature components inject feature services, not `RagApiService` directly.

---

## Deployment
- `docker-compose.yml` — starts PGVector (port 5433); preferred for local dev
- `Dockerfile` — builds the application image for containerized deployment

---

## Architecture Reference
`RAG-ARCHITECTURE.md` and `SPEC.md` at the project root reflect the **original design**
(Gemini/Anthropic stack) and are outdated. The current implementation uses OpenAI
(gpt-4o-mini + text-embedding-3-small). Use **this file** as the authoritative reference;
the diagrams in `RAG-ARCHITECTURE.md` are useful for pipeline shape but provider/config
details there are stale.

---

## Rules
- Constructor injection only — no field or setter injection
- No Lombok
- All monetary/threshold values as `BigDecimal`, never `double` or `float`
- Always use `Objects.nonNull` / `Objects.isNull` for null checks
- All data model types are Java records — keep them immutable

## Specs
See `docs/specs/` — always check for an existing spec before implementing a feature.
Each service sub-package has a corresponding spec file there.

## Prompt Template
`src/main/resources/prompts/rag-system.st` — Spring AI StringTemplate injected with
`{context}` (numbered chunk references `[1]…[N]`). Edit here to change model instructions.

## Evaluation
`ragas-eval.py` + `src/main/resources/eval/eval-dataset.json` — offline RAGAS-based
evaluation harness. Run against a live stack to measure faithfulness and answer relevance.

## PGVector Metadata Key Conventions
The following keys are stored in the `vector_store` metadata column and referenced
in raw SQL queries and service logic. **Do not rename without migrating existing rows.**

| Key | Written by | Used in |
|---|---|---|
| `source_id` | `TikaDocumentLoaderService` | `deleteBySource` SQL (`metadata->>'source_id'`), `listDocuments` grouping |
| `filename` | `TikaDocumentLoaderService` | `listDocuments` display, citations |
| `chunkId` | `PgVectorStoreService.toDocument()` | search result re-hydration |
| `chunkIndex` | `PgVectorStoreService.toDocument()` | search result re-hydration |
| `tokenCount` | `PgVectorStoreService.toDocument()` | `listDocuments` token totals |
| `content-type`, `author`, `created-date`, `upload-timestamp`, `file-size-bytes` | `TikaDocumentLoaderService` | `listDocuments` display |

Note: `PgVectorStoreService.toDocument()` derives the PGVector UUID deterministically
via `UUID.nameUUIDFromBytes(chunkId.getBytes(UTF_8))` so upserts are idempotent.

`listDocuments()` fetches all chunks with topK=10 000 and groups in Java — not suitable
for corpora with tens of thousands of chunks.

---

## What NOT to Do
- Do not bypass Spring AI to call OpenAI APIs via raw HTTP
- Do not put chunking params as magic numbers in service classes — use `RagProperties`
- Do not run PGVector SQL outside `VectorStoreService`
- Do not change embedding dimensions without reindexing the vector table
- Do not rename PGVector metadata keys (`source_id`, `chunkId`, etc.) without migrating existing `vector_store` rows — they are referenced in raw SQL
- Do not add blocking calls inside reactive/async chains
- Do not catch `Exception` directly — use specific types (`DocumentParseException`, `ChunkingException`, `EmbeddingException`, `GenerationException`)
- Do not apply `@Transactional` on private methods
- Do not hardcode environment config — use `application.properties` / environment variables
