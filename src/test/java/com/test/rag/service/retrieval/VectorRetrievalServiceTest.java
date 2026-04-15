package com.test.rag.service.retrieval;

import com.test.rag.config.RagProperties;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ScoredChunk;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorRetrievalServiceTest {

    @Mock
    private VectorStoreService vectorStoreService;

    private RagProperties props;
    private RetrievalService service;

    @BeforeEach
    void setUp() {
        props = new RagProperties(); // defaults: topK=5, minSimilarity=0.75
        service = new VectorRetrievalService(vectorStoreService, props);
    }

    // -------------------------------------------------------------------------
    // Return value — what comes back from vectorStoreService
    // -------------------------------------------------------------------------

    @Test
    void retrieve_noResults_returnsEmptyList() {
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        List<ScoredChunk> result = service.retrieve("What is AI?");

        assertThat(result).isEmpty();
    }

    @Test
    void retrieve_singleResult_returnsSingleScoredChunk() {
        ScoredChunk chunk = scoredChunk("chunk-1", "AI is transformative", 0.91);
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(chunk));

        List<ScoredChunk> result = service.retrieve("What is AI?");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(chunk);
    }

    @Test
    void retrieve_multipleResults_returnsAllChunks() {
        List<ScoredChunk> chunks = List.of(
                scoredChunk("chunk-1", "First result", 0.95),
                scoredChunk("chunk-2", "Second result", 0.88),
                scoredChunk("chunk-3", "Third result", 0.81)
        );
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(chunks);

        List<ScoredChunk> result = service.retrieve("Tell me about AI");

        assertThat(result).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // Query forwarding — the exact string must reach vectorStoreService
    // -------------------------------------------------------------------------

    @Test
    void retrieve_passesExactQueryStringToVectorStore() {
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.captor();

        service.retrieve("What is machine learning?");

        verify(vectorStoreService).search(queryCaptor.capture(), anyInt(), anyDouble());
        assertThat(queryCaptor.getValue()).isEqualTo("What is machine learning?");
    }

    @Test
    void retrieve_queryWithSpecialCharacters_passedAsIs() {
        String query = "What's the #1 use-case? (AI/ML & NLP)";
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.captor();

        service.retrieve(query);

        verify(vectorStoreService).search(queryCaptor.capture(), anyInt(), anyDouble());
        assertThat(queryCaptor.getValue()).isEqualTo(query);
    }

    @Test
    void retrieve_queryWithLeadingAndTrailingSpaces_passedAsIs() {
        String query = "  neural networks  ";
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.captor();

        service.retrieve(query);

        verify(vectorStoreService).search(queryCaptor.capture(), anyInt(), anyDouble());
        assertThat(queryCaptor.getValue()).isEqualTo(query);
    }

    // -------------------------------------------------------------------------
    // Properties — topK and minSimilarity must come from RagProperties
    // -------------------------------------------------------------------------

    @Test
    void retrieve_usesDefaultTopKFromProperties() {
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.captor();

        service.retrieve("query");

        verify(vectorStoreService).search(anyString(), topKCaptor.capture(), anyDouble());
        assertThat(topKCaptor.getValue()).isEqualTo(5);
    }

    @Test
    void retrieve_usesDefaultThresholdFromProperties() {
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<Double> thresholdCaptor = ArgumentCaptor.captor();

        service.retrieve("query");

        verify(vectorStoreService).search(anyString(), anyInt(), thresholdCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isEqualTo(0.75);
    }

    @Test
    void retrieve_customTopK_passedToVectorStore() {
        props.setTopK(10);
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.captor();

        service.retrieve("query");

        verify(vectorStoreService).search(anyString(), topKCaptor.capture(), anyDouble());
        assertThat(topKCaptor.getValue()).isEqualTo(10);
    }

    @Test
    void retrieve_customThreshold_passedToVectorStore() {
        props.setMinSimilarity(new BigDecimal("0.90"));
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<Double> thresholdCaptor = ArgumentCaptor.captor();

        service.retrieve("query");

        verify(vectorStoreService).search(anyString(), anyInt(), thresholdCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isEqualTo(0.90);
    }

    @Test
    void retrieve_topK1_passesSingleResultLimit() {
        props.setTopK(1);
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(scoredChunk("chunk-1", "Only result", 0.92)));
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.captor();

        service.retrieve("query");

        verify(vectorStoreService).search(anyString(), topKCaptor.capture(), anyDouble());
        assertThat(topKCaptor.getValue()).isEqualTo(1);
    }

    @Test
    void retrieve_thresholdZero_passesZeroToVectorStore() {
        props.setMinSimilarity(BigDecimal.ZERO);
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        ArgumentCaptor<Double> thresholdCaptor = ArgumentCaptor.captor();

        service.retrieve("query");

        verify(vectorStoreService).search(anyString(), anyInt(), thresholdCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Result integrity — order, scores, and chunk data must not be altered
    // -------------------------------------------------------------------------

    @Test
    void retrieve_resultsReturnedInSameOrderAsVectorStore() {
        ScoredChunk first  = scoredChunk("chunk-1", "High score result",   0.95);
        ScoredChunk second = scoredChunk("chunk-2", "Medium score result", 0.85);
        ScoredChunk third  = scoredChunk("chunk-3", "Lower score result",  0.77);
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(first, second, third));

        List<ScoredChunk> result = service.retrieve("query");

        assertThat(result.get(0).chunk().chunkId()).isEqualTo("chunk-1");
        assertThat(result.get(1).chunk().chunkId()).isEqualTo("chunk-2");
        assertThat(result.get(2).chunk().chunkId()).isEqualTo("chunk-3");
    }

    @Test
    void retrieve_similarityScoresAreNotModified() {
        ScoredChunk chunk = scoredChunk("chunk-1", "Some content", 0.876);
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(chunk));

        List<ScoredChunk> result = service.retrieve("query");

        assertThat(result.get(0).similarityScore()).isEqualTo(0.876);
    }

    @Test
    void retrieve_chunkContentAndMetadataAreNotModified() {
        DocumentChunk originalChunk = new DocumentChunk(
                "chunk-42", "Original content text", 3, 15,
                Map.of("filename", "report.pdf", "chunk_index", "3")
        );
        ScoredChunk scored = new ScoredChunk(originalChunk, 0.91);
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(scored));

        List<ScoredChunk> result = service.retrieve("query");

        DocumentChunk returned = result.get(0).chunk();
        assertThat(returned.chunkId()).isEqualTo("chunk-42");
        assertThat(returned.content()).isEqualTo("Original content text");
        assertThat(returned.chunkIndex()).isEqualTo(3);
        assertThat(returned.tokenCount()).isEqualTo(15);
        assertThat(returned.metadata()).containsEntry("filename", "report.pdf");
    }

    // -------------------------------------------------------------------------
    // Exception propagation — no swallowing of errors from vectorStoreService
    // -------------------------------------------------------------------------

    @Test
    void retrieve_vectorStoreThrowsRuntimeException_propagatesToCaller() {
        when(vectorStoreService.search(anyString(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThatThrownBy(() -> service.retrieve("query"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB connection lost");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ScoredChunk scoredChunk(String chunkId, String content, double score) {
        DocumentChunk chunk = new DocumentChunk(
                chunkId, content, 0,
                (int) Math.ceil(content.length() / 4.0),
                Map.of("filename", "test.pdf")
        );
        return new ScoredChunk(chunk, score);
    }
}
