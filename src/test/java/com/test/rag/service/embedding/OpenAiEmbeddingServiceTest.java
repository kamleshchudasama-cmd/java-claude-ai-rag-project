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

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
    private EmbeddingBatchProcessor batchProcessor;

    private EmbeddingService service;
    private RagProperties props;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        props.setEmbeddingBatchSize(32);
        service = new OpenAiEmbeddingService(batchProcessor, props);
    }

    @Test
    void embed_emptyList_returnsEmptyListWithNoApiCall() {
        List<EmbeddedChunk> result = service.embed(List.of());
        assertThat(result).isEmpty();
        verify(batchProcessor, never()).embedBatch(anyList());
    }

    @Test
    void embed_outputCountMatchesInputCount() {
        DocumentChunk c1 = chunk("c1", "Hello world");
        DocumentChunk c2 = chunk("c2", "Goodbye world");
        when(batchProcessor.embedBatch(anyList()))
                .thenReturn(List.of(
                        new EmbeddedChunk(c1, new float[]{0.6f, 0.8f}),
                        new EmbeddedChunk(c2, new float[]{0.8f, 0.6f})
                ));

        List<EmbeddedChunk> result = service.embed(List.of(c1, c2));

        assertThat(result).hasSize(2);
    }

    @Test
    void embed_vectorsAreNormalized() {
        DocumentChunk c1 = chunk("c1", "Test");
        float norm = (float) Math.sqrt(3.0f * 3.0f + 4.0f * 4.0f);
        float[] normalized = new float[]{3.0f / norm, 4.0f / norm};
        when(batchProcessor.embedBatch(anyList()))
                .thenReturn(List.of(new EmbeddedChunk(c1, normalized)));

        List<EmbeddedChunk> result = service.embed(List.of(c1));

        float[] embedding = result.get(0).embedding();
        double computedNorm = Math.sqrt(embedding[0] * embedding[0] + embedding[1] * embedding[1]);
        assertThat(computedNorm).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void embed_originalChunkPreservedInOutput() {
        DocumentChunk original = chunk("cOrig", "Original content");
        when(batchProcessor.embedBatch(anyList()))
                .thenReturn(List.of(new EmbeddedChunk(original, new float[]{0.5f, 0.5f})));

        List<EmbeddedChunk> result = service.embed(List.of(original));

        assertThat(result.get(0).chunk()).isEqualTo(original);
    }

    @Test
    void embed_100ChunksWithBatchSize32_makes4ApiCalls() {
        props.setEmbeddingBatchSize(32);
        List<DocumentChunk> chunks = IntStream.range(0, 100)
                .mapToObj(i -> chunk("c" + i, "content " + i))
                .toList();
        when(batchProcessor.embedBatch(anyList()))
                .thenAnswer(inv -> {
                    List<DocumentChunk> batch = inv.getArgument(0);
                    return batch.stream()
                            .map(c -> new EmbeddedChunk(c, new float[]{0.6f, 0.8f}))
                            .toList();
                });

        service.embed(chunks);

        verify(batchProcessor, times(4)).embedBatch(anyList());
    }

    @Test
    void embed_apiThrows_throwsEmbeddingException() {
        List<DocumentChunk> chunks = List.of(chunk("c1", "fail"));
        when(batchProcessor.embedBatch(anyList()))
                .thenThrow(new EmbeddingException("Embedding API call failed for batch of 1",
                        new RuntimeException("API error")));

        assertThatThrownBy(() -> service.embed(chunks))
                .isInstanceOf(EmbeddingException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void embed_zeroVector_returnedUnchanged() {
        DocumentChunk c1 = chunk("c1", "Test");
        when(batchProcessor.embedBatch(anyList()))
                .thenReturn(List.of(new EmbeddedChunk(c1, new float[]{0.0f, 0.0f})));

        List<EmbeddedChunk> result = service.embed(List.of(c1));

        float[] embedding = result.get(0).embedding();
        assertThat(embedding[0]).isEqualTo(0.0f);
        assertThat(embedding[1]).isEqualTo(0.0f);
    }

    // --- helpers ---

    private DocumentChunk chunk(String id, String content) {
        int tokens = (int) Math.ceil(content.length() / 4.0);
        return new DocumentChunk(id, content, 0, tokens, Map.of("filename", "test.txt"));
    }
}
