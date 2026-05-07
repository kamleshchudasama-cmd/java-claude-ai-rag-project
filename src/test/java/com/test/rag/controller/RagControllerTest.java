package com.test.rag.controller;

import com.test.rag.controller.GlobalExceptionHandler;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    @Mock private DocumentLoaderService documentLoaderService;
    @Mock private ChunkingService chunkingService;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private RetrievalService retrievalService;
    @Mock private ContextBuilderService contextBuilderService;
    @Mock private GenerationService generationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RagController controller = new RagController(
                documentLoaderService, chunkingService, embeddingService,
                vectorStoreService, retrievalService, contextBuilderService, generationService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================================
    // POST /api/rag/ingest — HTTP response
    // =========================================================================

    @Test
    void ingest_validFile_returns200Ok() throws Exception {
        givenIngestPipelineSucceeds();

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()))
               .andExpect(status().isOk());
    }

    @Test
    void ingest_validFile_responseBodyIsEmpty() throws Exception {
        givenIngestPipelineSucceeds();

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()))
               .andExpect(content().string(""));
    }

    @Test
    void ingest_missingFileParam_returns400BadRequest() throws Exception {
        mockMvc.perform(multipart("/api/rag/ingest"))
               .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // POST /api/rag/ingest — pipeline orchestration
    // =========================================================================

    @Test
    void ingest_passesUploadedFileToDocumentLoaderService() throws Exception {
        givenIngestPipelineSucceeds();

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()));

        verify(documentLoaderService).load(any(MultipartFile.class));
    }

    @Test
    void ingest_passesDocumentLoaderOutputToChunkingService() throws Exception {
        ParsedDocument parsedDoc = parsedDocument();
        given(documentLoaderService.load(any(MultipartFile.class))).willReturn(parsedDoc);
        given(chunkingService.chunk(parsedDoc)).willReturn(List.of(chunk("c1")));
        given(embeddingService.embed(any())).willReturn(List.of());

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()));

        verify(chunkingService).chunk(parsedDoc);
    }

    @Test
    void ingest_passesChunkingOutputToEmbeddingService() throws Exception {
        List<DocumentChunk> chunks = List.of(chunk("c1"), chunk("c2"));
        given(documentLoaderService.load(any(MultipartFile.class))).willReturn(parsedDocument());
        given(chunkingService.chunk(any())).willReturn(chunks);
        given(embeddingService.embed(chunks)).willReturn(List.of());

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()));

        verify(embeddingService).embed(chunks);
    }

    @Test
    void ingest_passesEmbeddingOutputToVectorStoreService() throws Exception {
        List<EmbeddedChunk> embedded = List.of(embeddedChunk("c1"));
        given(documentLoaderService.load(any(MultipartFile.class))).willReturn(parsedDocument());
        given(chunkingService.chunk(any())).willReturn(List.of(chunk("c1")));
        given(embeddingService.embed(any())).willReturn(embedded);

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()));

        verify(vectorStoreService).upsert(embedded);
    }

    @Test
    void ingest_servicesInvokedInCorrectPipelineOrder() throws Exception {
        givenIngestPipelineSucceeds();

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()));

        InOrder order = inOrder(documentLoaderService, chunkingService, embeddingService, vectorStoreService);
        order.verify(documentLoaderService).load(any(MultipartFile.class));
        order.verify(chunkingService).chunk(any());
        order.verify(embeddingService).embed(any());
        order.verify(vectorStoreService).upsert(any());
    }

    // =========================================================================
    // POST /api/rag/ingest — error handling
    // =========================================================================

    @Test
    void ingest_documentLoaderThrows_returns500() throws Exception {
        given(documentLoaderService.load(any(MultipartFile.class))).willThrow(new RuntimeException("Parse failed"));

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()))
               .andExpect(status().isInternalServerError());
    }

    @Test
    void ingest_chunkingServiceThrows_returns500() throws Exception {
        given(documentLoaderService.load(any(MultipartFile.class))).willReturn(parsedDocument());
        given(chunkingService.chunk(any())).willThrow(new RuntimeException("Chunk failed"));

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()))
               .andExpect(status().isInternalServerError());
    }

    @Test
    void ingest_embeddingServiceThrows_returns500() throws Exception {
        given(documentLoaderService.load(any(MultipartFile.class))).willReturn(parsedDocument());
        given(chunkingService.chunk(any())).willReturn(List.of(chunk("c1")));
        given(embeddingService.embed(any())).willThrow(new RuntimeException("Embed failed"));

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()))
               .andExpect(status().isInternalServerError());
    }

    @Test
    void ingest_vectorStoreThrows_returns500() throws Exception {
        given(documentLoaderService.load(any(MultipartFile.class))).willReturn(parsedDocument());
        given(chunkingService.chunk(any())).willReturn(List.of(chunk("c1")));
        given(embeddingService.embed(any())).willReturn(List.of(embeddedChunk("c1")));
        willThrow(new RuntimeException("DB write failed")).given(vectorStoreService).upsert(any());

        mockMvc.perform(multipart("/api/rag/ingest").file(mockFile()))
               .andExpect(status().isInternalServerError());
    }

    // =========================================================================
    // POST /api/rag/query — HTTP response
    // =========================================================================

    @Test
    void query_validQuestion_returns200Ok() throws Exception {
        givenQueryPipelineSucceeds("The answer");

        mockMvc.perform(post("/api/rag/query").param("q", "What is AI?"))
               .andExpect(status().isOk());
    }

    @Test
    void query_missingQParam_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/rag/query"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void query_responseContentTypeIsApplicationJson() throws Exception {
        givenQueryPipelineSucceeds("The answer");

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    // =========================================================================
    // POST /api/rag/query — response body serialization
    // =========================================================================

    @Test
    void query_responseContainsAnswerField() throws Exception {
        givenQueryPipelineSucceeds("Machine learning is a subset of AI.");

        mockMvc.perform(post("/api/rag/query").param("q", "What is ML?"))
               .andExpect(jsonPath("$.answer").value("Machine learning is a subset of AI."));
    }

    @Test
    void query_responseContainsTotalTokensField() throws Exception {
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any())).willReturn(builtContext());
        given(generationService.generate(any())).willReturn(new RagResponse("answer", List.of(), 300));

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(jsonPath("$.totalTokens").value(300));
    }

    @Test
    void query_responseContainsCitationsArray() throws Exception {
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any())).willReturn(builtContext());
        given(generationService.generate(any())).willReturn(
                new RagResponse("answer", List.of(), 100));

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(jsonPath("$.citations").isArray());
    }

    @Test
    void query_citationFieldsSerializedCorrectly() throws Exception {
        Citation citation = new Citation(1, "report.pdf", 2, java.math.BigDecimal.valueOf(0.91), "Some chunk text");
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any())).willReturn(builtContext());
        given(generationService.generate(any())).willReturn(
                new RagResponse("answer [1]", List.of(citation), 150));

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(jsonPath("$.citations[0].ref").value(1))
               .andExpect(jsonPath("$.citations[0].filename").value("report.pdf"))
               .andExpect(jsonPath("$.citations[0].chunkIndex").value(2))
               .andExpect(jsonPath("$.citations[0].score").value(0.91))
               .andExpect(jsonPath("$.citations[0].chunkText").value("Some chunk text"));
    }

    // =========================================================================
    // POST /api/rag/query — pipeline orchestration
    // =========================================================================

    @Test
    void query_passesQuestionToRetrievalService() throws Exception {
        givenQueryPipelineSucceeds("answer");

        mockMvc.perform(post("/api/rag/query").param("q", "What is deep learning?"));

        verify(retrievalService).retrieve("What is deep learning?");
    }

    @Test
    void query_passesRetrievedChunksAndQuestionToContextBuilder() throws Exception {
        List<ScoredChunk> chunks = List.of(scoredChunk("c1", 0.91));
        given(retrievalService.retrieve(anyString())).willReturn(chunks);
        given(contextBuilderService.build(eq("What is AI?"), eq(chunks))).willReturn(builtContext());
        given(generationService.generate(any())).willReturn(ragResponse("answer"));

        mockMvc.perform(post("/api/rag/query").param("q", "What is AI?"));

        verify(contextBuilderService).build("What is AI?", chunks);
    }

    @Test
    void query_passesBuiltContextToGenerationService() throws Exception {
        BuiltContext context = builtContext();
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any())).willReturn(context);
        given(generationService.generate(context)).willReturn(ragResponse("answer"));

        mockMvc.perform(post("/api/rag/query").param("q", "question"));

        verify(generationService).generate(context);
    }

    @Test
    void query_returnsExactResponseFromGenerationService() throws Exception {
        RagResponse expected = new RagResponse("Exact answer from Gemini", List.of(), 250);
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any())).willReturn(builtContext());
        given(generationService.generate(any())).willReturn(expected);

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(jsonPath("$.answer").value("Exact answer from Gemini"))
               .andExpect(jsonPath("$.totalTokens").value(250));
    }

    // =========================================================================
    // POST /api/rag/query — error handling
    // =========================================================================

    @Test
    void query_retrievalServiceThrows_returns500() throws Exception {
        given(retrievalService.retrieve(anyString()))
                .willThrow(new RuntimeException("Vector DB unavailable"));

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(status().isInternalServerError());
    }

    @Test
    void query_contextBuilderThrows_returns500() throws Exception {
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any()))
                .willThrow(new RuntimeException("Template error"));

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(status().isInternalServerError());
    }

    @Test
    void query_generationServiceThrows_returns500() throws Exception {
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any())).willReturn(builtContext());
        given(generationService.generate(any()))
                .willThrow(new RuntimeException("Gemini API error"));

        mockMvc.perform(post("/api/rag/query").param("q", "question"))
               .andExpect(status().isInternalServerError());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void givenIngestPipelineSucceeds() {
        given(documentLoaderService.load(any(MultipartFile.class))).willReturn(parsedDocument());
        given(chunkingService.chunk(any())).willReturn(List.of(chunk("c1")));
        given(embeddingService.embed(any())).willReturn(List.of(embeddedChunk("c1")));
    }

    private void givenQueryPipelineSucceeds(String answer) {
        given(retrievalService.retrieve(anyString())).willReturn(List.of());
        given(contextBuilderService.build(anyString(), any())).willReturn(builtContext());
        given(generationService.generate(any())).willReturn(ragResponse(answer));
    }

    private MockMultipartFile mockFile() {
        return new MockMultipartFile("file", "test.txt", "text/plain",
                "File content".getBytes());
    }

    private ParsedDocument parsedDocument() {
        return new ParsedDocument("Parsed content",
                Map.of("filename", "test.txt"), "source-id-abc");
    }

    private DocumentChunk chunk(String chunkId) {
        return new DocumentChunk(chunkId, "Chunk content", 0, 5,
                Map.of("filename", "test.txt"));
    }

    private EmbeddedChunk embeddedChunk(String chunkId) {
        return new EmbeddedChunk(chunk(chunkId), new float[]{0.6f, 0.8f});
    }

    private ScoredChunk scoredChunk(String chunkId, double score) {
        return new ScoredChunk(chunk(chunkId), java.math.BigDecimal.valueOf(score));
    }

    private BuiltContext builtContext() {
        return new BuiltContext("You are a grounded assistant.", "User question", List.of());
    }

    private RagResponse ragResponse(String answer) {
        return new RagResponse(answer, List.of(), 100);
    }
}