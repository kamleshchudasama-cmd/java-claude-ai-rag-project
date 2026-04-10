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

    @Test
    void chunk_nullDocument_throwsNullPointerException() {
        assertThatThrownBy(() -> service.chunk(null))
                .isInstanceOf(NullPointerException.class);
    }
}
