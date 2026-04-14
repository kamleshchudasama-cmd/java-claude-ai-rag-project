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