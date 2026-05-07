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
│   ├── SpringAiConfig.java               # ChatClient + EmbeddingModel bean wiring
│   └── RagProperties.java                # @ConfigurationProperties — all RAG tuning params
├── controller/
│   ├── RagController.java                # REST endpoints (see REST API below)
│   └── GlobalExceptionHandler.java       # @RestControllerAdvice — catches RuntimeException → HTTP 500
├── exception/
│   ├── DocumentParseException.java
│   ├── ChunkingException.java
│   └── EmbeddingException.java
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
    │   └── OpenAiEmbeddingService.java     # batch=32, L2-normalized, @Retryable(3×, 1 s delay)
    ├── vectorstore/
    │   ├── VectorStoreService.java
    │   └── PgVectorStoreService.java       # upsert / search / deleteBySource(@Transactional) / listDocuments
    ├── queryembedding/
    │   ├── QueryEmbeddingService.java
    │   └── OpenAiQueryEmbeddingService.java  # single-query embed, L2-normalized
    ├── retrieval/
    │   ├── RetrievalService.java
    │   └── VectorRetrievalService.java     # calls QueryEmbeddingService → VectorStoreService.search()
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
| `minSimilarity` | 0.75 (class) / 0.7 (application.properties) | Cosine similarity threshold (BigDecimal) |
| `useMmr` | false | Enable Maximal Marginal Relevance re-ranking |
| `temperature` | 0.2 | LLM temperature (BigDecimal) |
| `maxOutputTokens` | 2048 | LLM max response length |
| `maxContextTokens` | 4096 | Max tokens assembled into prompt context |
| `maxContentChars` | 500,000 | Max document size accepted by loader |

---

## Retry Policies
- **EmbeddingService**: `@Retryable`, maxAttempts=3, delay=1 000 ms (HTTP 429 / IOException)
- **GenerationService**: `@Retryable`, maxAttempts=3, delay=10 000 ms (HTTP 429 / IOException)
- Requires `spring-retry` + `spring-boot-starter-aop` on the classpath.

---

## Service Contracts (invariants — do not change without updating this file)

1. **Never call the OpenAI API directly — always go through Spring AI abstraction.**
   Use `ChatClient` bean for generation, `EmbeddingModel` bean for embeddings.

2. `VectorStoreService` is the single point of contact with PGVector.
   No other service imports `VectorStore` or runs SQL directly.

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

## Deployment
- `docker-compose.yml` — starts PGVector (port 5433); preferred for local dev
- `Dockerfile` — builds the application image for containerized deployment

---

## Architecture Reference
`RAG-ARCHITECTURE.md` at the project root contains detailed data-flow diagrams and
sequence diagrams for both ingestion and query pipelines. Consult it before making
structural changes to the pipeline.

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

## What NOT to Do
- Do not bypass Spring AI to call OpenAI APIs via raw HTTP
- Do not put chunking params as magic numbers in service classes — use `RagProperties`
- Do not run PGVector SQL outside `VectorStoreService`
- Do not change embedding dimensions without reindexing the vector table
- Do not add blocking calls inside reactive/async chains
- Do not catch `Exception` directly — use specific types (`DocumentParseException`, `ChunkingException`, `EmbeddingException`)
- Do not apply `@Transactional` on private methods
- Do not hardcode environment config — use `application.properties` / environment variables
