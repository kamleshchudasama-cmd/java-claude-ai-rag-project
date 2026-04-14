# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview — Java RAG System
A Retrieval-Augmented Generation pipeline built on Spring Boot + Spring AI.
Ingests multi-format documents (PDF, DOCX, HTML) via Apache Tika, embeds chunks
using OpenAI Embeddings API, stores vectors in PGVector, retrieves via cosine
similarity, and generates cited answers via gpt-4o-mini through Spring AI.

---

## Stack & Versions
    Framework           Spring Boot                  3.3.x
    AI Abstraction      Spring AI                    1.0.0
    Vector Store        PGVector on PostgreSQL        pg 16, pgvector 0.7.x
    Document Parsing    Apache Tika                  2.9.x
    Embeddings          OpenAI text-embedding-3-small  via Spring AI (1536 dims)
    Generation          OpenAI gpt-4o-mini             via Spring AI
    Build               Maven                        3.9.x
    Language            Java                         21

---

## Maven Build Commands
```bash
mvn clean install                  # full build
mvn spring-boot:run                # run locally
mvn test                           # unit tests
mvn test -Dtest=IntegrationTests   # integration tests only
mvn dependency:tree                # inspect dependency graph
mvn versions:display-dependency-updates  # check for updates
```

---

## PGVector Docker Setup
```bash
# Start PGVector
docker run -d \
  --name pgvector \
  -e POSTGRES_USER=rag \
  -e POSTGRES_PASSWORD=rag \
  -e POSTGRES_DB=ragdb \
  -p 5432:5432 \
  pgvector/pgvector:pg16

# Verify extension
docker exec -it pgvector psql -U rag -d ragdb -c "CREATE EXTENSION IF NOT EXISTS vector;"

# Check vector table
docker exec -it pgvector psql -U rag -d ragdb -c "\d vector_store"
```

Spring AI auto-creates the `vector_store` table on startup when
`spring.ai.vectorstore.pgvector.initialize-schema=true`.

---

## Environment Variables (never hardcode values)
```bash
# OpenAI
OPENAI_API_KEY=...

# PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ragdb
SPRING_DATASOURCE_USERNAME=rag
SPRING_DATASOURCE_PASSWORD=rag
```
Reference in `application.properties` as `${OPENAI_API_KEY}`, etc.

---

## Spring AI Config Keys (application.properties)
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: text-embedding-3-small
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1536
        distance-type: COSINE_DISTANCE
        index-type: HNSW
```

---

## Module Map
```
src/main/java/com/yourorg/rag/
├── service/
│   ├── DocumentLoaderService.java    # Tika-based file ingestion
│   ├── ChunkingService.java          # overlapping token-based splitting
│   ├── EmbeddingService.java         # OpenAI embed via Spring AI
│   ├── VectorStoreService.java       # PGVector read/write abstraction
│   ├── QueryEmbeddingService.java    # embed user queries
│   ├── RetrievalService.java         # cosine similarity top-k search
│   ├── ContextBuilderService.java    # assemble grounded prompt
│   └── GenerationService.java        # gpt-4o-mini call, return cited answer
├── config/
│   ├── SpringAiConfig.java           # bean wiring for AI clients
│   └── RagProperties.java            # @ConfigurationProperties POJO
├── model/
│   ├── DocumentChunk.java
│   └── RagResponse.java
├── controller/
│   └── RagController.java            # REST endpoints
└── RagApplication.java
```

---

## Chunking Defaults
    Chunk size          512 tokens
    Overlap              50 tokens
    Strategy            Recursive character split (paragraph → sentence → word)
    Min chunk size      100 tokens (discard smaller)

All values in `RagProperties.java` — **never hardcode in service classes**.

---

## Service Contracts (invariants — do not change without updating this file)

1. **Never call the OpenAI API directly — always go through Spring AI abstraction.**
   Use `ChatClient` bean, not raw HTTP or OpenAI SDK.

2. **Never call the OpenAI Embeddings API directly — always use Spring AI's
   `EmbeddingModel` bean.**

3. `VectorStoreService` is the single point of contact with PGVector.
   No other service imports `VectorStore` or runs SQL directly.

4. `RagProperties` is the single source of truth for all tuneable params
   (chunk size, top-k, similarity threshold). Never read `@Value` for RAG params
   outside this class.

5. All embedding calls must log: model name, input token count, latency.

6. All retrieval calls must log: query text, top-k count, similarity scores.

7. `GenerationService` must include source document metadata in every response
   (`RagResponse.citations`).

---

## Key Dependencies (pom.xml excerpt)
```xml
<!-- Spring AI BOM -->
<dependencyManagement>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
    <scope>import</scope>
  </dependency>
</dependencyManagement>

<!-- Core -->
<dependency>org.springframework.ai:spring-ai-starter-model-openai</dependency>
<dependency>org.springframework.ai:spring-ai-starter-vector-store-pgvector</dependency>

<!-- Tika -->
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-parsers-standard-package</artifactId>
  <version>2.9.2</version>
</dependency>
```

---

## Rules
- Constructor injection only — no field or setter injection
- No Lombok
- All monetary values as `BigDecimal`, never `double` or `float`
- always use Objects.nonNull or Objects.isNull when checking for null values

## Specs
See `docs/specs/` — always check for an existing spec before implementing a feature.

## What NOT to Do
- Do not bypass Spring AI to call OpenAI APIs via raw HTTP
- Do not put chunking params as magic numbers in `ChunkingService`
- Do not run PGVector SQL outside `VectorStoreService`
- Do not change embedding dimensions without reindexing the vector table
- Do not add blocking calls inside reactive/async chains
- Catch `Exception` directly — use specific exception types
- Apply `@Transactional` on private methods
- Hardcode environment config — use `application.properties` / environment variables