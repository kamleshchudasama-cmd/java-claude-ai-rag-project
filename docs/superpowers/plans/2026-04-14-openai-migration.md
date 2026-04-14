# OpenAI Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Voyage AI embeddings and Gemini generation with OpenAI `text-embedding-3-small` and `gpt-4o-mini` via Spring AI, with no pom.xml changes.

**Architecture:** The `spring-ai-starter-model-openai` starter is already present and auto-configures both `OpenAiChatModel` and `OpenAiEmbeddingModel` beans. The only blocker is `VoyageEmbeddingModel` (a custom `@Primary` component) which overrides the auto-configured embedding bean — deleting it hands control back to Spring AI. Chat config currently points at Gemini via a base-url override; removing that override and updating the model name completes the generation swap.

**Tech Stack:** Spring Boot 3.3.x, Spring AI 1.0.0, `spring-ai-starter-model-openai`, PGVector, Java 21, Maven, JUnit 5 + Mockito

---

## File Map

| Action | File |
|---|---|
| Modify | `src/main/resources/application.properties` |
| Delete | `src/main/java/com/test/rag/config/VoyageEmbeddingModel.java` |
| Create | `src/main/java/com/test/rag/service/embedding/OpenAiEmbeddingService.java` |
| Delete | `src/main/java/com/test/rag/service/embedding/AnthropicEmbeddingService.java` |
| Create | `src/test/java/com/test/rag/service/embedding/OpenAiEmbeddingServiceTest.java` |
| Delete | `src/test/java/com/test/rag/service/embedding/AnthropicEmbeddingServiceTest.java` |
| Create | `src/main/java/com/test/rag/service/queryembedding/OpenAiQueryEmbeddingService.java` |
| Delete | `src/main/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingService.java` |
| Create | `src/test/java/com/test/rag/service/queryembedding/OpenAiQueryEmbeddingServiceTest.java` |
| Delete | `src/test/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingServiceTest.java` |
| Create | `src/main/java/com/test/rag/service/generation/OpenAiGenerationService.java` |
| Delete | `src/main/java/com/test/rag/service/generation/GeminiGenerationService.java` |
| Create | `src/test/java/com/test/rag/service/generation/OpenAiGenerationServiceTest.java` |
| Delete | `src/test/java/com/test/rag/service/generation/GeminiGenerationServiceTest.java` |
| Modify | `CLAUDE.md` |

---

## Task 1: Update application.properties

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Replace the entire file content**

Replace the full contents of `src/main/resources/application.properties` with:

```properties
# ── OpenAI (chat + embeddings) ────────────────────────────────────────────────
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.embedding.options.model=text-embedding-3-small

# ── RAG tuning ────────────────────────────────────────────────────────────────
rag.chunk-overlap=50
rag.embedding-batch-size=32
rag.embedding-request-delay-ms=0
rag.min-similarity=0.3

# ── PGVector store ────────────────────────────────────────────────────────────
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.dimensions=1536
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.index-type=HNSW

# ── PostgreSQL datasource ─────────────────────────────────────────────────────
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

Key changes from before:
- `spring.ai.openai.api-key` now reads `OPENAI_API_KEY` (was `GOOGLE_API_KEY`)
- `spring.ai.openai.chat.options.model` is `gpt-4o-mini` (was `gemini-2.5-flash-lite`)
- Gemini base-url and completions-path overrides are gone
- Added `spring.ai.openai.embedding.options.model=text-embedding-3-small`
- `pgvector.dimensions` is `1536` (was `1024`)
- `embedding-batch-size` reset to `32` (was `8` — Voyage rate-limit workaround)
- `embedding-request-delay-ms` reset to `0` (was `21000` — Voyage rate-limit workaround)

- [ ] **Step 2: Verify the build compiles**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS (no source changes yet, just config)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "config: switch to OpenAI gpt-4o-mini + text-embedding-3-small, dims 1536"
```

---

## Task 2: Delete VoyageEmbeddingModel

`VoyageEmbeddingModel` is a `@Primary @Component` that overrides Spring AI's auto-configured `OpenAiEmbeddingModel`. Deleting it lets the auto-configured bean take over.

**Files:**
- Delete: `src/main/java/com/test/rag/config/VoyageEmbeddingModel.java`

- [ ] **Step 1: Delete the file**

Delete `src/main/java/com/test/rag/config/VoyageEmbeddingModel.java`.

- [ ] **Step 2: Run all tests to confirm nothing breaks**

```bash
mvn test
```

Expected: BUILD SUCCESS — all existing tests pass. The tests for `AnthropicEmbeddingService` and `AnthropicQueryEmbeddingService` mock `EmbeddingModel` directly and do not depend on `VoyageEmbeddingModel`.

- [ ] **Step 3: Commit**

```bash
git add -u src/main/java/com/test/rag/config/VoyageEmbeddingModel.java
git commit -m "refactor: delete VoyageEmbeddingModel — OpenAI auto-config takes over"
```

---

## Task 3: Rename AnthropicEmbeddingService → OpenAiEmbeddingService

No logic changes — only the class name and logger reference change.

**Files:**
- Create: `src/main/java/com/test/rag/service/embedding/OpenAiEmbeddingService.java`
- Delete: `src/main/java/com/test/rag/service/embedding/AnthropicEmbeddingService.java`
- Create: `src/test/java/com/test/rag/service/embedding/OpenAiEmbeddingServiceTest.java`
- Delete: `src/test/java/com/test/rag/service/embedding/AnthropicEmbeddingServiceTest.java`

- [ ] **Step 1: Write the new test class (it will fail to compile until Step 3)**

Create `src/test/java/com/test/rag/service/embedding/OpenAiEmbeddingServiceTest.java`:

```java
package com.test.rag.service.embedding;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.EmbeddingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingService service;
    private RagProperties props;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        props.setEmbeddingBatchSize(32);
        service = new OpenAiEmbeddingService(embeddingModel, props);
    }

    @Test
    void embed_emptyList_returnsEmptyListWithNoApiCall() {
        List<EmbeddedChunk> result = service.embed(List.of());
        assertThat(result).isEmpty();
        verify(embeddingModel, never()).embedForResponse(anyList());
    }

    @Test
    void embed_outputCountMatchesInputCount() {
        List<DocumentChunk> chunks = List.of(
                chunk("c1", "Hello world"),
                chunk("c2", "Goodbye world")
        );
        when(embeddingModel.embedForResponse(anyList()))
                .thenReturn(fakeResponse(new float[]{0.6f, 0.8f}, new float[]{0.8f, 0.6f}));

        List<EmbeddedChunk> result = service.embed(chunks);

        assertThat(result).hasSize(2);
    }

    @Test
    void embed_vectorsAreNormalized() {
        List<DocumentChunk> chunks = List.of(chunk("c1", "Test"));
        when(embeddingModel.embedForResponse(anyList()))
                .thenReturn(fakeResponse(new float[]{3.0f, 4.0f}));

        List<EmbeddedChunk> result = service.embed(chunks);

        float[] embedding = result.get(0).embedding();
        double norm = Math.sqrt(embedding[0] * embedding[0] + embedding[1] * embedding[1]);
        assertThat(norm).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void embed_originalChunkPreservedInOutput() {
        DocumentChunk original = chunk("cOrig", "Original content");
        when(embeddingModel.embedForResponse(anyList()))
                .thenReturn(fakeResponse(new float[]{0.5f, 0.5f}));

        List<EmbeddedChunk> result = service.embed(List.of(original));

        assertThat(result.get(0).chunk()).isEqualTo(original);
    }

    @Test
    void embed_100ChunksWithBatchSize32_makes4ApiCalls() {
        props.setEmbeddingBatchSize(32);
        List<DocumentChunk> chunks = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> chunk("c" + i, "content " + i))
                .toList();
        when(embeddingModel.embedForResponse(anyList()))
                .thenAnswer(inv -> {
                    List<String> texts = inv.getArgument(0);
                    float[][] vectors = new float[texts.size()][];
                    for (int i = 0; i < texts.size(); i++) vectors[i] = new float[]{0.6f, 0.8f};
                    return fakeResponse(vectors);
                });

        service.embed(chunks);

        verify(embeddingModel, times(4)).embedForResponse(anyList());
    }

    @Test
    void embed_apiThrows_throwsEmbeddingException() {
        List<DocumentChunk> chunks = List.of(chunk("c1", "fail"));
        when(embeddingModel.embedForResponse(anyList()))
                .thenThrow(new RuntimeException("API error"));

        assertThatThrownBy(() -> service.embed(chunks))
                .isInstanceOf(EmbeddingException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void embed_zeroVector_returnedUnchanged() {
        List<DocumentChunk> chunks = List.of(chunk("c1", "Test"));
        when(embeddingModel.embedForResponse(anyList()))
                .thenReturn(fakeResponse(new float[]{0.0f, 0.0f}));

        List<EmbeddedChunk> result = service.embed(chunks);

        float[] embedding = result.get(0).embedding();
        assertThat(embedding[0]).isEqualTo(0.0f);
        assertThat(embedding[1]).isEqualTo(0.0f);
    }

    // --- helpers ---

    private DocumentChunk chunk(String id, String content) {
        int tokens = (int) Math.ceil(content.length() / 4.0);
        return new DocumentChunk(id, content, 0, tokens, Map.of("filename", "test.txt"));
    }

    private EmbeddingResponse fakeResponse(float[]... vectors) {
        List<Embedding> embeddings = new java.util.ArrayList<>();
        for (int i = 0; i < vectors.length; i++) {
            embeddings.add(new Embedding(vectors[i], i));
        }
        return new EmbeddingResponse(embeddings);
    }
}
```

- [ ] **Step 2: Run tests to confirm the new test fails to compile**

```bash
mvn test -Dtest=OpenAiEmbeddingServiceTest 2>&1 | head -20
```

Expected: COMPILATION ERROR — `cannot find symbol: class OpenAiEmbeddingService`

- [ ] **Step 3: Create OpenAiEmbeddingService.java**

Create `src/main/java/com/test/rag/service/embedding/OpenAiEmbeddingService.java`:

```java
package com.test.rag.service.embedding;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.EmbeddingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final RagProperties props;

    public OpenAiEmbeddingService(EmbeddingModel embeddingModel, RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.props = props;
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<EmbeddedChunk> embed(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return List.of();

        int batchSize = props.getEmbeddingBatchSize();
        List<EmbeddedChunk> result = new ArrayList<>();

        long delayMs = props.getEmbeddingRequestDelayMs();
        for (int start = 0; start < chunks.size(); start += batchSize) {
            if (start > 0 && delayMs > 0) {
                try {
                    log.info("Rate-limit delay {}ms before next embedding batch", delayMs);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new EmbeddingException("Embedding interrupted during rate-limit delay", ie);
                }
            }
            List<DocumentChunk> batch = chunks.subList(start, Math.min(start + batchSize, chunks.size()));
            result.addAll(embedBatch(batch));
        }
        return result;
    }

    private List<EmbeddedChunk> embedBatch(List<DocumentChunk> batch) {
        List<String> texts = batch.stream().map(DocumentChunk::content).toList();
        long startMs = System.currentTimeMillis();
        EmbeddingResponse response;
        try {
            response = embeddingModel.embedForResponse(texts);
        } catch (RuntimeException e) {
            throw new EmbeddingException("Embedding API call failed for batch of " + batch.size(), e);
        }

        long tokens = Objects.nonNull(response.getMetadata().getUsage())
                ? response.getMetadata().getUsage().getTotalTokens() : 0;
        log.info("Embedded batchSize={} model='{}' tokens={} latencyMs={}",
                batch.size(),
                response.getMetadata().getModel(),
                tokens,
                System.currentTimeMillis() - startMs);

        List<EmbeddedChunk> result = new ArrayList<>();
        List<org.springframework.ai.embedding.Embedding> embeddings = response.getResults();
        for (int i = 0; i < batch.size(); i++) {
            float[] raw = embeddings.get(i).getOutput();
            result.add(new EmbeddedChunk(batch.get(i), normalize(raw)));
        }
        return result;
    }

    private float[] normalize(float[] vector) {
        float sumSq = 0;
        for (float v : vector) sumSq += v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm < 1e-6f) return vector;
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = vector[i] / norm;
        return out;
    }
}
```

- [ ] **Step 4: Run new tests to confirm they pass**

```bash
mvn test -Dtest=OpenAiEmbeddingServiceTest
```

Expected: BUILD SUCCESS — all 6 tests pass

- [ ] **Step 5: Delete old files**

Delete:
- `src/main/java/com/test/rag/service/embedding/AnthropicEmbeddingService.java`
- `src/test/java/com/test/rag/service/embedding/AnthropicEmbeddingServiceTest.java`

- [ ] **Step 6: Run all tests to confirm nothing else broke**

```bash
mvn test
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/test/rag/service/embedding/ src/test/java/com/test/rag/service/embedding/
git commit -m "refactor: rename AnthropicEmbeddingService → OpenAiEmbeddingService"
```

---

## Task 4: Rename AnthropicQueryEmbeddingService → OpenAiQueryEmbeddingService

No logic changes — only class name and logger reference change. The dimensionality test is updated from 1024 to 1536 to match the new model.

**Files:**
- Create: `src/main/java/com/test/rag/service/queryembedding/OpenAiQueryEmbeddingService.java`
- Delete: `src/main/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingService.java`
- Create: `src/test/java/com/test/rag/service/queryembedding/OpenAiQueryEmbeddingServiceTest.java`
- Delete: `src/test/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingServiceTest.java`

- [ ] **Step 1: Write the new test class**

Create `src/test/java/com/test/rag/service/queryembedding/OpenAiQueryEmbeddingServiceTest.java`:

```java
package com.test.rag.service.queryembedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiQueryEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private QueryEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new OpenAiQueryEmbeddingService(embeddingModel);
    }

    @Test
    void embed_normalVector_returnsUnitNormalizedVector() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{3.0f, 4.0f});

        float[] result = service.embed("What is AI?");

        double norm = Math.sqrt(result[0] * result[0] + result[1] * result[1]);
        assertThat(norm).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void embed_knownVector_producesCorrectNormalizedValues() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{3.0f, 4.0f});

        float[] result = service.embed("What is AI?");

        assertThat(result[0]).isCloseTo(0.6f, within(0.0001f));
        assertThat(result[1]).isCloseTo(0.8f, within(0.0001f));
    }

    @Test
    void embed_zeroVector_returnedUnchanged() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.0f, 0.0f, 0.0f});

        float[] result = service.embed("query");

        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f);
    }

    @Test
    void embed_preservesDimensionality() {
        // text-embedding-3-small produces 1536-dimensional vectors
        float[] raw = new float[1536];
        for (int i = 0; i < 1536; i++) raw[i] = 0.1f;
        when(embeddingModel.embed(anyString())).thenReturn(raw);

        float[] result = service.embed("query");

        assertThat(result).hasSize(1536);
    }

    @Test
    void embed_singleElementVector_normalizedToOne() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{5.0f});

        float[] result = service.embed("query");

        assertThat(result[0]).isCloseTo(1.0f, within(0.0001f));
    }

    @Test
    void embed_passesExactQueryToEmbeddingModel() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1.0f});
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.captor();

        service.embed("What is machine learning?");

        verify(embeddingModel).embed(queryCaptor.capture());
        assertThat(queryCaptor.getValue()).isEqualTo("What is machine learning?");
    }

    @Test
    void embed_longQueryOver200Chars_fullQueryPassedToModel() {
        String longQuery = "A".repeat(300);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1.0f});
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.captor();

        service.embed(longQuery);

        verify(embeddingModel).embed(queryCaptor.capture());
        assertThat(queryCaptor.getValue()).hasSize(300);
        assertThat(queryCaptor.getValue()).isEqualTo(longQuery);
    }

    @Test
    void embed_queryExactly200Chars_passedToModelUntruncated() {
        String exactQuery = "B".repeat(200);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1.0f});
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.captor();

        service.embed(exactQuery);

        verify(embeddingModel).embed(queryCaptor.capture());
        assertThat(queryCaptor.getValue()).hasSize(200);
    }

    @Test
    void embed_queryWithSpecialCharacters_passedAsIs() {
        String query = "What's the #1 difference between AI & ML? (2024)";
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.6f, 0.8f});
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.captor();

        service.embed(query);

        verify(embeddingModel).embed(queryCaptor.capture());
        assertThat(queryCaptor.getValue()).isEqualTo(query);
    }

    @Test
    void embed_embeddingModelThrowsRuntimeException_propagatesToCaller() {
        when(embeddingModel.embed(anyString()))
                .thenThrow(new RuntimeException("Embedding API unavailable"));

        assertThatThrownBy(() -> service.embed("query"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Embedding API unavailable");
    }
}
```

- [ ] **Step 2: Run tests to confirm the new test fails to compile**

```bash
mvn test -Dtest=OpenAiQueryEmbeddingServiceTest 2>&1 | head -20
```

Expected: COMPILATION ERROR — `cannot find symbol: class OpenAiQueryEmbeddingService`

- [ ] **Step 3: Create OpenAiQueryEmbeddingService.java**

Create `src/main/java/com/test/rag/service/queryembedding/OpenAiQueryEmbeddingService.java`:

```java
package com.test.rag.service.queryembedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class OpenAiQueryEmbeddingService implements QueryEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQueryEmbeddingService.class);
    private static final int LOG_QUERY_MAX_CHARS = 200;

    private final EmbeddingModel embeddingModel;

    public OpenAiQueryEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String userQuery) {
        long start = System.currentTimeMillis();
        float[] raw = embeddingModel.embed(userQuery);
        float[] normalized = normalize(raw);

        String truncated = userQuery.length() > LOG_QUERY_MAX_CHARS
                ? userQuery.substring(0, LOG_QUERY_MAX_CHARS) + "…"
                : userQuery;
        log.info("QueryEmbedding query='{}' dims={} latencyMs={}",
                truncated, normalized.length, System.currentTimeMillis() - start);

        return normalized;
    }

    private float[] normalize(float[] vector) {
        float sumSq = 0;
        for (float v : vector) sumSq += v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm < 1e-6f) return vector;
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = vector[i] / norm;
        return out;
    }
}
```

- [ ] **Step 4: Run new tests to confirm they pass**

```bash
mvn test -Dtest=OpenAiQueryEmbeddingServiceTest
```

Expected: BUILD SUCCESS — all 9 tests pass

- [ ] **Step 5: Delete old files**

Delete:
- `src/main/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingService.java`
- `src/test/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingServiceTest.java`

- [ ] **Step 6: Run all tests to confirm nothing else broke**

```bash
mvn test
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/test/rag/service/queryembedding/ src/test/java/com/test/rag/service/queryembedding/
git commit -m "refactor: rename AnthropicQueryEmbeddingService → OpenAiQueryEmbeddingService"
```

---

## Task 5: Rename GeminiGenerationService → OpenAiGenerationService + update retry

Logic change: `@Retryable` backoff changes from `delay=31_000, multiplier=1.5` (Gemini-specific) to `delay=10_000` (flat, for OpenAI).

**Files:**
- Create: `src/main/java/com/test/rag/service/generation/OpenAiGenerationService.java`
- Delete: `src/main/java/com/test/rag/service/generation/GeminiGenerationService.java`
- Create: `src/test/java/com/test/rag/service/generation/OpenAiGenerationServiceTest.java`
- Delete: `src/test/java/com/test/rag/service/generation/GeminiGenerationServiceTest.java`

- [ ] **Step 1: Write the new test class**

Create `src/test/java/com/test/rag/service/generation/OpenAiGenerationServiceTest.java`:

```java
package com.test.rag.service.generation;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.RagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiGenerationServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatResponse chatResponse;

    private RagProperties props;
    private GenerationService service;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        service = new OpenAiGenerationService(chatClient, props);
    }

    private void givenChatClientReturns(ChatResponse response) {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .chatResponse())
            .thenReturn(response);
    }

    @Test
    void generate_returnsAnswerTextFromChatResponse() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText()).thenReturn("This is the AI answer.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("What is AI?", List.of()));

        assertThat(result.answer()).isEqualTo("This is the AI answer.");
    }

    @Test
    void generate_answerWithCitationMarkers_returnedVerbatim() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Machine learning [1] is a subset of AI [2].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 1)
        )));

        assertThat(result.answer()).isEqualTo("Machine learning [1] is a subset of AI [2].");
    }

    @Test
    void generate_noCitationMarkersInAnswer_returnsEmptyCitations() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Answer with no citation markers.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query",
                List.of(citation(1, "doc.pdf", 0))));

        assertThat(result.citations()).isEmpty();
    }

    @Test
    void generate_allCitationMarkersInAnswer_returnsAllCitations() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("First point [1] and second point [2].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 1)
        )));

        assertThat(result.citations()).hasSize(2);
    }

    @Test
    void generate_partialCitationMarkersInAnswer_returnsOnlyReferencedCitations() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Only [1] is mentioned here.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 1),
                citation(3, "c.pdf", 2)
        )));

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).ref()).isEqualTo(1);
        assertThat(result.citations().get(0).filename()).isEqualTo("a.pdf");
    }

    @Test
    void generate_sameRefUsedMultipleTimesInAnswer_citationIncludedOnce() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Claim A [1]. Also claim B [1].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query",
                List.of(citation(1, "source.pdf", 0))));

        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void generate_multipleRefsForOneClaim_allReferencedCitationsIncluded() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Neural networks [1][2] power deep learning.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 0)
        )));

        assertThat(result.citations()).hasSize(2);
    }

    @Test
    void generate_unreferencedCitationsNotIncludedInResponse() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("The answer is supported by [2].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "unused-a.pdf", 0),
                citation(2, "used.pdf",     1),
                citation(3, "unused-b.pdf", 2)
        )));

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filename()).isEqualTo("used.pdf");
    }

    @Test
    void generate_usageMetadataIsNull_totalTokensIsZero() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText()).thenReturn("An answer.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of()));

        assertThat(result.totalTokens()).isZero();
    }

    @Test
    void generate_usageMetadataPresent_totalTokensFromApiResponse() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText()).thenReturn("An answer.");
        when(chatResponse.getMetadata().getUsage().getTotalTokens()).thenReturn(250);

        RagResponse result = service.generate(builtContext("query", List.of()));

        assertThat(result.totalTokens()).isEqualTo(250);
    }

    @Test
    void generate_chatClientThrowsRuntimeException_propagatesToCaller() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .chatResponse())
            .thenThrow(new RuntimeException("Chat API unavailable"));

        assertThatThrownBy(() -> service.generate(builtContext("query", List.of())))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Chat API unavailable");
    }

    // --- helpers ---

    private BuiltContext builtContext(String userMessage, List<Citation> citations) {
        return new BuiltContext("You are a grounded assistant.", userMessage, citations);
    }

    private Citation citation(int ref, String filename, int chunkIndex) {
        return new Citation(ref, filename, chunkIndex, 0.90, "Sample text for ref " + ref);
    }
}
```

- [ ] **Step 2: Run tests to confirm the new test fails to compile**

```bash
mvn test -Dtest=OpenAiGenerationServiceTest 2>&1 | head -20
```

Expected: COMPILATION ERROR — `cannot find symbol: class OpenAiGenerationService`

- [ ] **Step 3: Create OpenAiGenerationService.java**

Create `src/main/java/com/test/rag/service/generation/OpenAiGenerationService.java`:

```java
package com.test.rag.service.generation;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.RagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Objects;

@Service
public class OpenAiGenerationService implements GenerationService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiGenerationService.class);

    private final ChatClient chatClient;
    private final RagProperties props;

    public OpenAiGenerationService(ChatClient chatClient, RagProperties props) {
        this.chatClient = chatClient;
        this.props = props;
    }

    @Override
    @Retryable(
            retryFor = HttpClientErrorException.TooManyRequests.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 10_000)
    )
    public RagResponse generate(BuiltContext context) {
        long start = System.currentTimeMillis();

        ChatResponse chatResponse = chatClient.prompt()
                .system(context.systemPrompt())
                .user(context.userMessage())
                .options(OpenAiChatOptions.builder()
                        .temperature(props.getTemperature().doubleValue())
                        .maxCompletionTokens(props.getMaxOutputTokens())
                        .build())
                .call()
                .chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        long totalTokens = Objects.nonNull(chatResponse.getMetadata().getUsage())
                ? chatResponse.getMetadata().getUsage().getTotalTokens() : 0;

        List<Citation> usedCitations = context.citations().stream()
                .filter(c -> answer.contains("[" + c.ref() + "]"))
                .toList();

        log.info("Generation totalTokens={} citations={} latencyMs={}",
                totalTokens, usedCitations.size(), System.currentTimeMillis() - start);

        return new RagResponse(answer, usedCitations, (int) totalTokens);
    }
}
```

- [ ] **Step 4: Run new tests to confirm they pass**

```bash
mvn test -Dtest=OpenAiGenerationServiceTest
```

Expected: BUILD SUCCESS — all 11 tests pass

- [ ] **Step 5: Delete old files**

Delete:
- `src/main/java/com/test/rag/service/generation/GeminiGenerationService.java`
- `src/test/java/com/test/rag/service/generation/GeminiGenerationServiceTest.java`

- [ ] **Step 6: Run all tests to confirm nothing else broke**

```bash
mvn test
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/test/rag/service/generation/ src/test/java/com/test/rag/service/generation/
git commit -m "refactor: rename GeminiGenerationService → OpenAiGenerationService, update retry to 3x/10s"
```

---

## Task 6: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update the Stack table**

In the `## Stack & Versions` section, replace:

```
    Embeddings          Anthropic Embeddings API      via Spring AI
    Generation          Gemini-2.5-flash             via Spring AI Google Vertex AI
```

with:

```
    Embeddings          OpenAI text-embedding-3-small  via Spring AI (1536 dims)
    Generation          OpenAI gpt-4o-mini             via Spring AI
```

- [ ] **Step 2: Update the Environment Variables section**

Replace:

```bash
# Anthropic
ANTHROPIC_API_KEY=...

# Google (Gemini)
GOOGLE_API_KEY=...
```

with:

```bash
# OpenAI
OPENAI_API_KEY=...
```

- [ ] **Step 3: Update the Spring AI Config Keys section**

Replace the entire yaml block with:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.2
          max-completion-tokens: 2048
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

- [ ] **Step 4: Update the Module Map service class names**

Replace:
```
│   ├── EmbeddingService.java         # Anthropic embed via Spring AI
```
with:
```
│   ├── EmbeddingService.java         # OpenAI embed via Spring AI
```

And in Service Contracts section, update contract #2:

Replace:
> "Never call the Anthropic Embeddings API directly — always use Spring AI's `EmbeddingClient` bean."

with:
> "Never call the OpenAI Embeddings API directly — always use Spring AI's `EmbeddingModel` bean."

- [ ] **Step 5: Update Key Dependencies section**

Remove the Anthropic and Vertex AI starter entries. Replace with:

```xml
<!-- Spring AI — OpenAI (chat + embeddings) -->
<dependency>org.springframework.ai:spring-ai-starter-model-openai</dependency>
```

- [ ] **Step 6: Build to confirm CLAUDE.md changes didn't break anything**

```bash
mvn test
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for OpenAI stack (gpt-4o-mini + text-embedding-3-small)"
```

---

## Task 7: PGVector Re-index

> **Manual step — required before the application will work end-to-end.**

The vector dimensions changed from 1024 to 1536. The existing `vector_store` table must be dropped and recreated. All previously ingested documents must be re-ingested.

- [ ] **Step 1: Drop the vector_store table**

```bash
docker exec -it pgvector psql -U rag -d ragdb -c "DROP TABLE IF EXISTS vector_store;"
```

Expected output: `DROP TABLE`

- [ ] **Step 2: Start the application**

```bash
mvn spring-boot:run
```

Spring AI will auto-recreate the `vector_store` table with 1536 dimensions (because `spring.ai.vectorstore.pgvector.initialize-schema=true`).

- [ ] **Step 3: Verify the new schema**

```bash
docker exec -it pgvector psql -U rag -d ragdb -c "\d vector_store"
```

Expected: the `embedding` column shows `vector(1536)` (not `vector(1024)`).

- [ ] **Step 4: Re-ingest your documents**

Use `POST /api/rag/ingest` to re-upload all previously ingested files. The new embeddings will be generated by `text-embedding-3-small` and stored with 1536 dimensions.
