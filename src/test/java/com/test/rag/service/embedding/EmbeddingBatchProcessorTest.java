package com.test.rag.service.embedding;

import com.test.rag.exception.EmbeddingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingBatchProcessorTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingBatchProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EmbeddingBatchProcessor(embeddingModel);
    }

    // =========================================================================
    // Single chunk — basic output
    // =========================================================================

    @Test
    void embedBatch_singleChunk_returnsOneEmbeddedChunk() {
        givenModelReturns(new float[]{0.6f, 0.8f});

        List<EmbeddedChunk> result = processor.embedBatch(List.of(chunk("c1", "Hello world")));

        assertThat(result).hasSize(1);
    }

    @Test
    void embedBatch_singleChunk_preservesOriginalChunkInOutput() {
        DocumentChunk original = chunk("c1", "Hello world");
        givenModelReturns(new float[]{0.6f, 0.8f});

        List<EmbeddedChunk> result = processor.embedBatch(List.of(original));

        assertThat(result.get(0).chunk()).isEqualTo(original);
    }

    // =========================================================================
    // L2 normalization
    // =========================================================================

    @Test
    void embedBatch_rawVector_isL2Normalized() {
        givenModelReturns(new float[]{3.0f, 4.0f});

        List<EmbeddedChunk> result = processor.embedBatch(List.of(chunk("c1", "text")));

        float[] v = result.get(0).embedding();
        double norm = Math.sqrt((double) v[0] * v[0] + (double) v[1] * v[1]);
        assertThat(norm).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void embedBatch_knownVector_producesCorrectNormalizedValues() {
        givenModelReturns(new float[]{3.0f, 4.0f});   // norm=5 → [0.6, 0.8]

        List<EmbeddedChunk> result = processor.embedBatch(List.of(chunk("c1", "text")));

        float[] v = result.get(0).embedding();
        assertThat(v[0]).isCloseTo(0.6f, within(0.0001f));
        assertThat(v[1]).isCloseTo(0.8f, within(0.0001f));
    }

    @Test
    void embedBatch_zeroVector_returnedUnchanged() {
        givenModelReturns(new float[]{0.0f, 0.0f});

        List<EmbeddedChunk> result = processor.embedBatch(List.of(chunk("c1", "text")));

        assertThat(result.get(0).embedding()).containsExactly(0.0f, 0.0f);
    }

    // =========================================================================
    // Multiple chunks — count and ordering
    // =========================================================================

    @Test
    void embedBatch_multipleChunks_outputCountMatchesInput() {
        givenModelReturns(new float[]{1.0f, 0.0f}, new float[]{0.0f, 1.0f}, new float[]{0.6f, 0.8f});

        List<EmbeddedChunk> result = processor.embedBatch(List.of(
                chunk("c1", "Alpha"), chunk("c2", "Beta"), chunk("c3", "Gamma")));

        assertThat(result).hasSize(3);
    }

    @Test
    void embedBatch_multipleChunks_chunkOrderPreserved() {
        givenModelReturns(new float[]{1.0f, 0.0f}, new float[]{0.0f, 1.0f}, new float[]{0.6f, 0.8f});

        List<EmbeddedChunk> result = processor.embedBatch(List.of(
                chunk("first", "Alpha"), chunk("second", "Beta"), chunk("third", "Gamma")));

        assertThat(result.get(0).chunk().chunkId()).isEqualTo("first");
        assertThat(result.get(1).chunk().chunkId()).isEqualTo("second");
        assertThat(result.get(2).chunk().chunkId()).isEqualTo("third");
    }

    // =========================================================================
    // Exception handling
    // =========================================================================

    @Test
    void embedBatch_tooManyRequestsException_rethrownAsIs() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, null, null);
        when(embeddingModel.embedForResponse(anyList())).thenThrow(ex);

        assertThatThrownBy(() -> processor.embedBatch(List.of(chunk("c1", "text"))))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
    }

    @Test
    void embedBatch_resourceAccessException_rethrownAsIs() {
        when(embeddingModel.embedForResponse(anyList()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> processor.embedBatch(List.of(chunk("c1", "text"))))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessage("Connection refused");
    }

    @Test
    void embedBatch_otherRuntimeException_wrappedInEmbeddingException() {
        when(embeddingModel.embedForResponse(anyList()))
                .thenThrow(new RuntimeException("Unexpected API error"));

        assertThatThrownBy(() -> processor.embedBatch(List.of(chunk("c1", "text"))))
                .isInstanceOf(EmbeddingException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void embedBatch_otherRuntimeException_messageContainsBatchSize() {
        when(embeddingModel.embedForResponse(anyList()))
                .thenThrow(new RuntimeException("API error"));

        assertThatThrownBy(() -> processor.embedBatch(
                List.of(chunk("c1", "a"), chunk("c2", "b"))))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("2");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private DocumentChunk chunk(String id, String content) {
        return new DocumentChunk(id, content, 0,
                (int) Math.ceil(content.length() / 4.0), Map.of("filename", "test.txt"));
    }

    private void givenModelReturns(float[]... vectors) {
        List<Embedding> embeddings = Arrays.stream(vectors)
                .map(v -> {
                    Embedding e = mock(Embedding.class);
                    when(e.getOutput()).thenReturn(v);
                    return e;
                })
                .toList();

        EmbeddingResponse response = mock(EmbeddingResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.getResults()).thenReturn(embeddings);
        when(embeddingModel.embedForResponse(anyList())).thenReturn(response);
    }
}
