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
