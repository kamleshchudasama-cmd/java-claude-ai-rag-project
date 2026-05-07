package com.test.rag.service.vectorstore;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgVectorStoreServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private VectorStoreService service;

    @BeforeEach
    void setUp() {
        service = new PgVectorStoreService(vectorStore, jdbcTemplate, objectMapper);
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
        Document doc = captor.getValue().get(0);
        // chunkId is stored in metadata for retrieval; document ID is a deterministic UUID
        assertThat(doc.getMetadata()).containsEntry("chunkId", "chunk-id-1");
        String expectedId = UUID.nameUUIDFromBytes(
                "chunk-id-1".getBytes(StandardCharsets.UTF_8)).toString();
        assertThat(doc.getId()).isEqualTo(expectedId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returnsEmptyListWhenNoResults() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<ScoredChunk> result = service.search(new float[]{0.1f, 0.2f}, 5, BigDecimal.valueOf(0.75));

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returnsMappedScoredChunks() {
        DocumentChunk chunk = new DocumentChunk("chunk-id-1", "Chunk content", 0, 3,
                Map.of("chunkId", "chunk-id-1", "filename", "test.pdf"));
        ScoredChunk scoredChunk = new ScoredChunk(chunk, BigDecimal.valueOf(0.92));
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(scoredChunk));

        List<ScoredChunk> result = service.search(new float[]{0.1f, 0.2f}, 5, BigDecimal.valueOf(0.75));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).similarityScore()).isEqualTo(BigDecimal.valueOf(0.92));
        assertThat(result.get(0).chunk().content()).isEqualTo("Chunk content");
        assertThat(result.get(0).chunk().chunkId()).isEqualTo("chunk-id-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_passesTopKAndThresholdToQuery() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.captor();

        service.search(new float[]{0.1f, 0.2f}, 3, BigDecimal.valueOf(0.8));

        verify(jdbcTemplate).query(any(String.class), any(RowMapper.class),
                paramsCaptor.capture());
        Object[] params = paramsCaptor.getValue();
        // params: vectorStr, vectorStr, threshold.doubleValue(), vectorStr, topK
        assertThat(params[2]).isEqualTo(0.8);
        assertThat(params[4]).isEqualTo(3);
    }

    @Test
    void deleteBySource_callsFilterBasedDeleteWithSourceId() {
        // Stub the count query to return 1 (chunks exist) so delete proceeds
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any()))
                .thenReturn(1);
        ArgumentCaptor<Filter.Expression> filterCaptor = ArgumentCaptor.captor();

        service.deleteBySource("source-123");

        verify(vectorStore, never()).delete(anyList());
        verify(vectorStore).delete(filterCaptor.capture());
        // The captured filter should encode the eq("source_id", "source-123") expression
        assertThat(filterCaptor.getValue()).isNotNull();
    }

    @Test
    void deleteBySource_returnsFalse_whenNoChunksExist() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any()))
                .thenReturn(0);

        boolean result = service.deleteBySource("missing.pdf");

        assertThat(result).isFalse();
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    void deleteBySource_returnsTrue_whenChunksExist() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any()))
                .thenReturn(3);

        boolean result = service.deleteBySource("report.pdf");

        assertThat(result).isTrue();
        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    void deleteBySource_doesNotCallSimilaritySearch() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any()))
                .thenReturn(2);

        service.deleteBySource("report.pdf");

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_doesNotCallVectorStoreSimilaritySearch() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.search(new float[]{0.1f, 0.2f}, 5, BigDecimal.valueOf(0.75));

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    // --- helpers ---

    private EmbeddedChunk embeddedChunk(String chunkId, String content, int index) {
        DocumentChunk chunk = new DocumentChunk(
                chunkId, content, index, 3,
                Map.of("filename", "test.pdf", "chunk_index", String.valueOf(index)));
        return new EmbeddedChunk(chunk, new float[]{0.6f, 0.8f});
    }
}
