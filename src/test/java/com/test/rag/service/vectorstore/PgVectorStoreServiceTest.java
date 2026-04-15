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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        Document doc = captor.getValue().get(0);
        // chunkId is stored in metadata for retrieval; document ID is a deterministic UUID
        assertThat(doc.getMetadata()).containsEntry("chunkId", "chunk-id-1");
        String expectedId = UUID.nameUUIDFromBytes(
                "chunk-id-1".getBytes(StandardCharsets.UTF_8)).toString();
        assertThat(doc.getId()).isEqualTo(expectedId);
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

    @Test
    void deleteBySource_chunksFound_callsDeleteWithDocumentIds() {
        Document doc1 = Document.builder()
                .id("doc-id-1")
                .text("Chunk one")
                .metadata(Map.of("filename", "report.pdf"))
                .build();
        Document doc2 = Document.builder()
                .id("doc-id-2")
                .text("Chunk two")
                .metadata(Map.of("filename", "report.pdf"))
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));
        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.captor();

        service.deleteBySource("report.pdf");

        verify(vectorStore).delete(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder("doc-id-1", "doc-id-2");
    }

    @Test
    void search_documentWithNonNumericChunkIndex_defaultsToZero() {
        Document doc = Document.builder()
                .id("chunk-bad-index")
                .text("Some content")
                .metadata(Map.of(
                        "chunkId", "chunk-bad-index",
                        "chunkIndex", "not-a-number",
                        "tokenCount", "10",
                        "filename", "file.pdf"
                ))
                .score(0.80)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<ScoredChunk> result = service.search("query", 5, 0.75);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunk().chunkIndex()).isZero();
    }

    @Test
    void search_documentWithNullTokenCount_defaultsToZero() {
        Document doc = Document.builder()
                .id("chunk-null-tokens")
                .text("Some content")
                .metadata(Map.of(
                        "chunkId", "chunk-null-tokens",
                        "chunkIndex", "2",
                        "filename", "file.pdf"
                ))
                .score(0.85)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<ScoredChunk> result = service.search("query", 5, 0.75);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunk().tokenCount()).isZero();
    }

    // --- helpers ---

    private EmbeddedChunk embeddedChunk(String chunkId, String content, int index) {
        DocumentChunk chunk = new DocumentChunk(
                chunkId, content, index, 3,
                Map.of("filename", "test.pdf", "chunk_index", String.valueOf(index)));
        return new EmbeddedChunk(chunk, new float[]{0.6f, 0.8f});
    }
}
