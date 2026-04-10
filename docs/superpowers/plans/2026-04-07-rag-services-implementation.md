# RAG Services Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all 7 remaining RAG pipeline services (ChunkingService → GenerationService) plus the RagController endpoints, wiring the complete ingestion and query pipelines.

**Architecture:** Each service becomes an interface + single implementation following the DocumentLoaderService pattern. Ingestion pipeline: `DocumentLoaderService → ChunkingService → EmbeddingService → VectorStoreService`. Query pipeline: `RetrievalService (QueryEmbeddingService + VectorStoreService) → ContextBuilderService → GenerationService`. All tuneable params come from `RagProperties`; no `@Value` for RAG params. Constructor injection only, no Lombok.

**Tech Stack:** Spring Boot 3.3.x, Spring AI 1.0.0 (`EmbeddingModel`, `VectorStore`, `ChatClient`), PGVector, Java 21 records, Mockito 5 for tests.

**Spring AI note:** `VectorStore.similaritySearch(SearchRequest)` takes a text query — it does not accept a pre-computed `float[]` vector through its public API. `VectorStoreService.search` therefore accepts `String query`. `QueryEmbeddingService` is still fully implemented and called by `RetrievalService` for per-request telemetry/logging; the Spring AI VectorStore embeds the text internally for the actual search.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `src/main/java/com/test/rag/model/DocumentChunk.java` | Modify | Add `tokenCount` field (missing from spec) |
| `src/main/java/com/test/rag/exception/ChunkingException.java` | Create | Unchecked exception for chunking failures |
| `src/main/java/com/test/rag/service/chunking/ChunkingService.java` | Overwrite | Interface (replace concrete stub) |
| `src/main/java/com/test/rag/service/chunking/RecursiveChunkingService.java` | Create | Implementation: paragraph→sentence→word recursive split with overlap |
| `src/test/java/com/test/rag/service/chunking/RecursiveChunkingServiceTest.java` | Create | Pure-Java tests, no mocks |
| `src/main/java/com/test/rag/service/embedding/EmbeddingService.java` | Overwrite | Interface |
| `src/main/java/com/test/rag/service/embedding/AnthropicEmbeddingService.java` | Create | Batched embedding via Spring AI `EmbeddingModel`, L2 normalisation, `@Retryable` |
| `src/test/java/com/test/rag/service/embedding/AnthropicEmbeddingServiceTest.java` | Create | Mock `EmbeddingModel` |
| `src/main/java/com/test/rag/service/vectorstore/VectorStoreService.java` | Overwrite | Interface (search takes `String query`) |
| `src/main/java/com/test/rag/service/vectorstore/PgVectorStoreService.java` | Create | Spring AI `VectorStore` upsert / search / delete |
| `src/test/java/com/test/rag/service/vectorstore/PgVectorStoreServiceTest.java` | Create | Mock `VectorStore` |
| `src/main/java/com/test/rag/service/queryembedding/QueryEmbeddingService.java` | Overwrite | Interface |
| `src/main/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingService.java` | Create | Single-string embed + L2 normalise + log |
| `src/test/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingServiceTest.java` | Create | Mock `EmbeddingModel` |
| `src/main/java/com/test/rag/service/retrieval/RetrievalService.java` | Overwrite | Interface |
| `src/main/java/com/test/rag/service/retrieval/VectorRetrievalService.java` | Create | Calls `QueryEmbeddingService` (log) then `VectorStoreService.search` |
| `src/test/java/com/test/rag/service/retrieval/VectorRetrievalServiceTest.java` | Create | Mock both dependencies |
| `src/main/resources/prompts/rag-system.st` | Create | StringTemplate for system prompt |
| `src/main/java/com/test/rag/service/context/ContextBuilderService.java` | Overwrite | Interface |
| `src/main/java/com/test/rag/service/context/PromptContextBuilderService.java` | Create | Builds `BuiltContext`; truncates by token count |
| `src/test/java/com/test/rag/service/context/PromptContextBuilderServiceTest.java` | Create | Pure-Java tests, no mocks |
| `src/main/java/com/test/rag/service/generation/GenerationService.java` | Overwrite | Interface |
| `src/main/java/com/test/rag/service/generation/GeminiGenerationService.java` | Create | Calls `ChatClient`, parses `[N]` citations |
| `src/test/java/com/test/rag/service/generation/GeminiGenerationServiceTest.java` | Create | Mock `ChatClient` chain |
| `src/main/java/com/test/rag/controller/RagController.java` | Modify | Implement `/ingest` and `/query` endpoints |

---

## Task 1 — Add `tokenCount` to `DocumentChunk`

**Files:**
- Modify: `src/main/java/com/test/rag/model/DocumentChunk.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/test/rag/model/DocumentChunkTest.java`:

```java
package com.test.rag.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkTest {

    @Test
    void documentChunk_hasTokenCountField() {
        DocumentChunk chunk = new DocumentChunk("id1", "some content", 0, 3, Map.of("filename", "test.txt"));
        assertThat(chunk.tokenCount()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=DocumentChunkTest
```
Expected: `COMPILATION ERROR` — constructor takes 4 args, `tokenCount()` does not exist.

- [ ] **Step 3: Update `DocumentChunk` record**

```java
package com.test.rag.model;

import java.util.Map;

public record DocumentChunk(
        String chunkId,
        String content,
        int chunkIndex,
        int tokenCount,
        Map<String, String> metadata
) {}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=DocumentChunkTest
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Verify full compile (DocumentChunk is used by other files)**

```bash
mvn compile
```
Expected: `BUILD SUCCESS` (no other class constructs DocumentChunk yet)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/model/DocumentChunk.java src/test/java/com/test/rag/model/DocumentChunkTest.java
git commit -m "feat: add tokenCount field to DocumentChunk record"
```

---

## Task 2 — `ChunkingException`

**Files:**
- Create: `src/main/java/com/test/rag/exception/ChunkingException.java`

- [ ] **Step 1: Create the exception**

```java
package com.test.rag.exception;

public class ChunkingException extends RuntimeException {

    public ChunkingException(String message) {
        super(message);
    }

    public ChunkingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn compile
```
Expected: `BUILD SUCCESS`

---

## Task 3 — `ChunkingService`: interface + `RecursiveChunkingService`

**Files:**
- Overwrite: `src/main/java/com/test/rag/service/chunking/ChunkingService.java`
- Create: `src/main/java/com/test/rag/service/chunking/RecursiveChunkingService.java`
- Create: `src/test/java/com/test/rag/service/chunking/RecursiveChunkingServiceTest.java`

### 3a — Interface

- [ ] **Step 1: Overwrite stub with interface**

```java
package com.test.rag.service.chunking;

import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ParsedDocument;

import java.util.List;

public interface ChunkingService {
    List<DocumentChunk> chunk(ParsedDocument document);
}
```

### 3b — Tests (RED)

- [ ] **Step 2: Write failing tests**

Create `src/test/java/com/test/rag/service/chunking/RecursiveChunkingServiceTest.java`:

```java
package com.test.rag.service.chunking;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.ChunkingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecursiveChunkingServiceTest {

    private ChunkingService service;
    private RagProperties props;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        props.setChunkSize(50);       // 50 tokens ≈ 200 chars
        props.setChunkOverlap(10);    // 10 tokens ≈ 40 chars
        props.setMinChunkSize(5);     // 5 tokens ≈ 20 chars
        service = new RecursiveChunkingService(props);
    }

    @Test
    void chunk_blankContent_throwsChunkingException() {
        ParsedDocument doc = new ParsedDocument("   ", Map.of("filename", "a.txt"), "src1");
        assertThatThrownBy(() -> service.chunk(doc))
                .isInstanceOf(ChunkingException.class)
                .hasMessageContaining("Cannot chunk empty document");
    }

    @Test
    void chunk_shortDocument_returnsSingleChunk() {
        // 10 words ≈ 10 tokens — well below chunk size
        String text = "Hello world this is a short test document.";
        ParsedDocument doc = new ParsedDocument(text, Map.of("filename", "short.txt"), "src2");
        List<DocumentChunk> chunks = service.chunk(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo(text);
    }

    @Test
    void chunk_longDocument_consecutiveChunksOverlap() {
        // Build ~600 chars (≈150 tokens) to guarantee multiple chunks at chunkSize=50
        String paragraph = "The quick brown fox jumps over the lazy dog. ";
        String text = paragraph.repeat(14); // ~630 chars
        ParsedDocument doc = new ParsedDocument(text, Map.of("filename", "long.txt"), "src3");

        List<DocumentChunk> chunks = service.chunk(doc);
        assertThat(chunks.size()).isGreaterThan(1);

        // Consecutive chunks must share some text (overlap)
        String endOfFirst = chunks.get(0).content();
        String startOfSecond = chunks.get(1).content();
        // Last 40 chars of chunk[0] should appear at the beginning of chunk[1]
        String overlapText = endOfFirst.substring(Math.max(0, endOfFirst.length() - 40));
        assertThat(startOfSecond).startsWith(overlapText.strip());
    }

    @Test
    void chunk_sameInputTwice_producesIdenticalChunkIds() {
        String text = "Determinism check. ".repeat(30);
        ParsedDocument doc = new ParsedDocument(text, Map.of("filename", "det.txt"), "srcDet");

        List<DocumentChunk> first = service.chunk(doc);
        List<DocumentChunk> second = service.chunk(doc);

        assertThat(first).hasSameSizeAs(second);
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i).chunkId()).isEqualTo(second.get(i).chunkId());
        }
    }

    @Test
    void chunk_allChunksInheritSourceMetadata() {
        String text = "Metadata test. ".repeat(30);
        Map<String, String> meta = Map.of("filename", "meta.txt", "author", "Alice");
        ParsedDocument doc = new ParsedDocument(text, meta, "srcMeta");

        List<DocumentChunk> chunks = service.chunk(doc);
        for (DocumentChunk chunk : chunks) {
            assertThat(chunk.metadata()).containsKey("filename");
            assertThat(chunk.metadata().get("filename")).isEqualTo("meta.txt");
            assertThat(chunk.metadata()).containsKey("chunk_index");
        }
    }

    @Test
    void chunk_noChunkBelowMinChunkSize() {
        props.setMinChunkSize(10); // 10 tokens ≈ 40 chars
        String text = "Word ".repeat(60); // ~300 chars
        ParsedDocument doc = new ParsedDocument(text, Map.of("filename", "min.txt"), "srcMin");

        List<DocumentChunk> chunks = service.chunk(doc);
        for (DocumentChunk chunk : chunks) {
            assertThat(chunk.tokenCount()).isGreaterThanOrEqualTo(10);
        }
    }

    @Test
    void chunk_tokenCountIsCharCountDividedByFour() {
        String content = "A".repeat(100); // 100 chars → ceil(100/4.0) = 25 tokens
        ParsedDocument doc = new ParsedDocument(content, Map.of("filename", "tok.txt"), "srcTok");

        List<DocumentChunk> chunks = service.chunk(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).tokenCount()).isEqualTo(25);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn test -Dtest=RecursiveChunkingServiceTest
```
Expected: `COMPILATION ERROR` — `RecursiveChunkingService` does not exist.

### 3c — Implementation (GREEN)

- [ ] **Step 4: Create `RecursiveChunkingService`**

```java
package com.test.rag.service.chunking;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.ChunkingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RecursiveChunkingService implements ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(RecursiveChunkingService.class);
    private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ", "! ", "? ", " ", "");

    private final RagProperties props;

    public RecursiveChunkingService(RagProperties props) {
        this.props = props;
    }

    @Override
    public List<DocumentChunk> chunk(ParsedDocument document) {
        if (Objects.isNull(document.content()) || document.content().isBlank()) {
            throw new ChunkingException("Cannot chunk empty document");
        }

        int chunkSizeChars = props.getChunkSize() * 4;
        int overlapChars   = props.getChunkOverlap() * 4;
        int minChunkChars  = props.getMinChunkSize() * 4;

        List<String> rawChunks = split(document.content(), chunkSizeChars);

        // Apply overlap
        List<String> withOverlap = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            if (i == 0) {
                withOverlap.add(rawChunks.get(i));
            } else {
                String prev = rawChunks.get(i - 1);
                String tail = prev.substring(Math.max(0, prev.length() - overlapChars));
                withOverlap.add(tail + rawChunks.get(i));
            }
        }

        // Map to DocumentChunk, discard below minChunkChars
        String sourceId = document.sourceId();
        List<DocumentChunk> result = new ArrayList<>();
        int index = 0;

        for (String content : withOverlap) {
            String trimmed = content.strip();
            if (trimmed.isEmpty() || trimmed.length() < minChunkChars) continue;

            int tokenCount = (int) Math.ceil(trimmed.length() / 4.0);
            String chunkId = sha256(sourceId + index);

            Map<String, String> meta = new HashMap<>(document.metadata());
            meta.put("chunk_index", String.valueOf(index));

            result.add(new DocumentChunk(chunkId, trimmed, index, tokenCount, Map.copyOf(meta)));
            index++;
        }

        // If all chunks were discarded (very short doc), return one chunk for full content
        if (result.isEmpty()) {
            String trimmed = document.content().strip();
            int tokenCount = (int) Math.ceil(trimmed.length() / 4.0);
            Map<String, String> meta = new HashMap<>(document.metadata());
            meta.put("chunk_index", "0");
            result.add(new DocumentChunk(sha256(sourceId + "0"), trimmed, 0, tokenCount, Map.copyOf(meta)));
        }

        int min = result.stream().mapToInt(DocumentChunk::tokenCount).min().orElse(0);
        int max = result.stream().mapToInt(DocumentChunk::tokenCount).max().orElse(0);
        double avg = result.stream().mapToInt(DocumentChunk::tokenCount).average().orElse(0);
        log.info("Chunked file='{}' chunks={} tokenMin={} tokenMax={} tokenAvg={:.1f}",
                document.metadata().getOrDefault("filename", "unknown"),
                result.size(), min, max, avg);

        return result;
    }

    // Recursive character split: tries separators in order, merges small pieces
    private List<String> split(String text, int maxChars) {
        if (text.length() <= maxChars) return List.of(text);

        String separator = SEPARATORS.stream()
                .filter(s -> !s.isEmpty() && text.contains(s))
                .findFirst()
                .orElse("");

        String[] parts;
        if (separator.isEmpty()) {
            parts = new String[]{text.substring(0, maxChars), text.substring(maxChars)};
        } else {
            parts = text.split(java.util.regex.Pattern.quote(separator), -1);
        }

        // Merge parts greedily into chunks ≤ maxChars
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String joined = current.isEmpty() ? part : current + separator + part;
            if (joined.length() > maxChars && !current.isEmpty()) {
                chunks.add(current.toString());
                current = new StringBuilder(part);
            } else {
                current = new StringBuilder(joined);
            }
        }
        if (!current.isEmpty()) chunks.add(current.toString());

        // Recursively split any chunk still above maxChars
        List<String> result = new ArrayList<>();
        List<String> remaining = SEPARATORS.subList(
                SEPARATORS.indexOf(separator) + 1, SEPARATORS.size());
        for (String chunk : chunks) {
            if (chunk.length() > maxChars && !remaining.isEmpty()) {
                result.addAll(split(chunk, maxChars));
            } else {
                result.add(chunk);
            }
        }
        return result;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=RecursiveChunkingServiceTest
```
Expected: `Tests run: 7, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/exception/ChunkingException.java \
        src/main/java/com/test/rag/service/chunking/ \
        src/test/java/com/test/rag/service/chunking/
git commit -m "feat: implement RecursiveChunkingService with overlap and deterministic chunk IDs"
```

---

## Task 4 — `EmbeddingService`: interface + `AnthropicEmbeddingService`

**Files:**
- Overwrite: `src/main/java/com/test/rag/service/embedding/EmbeddingService.java`
- Create: `src/main/java/com/test/rag/service/embedding/AnthropicEmbeddingService.java`
- Create: `src/test/java/com/test/rag/service/embedding/AnthropicEmbeddingServiceTest.java`

### 4a — Interface

- [ ] **Step 1: Overwrite stub with interface**

```java
package com.test.rag.service.embedding;

import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;

import java.util.List;

public interface EmbeddingService {
    List<EmbeddedChunk> embed(List<DocumentChunk> chunks);
}
```

### 4b — Tests (RED)

- [ ] **Step 2: Write failing tests**

Create `src/test/java/com/test/rag/service/embedding/AnthropicEmbeddingServiceTest.java`:

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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

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
class AnthropicEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingService service;
    private RagProperties props;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        props.setEmbeddingBatchSize(32);
        service = new AnthropicEmbeddingService(embeddingModel, props);
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
        // Raw vector with L2 norm = 5.0 (3,4 → norm=5)
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
        // Return 32, 32, 32, 4 embeddings per batch
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

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn test -Dtest=AnthropicEmbeddingServiceTest
```
Expected: `COMPILATION ERROR` — `AnthropicEmbeddingService` does not exist.

### 4c — Implementation (GREEN)

- [ ] **Step 4: Create `AnthropicEmbeddingService`**

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
public class AnthropicEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final RagProperties props;

    public AnthropicEmbeddingService(EmbeddingModel embeddingModel, RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.props = props;
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<EmbeddedChunk> embed(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return List.of();

        int batchSize = props.getEmbeddingBatchSize();
        List<EmbeddedChunk> result = new ArrayList<>();

        for (int start = 0; start < chunks.size(); start += batchSize) {
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
        } catch (Exception e) {
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
        if (norm == 0) return vector;
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = vector[i] / norm;
        return out;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=AnthropicEmbeddingServiceTest
```
Expected: `Tests run: 6, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/embedding/ \
        src/test/java/com/test/rag/service/embedding/
git commit -m "feat: implement AnthropicEmbeddingService with batching, L2 normalisation, and retry"
```

---

## Task 5 — `VectorStoreService`: interface + `PgVectorStoreService`

**Files:**
- Overwrite: `src/main/java/com/test/rag/service/vectorstore/VectorStoreService.java`
- Create: `src/main/java/com/test/rag/service/vectorstore/PgVectorStoreService.java`
- Create: `src/test/java/com/test/rag/service/vectorstore/PgVectorStoreServiceTest.java`

### 5a — Interface

- [ ] **Step 1: Overwrite stub with interface**

```java
package com.test.rag.service.vectorstore;

import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ScoredChunk;

import java.util.List;

/**
 * Single gateway for all PGVector reads and writes.
 * No other service may import VectorStore or run SQL.
 * search() accepts the original query text; Spring AI VectorStore embeds it internally.
 */
public interface VectorStoreService {
    void upsert(List<EmbeddedChunk> chunks);
    List<ScoredChunk> search(String query, int topK, double threshold);
    void deleteBySource(String filename);
}
```

### 5b — Tests (RED)

- [ ] **Step 2: Write failing tests**

Create `src/test/java/com/test/rag/service/vectorstore/PgVectorStoreServiceTest.java`:

```java
package com.test.rag.service.vectorstore;

import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgVectorStoreServiceTest {

    @Mock
    private VectorStore vectorStore;

    private VectorStoreService service;

    @BeforeEach
    void setUp() {
        service = new PgVectorStoreService(vectorStore);
    }

    @Test
    void upsert_emptyList_makesNoDbCall() {
        service.upsert(List.of());
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void upsert_mapsChunkIdAsDocumentId() {
        EmbeddedChunk embedded = embeddedChunk("chunk-id-1", "Hello content", 0);
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.captor();

        service.upsert(List.of(embedded));

        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue().get(0).getId()).isEqualTo("chunk-id-1");
    }

    @Test
    void search_returnsEmptyListWhenNoResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<ScoredChunk> result = service.search("test query", 5, 0.75);

        assertThat(result).isEmpty();
    }

    @Test
    void search_mapsDocumentToScoredChunk() {
        Document doc = Document.builder()
                .id("chunk-id-1")
                .text("Chunk content")
                .metadata(Map.of(
                        "chunkId", "chunk-id-1",
                        "chunkIndex", "0",
                        "tokenCount", "3",
                        "filename", "test.pdf"
                ))
                .score(0.92)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<ScoredChunk> result = service.search("query", 5, 0.75);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).similarityScore()).isEqualTo(0.92);
        assertThat(result.get(0).chunk().content()).isEqualTo("Chunk content");
        assertThat(result.get(0).chunk().chunkId()).isEqualTo("chunk-id-1");
    }

    @Test
    void search_passesTopKAndThresholdToSearchRequest() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.captor();

        service.search("query", 3, 0.8);

        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(3);
        assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.8);
    }

    @Test
    void deleteBySource_noChunksFound_logsWarningNoException() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        // Should not throw
        service.deleteBySource("missing.pdf");
        verify(vectorStore, never()).delete(anyList());
    }

    // --- helpers ---

    private EmbeddedChunk embeddedChunk(String chunkId, String content, int index) {
        DocumentChunk chunk = new DocumentChunk(
                chunkId, content, index, 3,
                Map.of("filename", "test.pdf", "chunk_index", String.valueOf(index)));
        return new EmbeddedChunk(chunk, new float[]{0.6f, 0.8f});
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn test -Dtest=PgVectorStoreServiceTest
```
Expected: `COMPILATION ERROR` — `PgVectorStoreService` does not exist.

### 5c — Implementation (GREEN)

- [ ] **Step 4: Create `PgVectorStoreService`**

```java
package com.test.rag.service.vectorstore;

import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PgVectorStoreService implements VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreService.class);

    private final VectorStore vectorStore;

    public PgVectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void upsert(List<EmbeddedChunk> chunks) {
        if (chunks.isEmpty()) return;

        long start = System.currentTimeMillis();
        List<Document> documents = chunks.stream().map(this::toDocument).toList();
        vectorStore.add(documents);
        log.info("Upserted chunks={} latencyMs={}", chunks.size(), System.currentTimeMillis() - start);
    }

    @Override
    public List<ScoredChunk> search(String query, int topK, double threshold) {
        long start = System.currentTimeMillis();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);
        List<ScoredChunk> results = docs.stream().map(this::toScoredChunk).toList();

        double minScore = results.stream().mapToDouble(ScoredChunk::similarityScore).min().orElse(0);
        double maxScore = results.stream().mapToDouble(ScoredChunk::similarityScore).max().orElse(0);
        log.info("Search topK={} returned={} scoreRange=[{:.3f},{:.3f}] latencyMs={}",
                topK, results.size(), minScore, maxScore, System.currentTimeMillis() - start);

        return results;
    }

    @Override
    public void deleteBySource(String filename) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
                .query(" ")
                .topK(10_000)
                .similarityThreshold(0.0)
                .filterExpression(b.eq("filename", filename).build())
                .build();

        List<Document> found = vectorStore.similaritySearch(request);
        if (found.isEmpty()) {
            log.warn("deleteBySource: no chunks found for filename='{}'", filename);
            return;
        }

        List<String> ids = found.stream().map(Document::getId).toList();
        vectorStore.delete(ids);
        log.info("deleteBySource: deleted chunks={} filename='{}'", ids.size(), filename);
    }

    private Document toDocument(EmbeddedChunk embedded) {
        DocumentChunk chunk = embedded.chunk();
        Map<String, Object> meta = new HashMap<>(chunk.metadata());
        meta.put("chunkId", chunk.chunkId());
        meta.put("chunkIndex", String.valueOf(chunk.chunkIndex()));
        meta.put("tokenCount", String.valueOf(chunk.tokenCount()));

        return Document.builder()
                .id(chunk.chunkId())
                .text(chunk.content())
                .metadata(meta)
                .build();
    }

    private ScoredChunk toScoredChunk(Document doc) {
        Map<String, String> meta = doc.getMetadata().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())
                ));

        String chunkId   = (String) doc.getMetadata().getOrDefault("chunkId", doc.getId());
        int chunkIndex   = parseIntOrZero(doc.getMetadata().get("chunkIndex"));
        int tokenCount   = parseIntOrZero(doc.getMetadata().get("tokenCount"));
        double score     = Objects.nonNull(doc.getScore()) ? doc.getScore() : 0.0;

        DocumentChunk chunk = new DocumentChunk(chunkId, doc.getText(), chunkIndex, tokenCount, meta);
        return new ScoredChunk(chunk, score);
    }

    private int parseIntOrZero(Object value) {
        if (Objects.isNull(value)) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=PgVectorStoreServiceTest
```
Expected: `Tests run: 6, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/vectorstore/ \
        src/test/java/com/test/rag/service/vectorstore/
git commit -m "feat: implement PgVectorStoreService — upsert, search, deleteBySource via Spring AI VectorStore"
```

---

## Task 6 — `QueryEmbeddingService`: interface + `AnthropicQueryEmbeddingService`

**Files:**
- Overwrite: `src/main/java/com/test/rag/service/queryembedding/QueryEmbeddingService.java`
- Create: `src/main/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingService.java`
- Create: `src/test/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingServiceTest.java`

### 6a — Interface

- [ ] **Step 1: Overwrite stub with interface**

```java
package com.test.rag.service.queryembedding;

public interface QueryEmbeddingService {
    float[] embed(String userQuery);
}
```

### 6b — Tests (RED)

- [ ] **Step 2: Write failing tests**

Create `src/test/java/com/test/rag/service/queryembedding/AnthropicQueryEmbeddingServiceTest.java`:

```java
package com.test.rag.service.queryembedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnthropicQueryEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private QueryEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new AnthropicQueryEmbeddingService(embeddingModel);
    }

    @Test
    void embed_returnsNormalizedVector() {
        when(embeddingModel.embed("hello")).thenReturn(new float[]{3.0f, 4.0f}); // norm=5

        float[] result = service.embed("hello");

        double norm = Math.sqrt(result[0] * result[0] + result[1] * result[1]);
        assertThat(norm).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void embed_preservesDimensions() {
        float[] raw = new float[1024];
        raw[0] = 1.0f;
        when(embeddingModel.embed("query")).thenReturn(raw);

        float[] result = service.embed("query");

        assertThat(result).hasSize(1024);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn test -Dtest=AnthropicQueryEmbeddingServiceTest
```
Expected: `COMPILATION ERROR`.

### 6c — Implementation (GREEN)

- [ ] **Step 4: Create `AnthropicQueryEmbeddingService`**

```java
package com.test.rag.service.queryembedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class AnthropicQueryEmbeddingService implements QueryEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicQueryEmbeddingService.class);
    private static final int LOG_QUERY_MAX_CHARS = 200;

    private final EmbeddingModel embeddingModel;

    public AnthropicQueryEmbeddingService(EmbeddingModel embeddingModel) {
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
        if (norm == 0) return vector;
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = vector[i] / norm;
        return out;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=AnthropicQueryEmbeddingServiceTest
```
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/queryembedding/ \
        src/test/java/com/test/rag/service/queryembedding/
git commit -m "feat: implement AnthropicQueryEmbeddingService with L2 normalisation and telemetry logging"
```

---

## Task 7 — `RetrievalService`: interface + `VectorRetrievalService`

**Files:**
- Overwrite: `src/main/java/com/test/rag/service/retrieval/RetrievalService.java`
- Create: `src/main/java/com/test/rag/service/retrieval/VectorRetrievalService.java`
- Create: `src/test/java/com/test/rag/service/retrieval/VectorRetrievalServiceTest.java`

### 7a — Interface

- [ ] **Step 1: Overwrite stub with interface**

```java
package com.test.rag.service.retrieval;

import com.test.rag.model.ScoredChunk;

import java.util.List;

public interface RetrievalService {
    List<ScoredChunk> retrieve(String userQuery);
}
```

### 7b — Tests (RED)

- [ ] **Step 2: Write failing tests**

Create `src/test/java/com/test/rag/service/retrieval/VectorRetrievalServiceTest.java`:

```java
package com.test.rag.service.retrieval;

import com.test.rag.config.RagProperties;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ScoredChunk;
import com.test.rag.service.queryembedding.QueryEmbeddingService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorRetrievalServiceTest {

    @Mock
    private QueryEmbeddingService queryEmbeddingService;

    @Mock
    private VectorStoreService vectorStoreService;

    private RetrievalService service;
    private RagProperties props;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        props.setTopK(5);
        service = new VectorRetrievalService(queryEmbeddingService, vectorStoreService, props);
    }

    @Test
    void retrieve_callsQueryEmbeddingServiceForTelemetry() {
        when(queryEmbeddingService.embed(anyString())).thenReturn(new float[]{0.5f, 0.5f});
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());

        service.retrieve("What is RAG?");

        verify(queryEmbeddingService).embed("What is RAG?");
    }

    @Test
    void retrieve_passesQueryTextToVectorStoreService() {
        when(queryEmbeddingService.embed(anyString())).thenReturn(new float[]{0.5f});
        when(vectorStoreService.search(eq("What is RAG?"), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.retrieve("What is RAG?");

        verify(vectorStoreService).search(eq("What is RAG?"), anyInt(), anyDouble());
    }

    @Test
    void retrieve_usesTopKFromProperties() {
        props.setTopK(3);
        when(queryEmbeddingService.embed(anyString())).thenReturn(new float[]{0.5f});
        when(vectorStoreService.search(anyString(), eq(3), anyDouble())).thenReturn(List.of());

        service.retrieve("query");

        verify(vectorStoreService).search(anyString(), eq(3), anyDouble());
    }

    @Test
    void retrieve_returnsResultsFromVectorStore() {
        ScoredChunk scored = new ScoredChunk(
                new DocumentChunk("id1", "chunk content", 0, 3, Map.of("filename", "a.pdf")), 0.9);
        when(queryEmbeddingService.embed(anyString())).thenReturn(new float[]{0.5f});
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(scored));

        List<ScoredChunk> result = service.retrieve("query");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).similarityScore()).isEqualTo(0.9);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn test -Dtest=VectorRetrievalServiceTest
```
Expected: `COMPILATION ERROR`.

### 7c — Implementation (GREEN)

- [ ] **Step 4: Create `VectorRetrievalService`**

```java
package com.test.rag.service.retrieval;

import com.test.rag.config.RagProperties;
import com.test.rag.model.ScoredChunk;
import com.test.rag.service.queryembedding.QueryEmbeddingService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorRetrievalService implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(VectorRetrievalService.class);

    private final QueryEmbeddingService queryEmbeddingService;
    private final VectorStoreService vectorStoreService;
    private final RagProperties props;

    public VectorRetrievalService(QueryEmbeddingService queryEmbeddingService,
                                  VectorStoreService vectorStoreService,
                                  RagProperties props) {
        this.queryEmbeddingService = queryEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.props = props;
    }

    @Override
    public List<ScoredChunk> retrieve(String userQuery) {
        // Embed for telemetry/logging — Spring AI VectorStore handles embedding for search
        queryEmbeddingService.embed(userQuery);

        int topK = props.getTopK();
        double threshold = props.getMinSimilarity().doubleValue();

        List<ScoredChunk> results = vectorStoreService.search(userQuery, topK, threshold);

        log.info("Retrieve query='{}' topK={} returned={} scores={}",
                userQuery,
                topK,
                results.size(),
                results.stream().map(c -> String.format("%.3f", c.similarityScore())).toList());

        return results;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=VectorRetrievalServiceTest
```
Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/retrieval/ \
        src/test/java/com/test/rag/service/retrieval/
git commit -m "feat: implement VectorRetrievalService wiring QueryEmbeddingService and VectorStoreService"
```

---

## Task 8 — `ContextBuilderService`: prompt template + interface + `PromptContextBuilderService`

**Files:**
- Create: `src/main/resources/prompts/rag-system.st`
- Overwrite: `src/main/java/com/test/rag/service/context/ContextBuilderService.java`
- Create: `src/main/java/com/test/rag/service/context/PromptContextBuilderService.java`
- Create: `src/test/java/com/test/rag/service/context/PromptContextBuilderServiceTest.java`

### 8a — Prompt template

- [ ] **Step 1: Create `src/main/resources/prompts/rag-system.st`**

```
You are a helpful AI assistant. Answer the user's question using only the context provided below.
If the answer cannot be found in the context, say "I don't have enough information to answer this question."
Always cite your sources using the reference numbers shown, e.g. [1], [2].

Context:
{context}
```

### 8b — Interface

- [ ] **Step 2: Overwrite stub with interface**

```java
package com.test.rag.service.context;

import com.test.rag.model.BuiltContext;
import com.test.rag.model.ScoredChunk;

import java.util.List;

public interface ContextBuilderService {
    BuiltContext build(String userQuery, List<ScoredChunk> scoredChunks);
}
```

### 8c — Tests (RED)

- [ ] **Step 3: Write failing tests**

Create `src/test/java/com/test/rag/service/context/PromptContextBuilderServiceTest.java`:

```java
package com.test.rag.service.context;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptContextBuilderServiceTest {

    private ContextBuilderService service;
    private RagProperties props;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        props.setMaxContextTokens(4096);
        service = new PromptContextBuilderService(props);
    }

    @Test
    void build_systemPromptContainsChunkContent() {
        List<ScoredChunk> chunks = List.of(scored("Paris is the capital of France.", "doc.pdf", 0, 0.9));

        BuiltContext ctx = service.build("What is the capital of France?", chunks);

        assertThat(ctx.systemPrompt()).contains("Paris is the capital of France.");
    }

    @Test
    void build_systemPromptContainsReferenceNumbers() {
        List<ScoredChunk> chunks = List.of(
                scored("Chunk one content.", "a.pdf", 0, 0.9),
                scored("Chunk two content.", "b.pdf", 1, 0.8)
        );

        BuiltContext ctx = service.build("query", chunks);

        assertThat(ctx.systemPrompt()).contains("[1]").contains("[2]");
    }

    @Test
    void build_userMessageIsTheOriginalQuery() {
        BuiltContext ctx = service.build("What is RAG?", List.of(scored("RAG info.", "a.pdf", 0, 0.9)));
        assertThat(ctx.userMessage()).isEqualTo("What is RAG?");
    }

    @Test
    void build_citationsMatchChunks() {
        List<ScoredChunk> chunks = List.of(
                scored("First chunk.", "first.pdf", 2, 0.95),
                scored("Second chunk.", "second.pdf", 5, 0.85)
        );

        BuiltContext ctx = service.build("query", chunks);

        assertThat(ctx.citations()).hasSize(2);
        Citation c1 = ctx.citations().get(0);
        assertThat(c1.ref()).isEqualTo(1);
        assertThat(c1.filename()).isEqualTo("first.pdf");
        assertThat(c1.chunkIndex()).isEqualTo(2);
        assertThat(c1.score()).isEqualTo(0.95);
    }

    @Test
    void build_truncatesLowScoredChunksWhenOverTokenLimit() {
        props.setMaxContextTokens(50); // ~200 chars
        // Each chunk is ~300 chars → only the highest-scored fits
        String bigContent = "X".repeat(300);
        List<ScoredChunk> chunks = List.of(
                scored(bigContent, "high.pdf", 0, 0.95),
                scored(bigContent, "low.pdf",  1, 0.75)
        );

        BuiltContext ctx = service.build("query", chunks);

        // Only citation [1] should be present (highest scored fits; second gets truncated)
        assertThat(ctx.citations()).hasSize(1);
        assertThat(ctx.citations().get(0).filename()).isEqualTo("high.pdf");
    }

    @Test
    void build_emptyChunks_returnsEmptyCitationsAndTemplateSystemPrompt() {
        BuiltContext ctx = service.build("query", List.of());
        assertThat(ctx.citations()).isEmpty();
        assertThat(ctx.systemPrompt()).isNotBlank();
    }

    // --- helper ---
    private ScoredChunk scored(String content, String filename, int chunkIndex, double score) {
        int tokens = (int) Math.ceil(content.length() / 4.0);
        DocumentChunk chunk = new DocumentChunk(
                "id-" + chunkIndex, content, chunkIndex, tokens,
                Map.of("filename", filename, "chunk_index", String.valueOf(chunkIndex)));
        return new ScoredChunk(chunk, score);
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

```bash
mvn test -Dtest=PromptContextBuilderServiceTest
```
Expected: `COMPILATION ERROR`.

### 8d — Implementation (GREEN)

- [ ] **Step 5: Create `PromptContextBuilderService`**

```java
package com.test.rag.service.context;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PromptContextBuilderService implements ContextBuilderService {

    private static final Logger log = LoggerFactory.getLogger(PromptContextBuilderService.class);

    private final RagProperties props;

    public PromptContextBuilderService(RagProperties props) {
        this.props = props;
    }

    @Override
    public BuiltContext build(String userQuery, List<ScoredChunk> scoredChunks) {
        // Highest-scoring chunks first so truncation keeps the most relevant
        List<ScoredChunk> sorted = scoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::similarityScore).reversed())
                .toList();

        int maxTokens = props.getMaxContextTokens();
        StringBuilder contextBlock = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        int usedTokens = 0;
        int ref = 1;

        for (ScoredChunk sc : sorted) {
            String line = "[" + ref + "] " + sc.chunk().content();
            int lineTokens = (int) Math.ceil(line.length() / 4.0);
            if (usedTokens + lineTokens > maxTokens) break;

            contextBlock.append(line).append("\n\n");
            String filename = sc.chunk().metadata().getOrDefault("filename", "unknown");
            citations.add(new Citation(ref, filename, sc.chunk().chunkIndex(), sc.similarityScore()));
            usedTokens += lineTokens;
            ref++;
        }

        PromptTemplate template = new PromptTemplate(new ClassPathResource("prompts/rag-system.st"));
        String systemPrompt = template.render(Map.of("context", contextBlock.toString().strip()));

        log.info("ContextBuilder chunks={} citations={} contextTokens=~{}",
                scoredChunks.size(), citations.size(), usedTokens);

        return new BuiltContext(systemPrompt, userQuery, citations);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
mvn test -Dtest=PromptContextBuilderServiceTest
```
Expected: `Tests run: 6, Failures: 0`

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/prompts/rag-system.st \
        src/main/java/com/test/rag/service/context/ \
        src/test/java/com/test/rag/service/context/
git commit -m "feat: implement PromptContextBuilderService with token-budget truncation and citation numbering"
```

---

## Task 9 — `GenerationService`: interface + `GeminiGenerationService`

**Files:**
- Overwrite: `src/main/java/com/test/rag/service/generation/GenerationService.java`
- Create: `src/main/java/com/test/rag/service/generation/GeminiGenerationService.java`
- Create: `src/test/java/com/test/rag/service/generation/GeminiGenerationServiceTest.java`

### 9a — Interface

- [ ] **Step 1: Overwrite stub with interface**

```java
package com.test.rag.service.generation;

import com.test.rag.model.BuiltContext;
import com.test.rag.model.RagResponse;

public interface GenerationService {
    RagResponse generate(BuiltContext context);
}
```

### 9b — Tests (RED)

- [ ] **Step 2: Write failing tests**

Create `src/test/java/com/test/rag/service/generation/GeminiGenerationServiceTest.java`:

```java
package com.test.rag.service.generation;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.RagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiGenerationServiceTest {

    @Mock
    private ChatClient chatClient;

    private GenerationService service;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        service = new GeminiGenerationService(chatClient, props);
    }

    @Test
    void generate_returnsAnswerFromModel() {
        stubChatClient("Paris is the capital [1].", 42);

        BuiltContext ctx = context("What is the capital?", List.of(
                new Citation(1, "geo.pdf", 0, 0.95)));

        RagResponse response = service.generate(ctx);

        assertThat(response.answer()).isEqualTo("Paris is the capital [1].");
    }

    @Test
    void generate_populatesTotalTokens() {
        stubChatClient("The answer is here.", 150);

        RagResponse response = service.generate(context("q", List.of()));

        assertThat(response.totalTokens()).isEqualTo(150);
    }

    @Test
    void generate_filtersOnlyCitedReferences() {
        // Answer only uses [1], not [2]
        stubChatClient("Answer uses [1] only.", 20);

        BuiltContext ctx = context("query", List.of(
                new Citation(1, "used.pdf", 0, 0.9),
                new Citation(2, "unused.pdf", 1, 0.8)
        ));

        RagResponse response = service.generate(ctx);

        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).filename()).isEqualTo("used.pdf");
    }

    @Test
    void generate_noCitationsInAnswer_returnsEmptyCitations() {
        stubChatClient("Generic answer with no references.", 30);

        BuiltContext ctx = context("query", List.of(
                new Citation(1, "doc.pdf", 0, 0.9)
        ));

        RagResponse response = service.generate(ctx);

        assertThat(response.citations()).isEmpty();
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private void stubChatClient(String answer, long tokens) {
        ChatClient.ChatClientRequest requestMock = mock(ChatClient.ChatClientRequest.class);
        ChatClient.ChatClientRequest.ChatClientRequestSpec specMock =
                mock(ChatClient.ChatClientRequest.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callMock = mock(ChatClient.CallResponseSpec.class);

        AssistantMessage message = new AssistantMessage(answer);
        Generation generation = new Generation(message);
        Usage usage = mock(Usage.class);
        when(usage.getTotalTokens()).thenReturn(tokens);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation), metadata);

        when(chatClient.prompt()).thenReturn(requestMock);
        when(requestMock.system(anyString())).thenReturn(specMock);
        when(specMock.user(anyString())).thenReturn(specMock);
        when(specMock.options(any())).thenReturn(specMock);
        when(specMock.call()).thenReturn(callMock);
        when(callMock.chatResponse()).thenReturn(chatResponse);
    }

    private BuiltContext context(String query, List<Citation> citations) {
        return new BuiltContext("System prompt.", query, citations);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn test -Dtest=GeminiGenerationServiceTest
```
Expected: `COMPILATION ERROR`.

### 9c — Implementation (GREEN)

- [ ] **Step 4: Create `GeminiGenerationService`**

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
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class GeminiGenerationService implements GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GeminiGenerationService.class);

    private final ChatClient chatClient;
    private final RagProperties props;

    public GeminiGenerationService(ChatClient chatClient, RagProperties props) {
        this.chatClient = chatClient;
        this.props = props;
    }

    @Override
    public RagResponse generate(BuiltContext context) {
        long start = System.currentTimeMillis();

        ChatResponse chatResponse = chatClient.prompt()
                .system(context.systemPrompt())
                .user(context.userMessage())
                .options(VertexAiGeminiChatOptions.builder()
                        .temperature(props.getTemperature().floatValue())
                        .maxOutputTokens(props.getMaxOutputTokens())
                        .build())
                .call()
                .chatResponse();

        String answer = chatResponse.getResult().getOutput().getContent();
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

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=GeminiGenerationServiceTest
```
Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/generation/ \
        src/test/java/com/test/rag/service/generation/
git commit -m "feat: implement GeminiGenerationService using Spring AI ChatClient with citation filtering"
```

---

## Task 10 — Wire `RagController` endpoints

**Files:**
- Modify: `src/main/java/com/test/rag/controller/RagController.java`

- [ ] **Step 1: Update `RagController`**

```java
package com.test.rag.controller;

import com.test.rag.model.BuiltContext;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ParsedDocument;
import com.test.rag.model.RagResponse;
import com.test.rag.model.ScoredChunk;
import com.test.rag.service.chunking.ChunkingService;
import com.test.rag.service.context.ContextBuilderService;
import com.test.rag.service.embedding.EmbeddingService;
import com.test.rag.service.generation.GenerationService;
import com.test.rag.service.loader.DocumentLoaderService;
import com.test.rag.service.retrieval.RetrievalService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final DocumentLoaderService documentLoaderService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RetrievalService retrievalService;
    private final ContextBuilderService contextBuilderService;
    private final GenerationService generationService;

    public RagController(DocumentLoaderService documentLoaderService,
                         ChunkingService chunkingService,
                         EmbeddingService embeddingService,
                         VectorStoreService vectorStoreService,
                         RetrievalService retrievalService,
                         ContextBuilderService contextBuilderService,
                         GenerationService generationService) {
        this.documentLoaderService = documentLoaderService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.retrievalService = retrievalService;
        this.contextBuilderService = contextBuilderService;
        this.generationService = generationService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestParam("file") MultipartFile file) {
        ParsedDocument doc = documentLoaderService.load(file);
        List<DocumentChunk> chunks = chunkingService.chunk(doc);
        List<EmbeddedChunk> embedded = embeddingService.embed(chunks);
        vectorStoreService.upsert(embedded);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/query")
    public RagResponse query(@RequestParam("q") String question) {
        List<ScoredChunk> chunks = retrievalService.retrieve(question);
        BuiltContext context = contextBuilderService.build(question, chunks);
        return generationService.generate(context);
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn compile
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run all tests**

```bash
mvn test
```
Expected: All tests pass, no regressions.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/test/rag/controller/RagController.java
git commit -m "feat: implement RagController /ingest and /query endpoints wiring full pipeline"
```

---

## Self-Review

### Spec Coverage Check

| Spec requirement | Task |
|---|---|
| ChunkingService: paragraph→sentence→word split | Task 3 |
| ChunkingService: overlap N tokens | Task 3 |
| ChunkingService: discard under min-chunk-size | Task 3 |
| ChunkingService: SHA-256 chunkId deterministic | Task 3 |
| ChunkingService: inherit metadata, add chunk_index | Task 3 |
| ChunkingService: log source, total chunks, min/max/avg | Task 3 |
| EmbeddingService: Spring AI EmbeddingModel (not raw HTTP) | Task 4 |
| EmbeddingService: batch by batch-size | Task 4 |
| EmbeddingService: L2 normalise | Task 4 |
| EmbeddingService: @Retryable 3 attempts 1s backoff | Task 4 |
| EmbeddingService: log model, tokens, latency | Task 4 |
| VectorStoreService: sole VectorStore importer | Task 5 |
| VectorStoreService: upsert via VectorStore.add | Task 5 |
| VectorStoreService: search with threshold filter | Task 5 |
| VectorStoreService: deleteBySource | Task 5 |
| VectorStoreService: log on all operations | Task 5 |
| QueryEmbeddingService: EmbeddingModel singleton | Task 6 |
| QueryEmbeddingService: normalise | Task 6 |
| QueryEmbeddingService: log query (truncated 200 chars), latency | Task 6 |
| RetrievalService: calls QueryEmbeddingService | Task 7 |
| RetrievalService: topK and threshold from RagProperties | Task 7 |
| RetrievalService: log query, results, scores | Task 7 |
| ContextBuilderService: rag-system.st template | Task 8 |
| ContextBuilderService: numbered [1]…[N] references | Task 8 |
| ContextBuilderService: Citation per chunk | Task 8 |
| ContextBuilderService: truncate to maxContextTokens | Task 8 |
| GenerationService: ChatClient (not raw Gemini SDK) | Task 9 |
| GenerationService: temperature + maxOutputTokens from props | Task 9 |
| GenerationService: filter citations by answer references | Task 9 |
| GenerationService: log tokens, latency | Task 9 |
| RagController: /ingest and /query endpoints | Task 10 |
| DocumentChunk tokenCount field | Task 1 |

All spec requirements covered. No gaps identified.
