# RAG Architecture

## Overview

This system implements a **Retrieval-Augmented Generation (RAG)** pipeline on Spring Boot + Spring AI. It ingests documents, stores them as vector embeddings in PGVector, and answers natural-language queries by retrieving relevant chunks and generating cited answers via Gemini-2.5-flash.

---

## High-Level Flow

```
┌─────────────┐     ┌──────────────────────────────────────────────────────────┐
│             │     │                     INGESTION PIPELINE                    │
│  Document   │────▶│  DocumentLoader ──▶ Chunking ──▶ Embedding ──▶ VectorStore│
│ (PDF/DOCX/  │     └──────────────────────────────────────────────────────────┘
│  HTML/TXT)  │
└─────────────┘

┌─────────────┐     ┌──────────────────────────────────────────────────────────┐
│             │     │                      QUERY PIPELINE                       │
│  User Query │────▶│  QueryEmbed ──▶ Retrieval ──▶ ContextBuilder ──▶ Generation│
│             │     └──────────────────────────────────────────────────────────┘
└─────────────┘                                                        │
                                                                       ▼
                                                               RagResponse
                                                          (answer + citations)
```

---

## Component Map

```
com.test.rag/
├── controller/
│   └── RagController            REST API  (/api/rag/ingest, /api/rag/query)
├── service/
│   ├── DocumentLoaderService    Tika parsing → ParsedDocument
│   ├── ChunkingService          Recursive split → List<DocumentChunk>
│   ├── EmbeddingService         Batch embed chunks → List<EmbeddedChunk>
│   ├── VectorStoreService       PGVector read/write gateway
│   ├── QueryEmbeddingService    Embed user query → float[]
│   ├── RetrievalService         Query embed + vector search → List<ScoredChunk>
│   ├── ContextBuilderService    Assemble grounded prompt → BuiltContext
│   └── GenerationService        Gemini call → RagResponse
├── config/
│   ├── RagProperties            @ConfigurationProperties — all tunable params
│   └── SpringAiConfig           ChatClient bean wiring
├── model/
│   ├── ParsedDocument           content + metadata map
│   ├── DocumentChunk            chunkId + content + metadata
│   ├── EmbeddedChunk            chunk + float[] embedding
│   ├── ScoredChunk              chunk + similarity score
│   ├── BuiltContext             systemPrompt + userMessage + citations
│   ├── Citation                 ref + filename + chunkIndex + score
│   └── RagResponse              answer + citations + totalTokens
└── exception/
    ├── DocumentParseException   unchecked — thrown by DocumentLoaderService
    └── EmbeddingException       unchecked — thrown by EmbeddingService
```

---

## Ingestion Pipeline

```
POST /api/rag/ingest
        │
        ▼
DocumentLoaderService
  • Apache Tika AutoDetectParser
  • Supports: PDF, DOCX, HTML, TXT
  • Extracts: text body + metadata (filename, content-type, author, date)
  • Returns: ParsedDocument
        │
        ▼
ChunkingService
  • Strategy: recursive split  paragraph → sentence → word
  • Chunk size:   512 tokens  (RagProperties.chunkSize)
  • Overlap:       50 tokens  (RagProperties.chunkOverlap)
  • Min size:     100 tokens  (RagProperties.minChunkSize — discards smaller)
  • chunkId:      SHA-256(sourceFile + chunkIndex)  — deterministic, upsert-safe
  • Returns: List<DocumentChunk>
        │
        ▼
EmbeddingService
  • Model: voyage-3 via Spring AI EmbeddingModel bean
  • Batched calls  (RagProperties.embeddingBatchSize, default 32)
  • Normalises vectors before returning
  • @Retryable on API failure
  • Logs: model, input tokens, latency per batch
  • Returns: List<EmbeddedChunk>
        │
        ▼
VectorStoreService
  • Upserts to PGVector on chunkId conflict
  • Index: HNSW   Distance: COSINE_DISTANCE   Dimensions: 1024
  • Single owner of VectorStore bean — no other service touches the DB
```

---

## Query Pipeline

```
POST /api/rag/query?q=...
        │
        ▼
QueryEmbeddingService
  • Same EmbeddingModel bean as ingestion
  • Normalises output vector
  • Logs: query text (truncated 200 chars), latency
  • Returns: float[] queryEmbedding
        │
        ▼
RetrievalService
  • Calls QueryEmbeddingService → VectorStoreService.search()
  • top-k:          5     (RagProperties.topK)
  • min similarity: 0.75  (RagProperties.minSimilarity)
  • Optional MMR for diversity (RagProperties.useMmr)
  • Logs: query, result count, similarity scores
  • Returns: List<ScoredChunk>
        │
        ▼
ContextBuilderService
  • Loads prompt from resources/prompts/rag-system.st  (Spring AI StringTemplate)
  • Injects chunks as numbered [1]…[N] references
  • Builds Citation list: { ref, filename, chunkIndex, score }
  • Truncates to token budget  (RagProperties.maxContextTokens, default 4096)
  • Returns: BuiltContext { systemPrompt, userMessage, citations }
        │
        ▼
GenerationService
  • Spring AI ChatClient bean (Gemini-2.5-flash) — never raw Google SDK
  • temperature:       0.2   (RagProperties.temperature)
  • maxOutputTokens:  2048   (RagProperties.maxOutputTokens)
  • Parses inline [1]…[N] markers from model reply
  • Maps markers back to Citation objects from BuiltContext
  • Logs: prompt tokens, completion tokens, latency
  • Returns: RagResponse { answer, citations, totalTokens }
```

---

## Data Models

```
ParsedDocument          DocumentChunk              EmbeddedChunk
──────────────          ─────────────              ─────────────
content: String         chunkId: String            chunk: DocumentChunk
metadata: Map           content: String            embedding: float[]
                        chunkIndex: int
                        metadata: Map

ScoredChunk             BuiltContext                RagResponse
───────────             ───────────                ───────────
chunk: DocumentChunk    systemPrompt: String        answer: String
similarityScore: double userMessage: String         citations: List<Citation>
                        citations: List<Citation>   totalTokens: int

Citation
────────
ref: int
filename: String
chunkIndex: int
score: double
```

---

## Infrastructure
    Vector DB       PGVector on PostgreSQL 16            spring.ai.vectorstore.pgvector.*
    Embeddings      Anthropic voyage-3 (1024-dim)        spring.ai.anthropic.*
    Generation      Gemini-2.5-flash via Vertex AI       spring.ai.vertex.ai.gemini.*
    Doc parsing     Apache Tika 2.9.2                    (no Spring config key)
    Retry           Spring Retry @Retryable              @EnableRetry on RagApplication

---

## Key Invariants

1. **Never bypass Spring AI** — Gemini and Anthropic are always called through `ChatClient` / `EmbeddingModel` beans, never via raw HTTP or vendor SDK.
2. **`VectorStoreService` owns the DB** — `VectorStore` and any SQL access live here exclusively.
3. **`RagProperties` owns all params** — no `@Value` for RAG tuning params, no magic numbers in service classes.
4. **Every embedding call logs** model name, input token count, and latency.
5. **Every retrieval call logs** query text, result count, and similarity scores.
6. **Every `RagResponse` carries citations** — `GenerationService` always populates `citations`.

---

## REST API
    POST    /api/rag/ingest     Upload a document (multipart/form-data, param: file)
    POST    /api/rag/query      Ask a question (param: q), returns RagResponse JSON

---

## Environment Variables

```bash
ANTHROPIC_API_KEY          # Anthropic embeddings (voyage-3)
GOOGLE_API_KEY             # Vertex AI / Gemini generation
SPRING_DATASOURCE_URL      # jdbc:postgresql://localhost:5432/ragdb
SPRING_DATASOURCE_USERNAME # rag
SPRING_DATASOURCE_PASSWORD # rag
```

No secrets are hardcoded — `application.properties` references only `${ENV_VAR}` placeholders.
