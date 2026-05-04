package com.test.rag.config;

import com.test.rag.service.chunking.ChunkingService;
import com.test.rag.service.context.ContextBuilderService;
import com.test.rag.service.embedding.EmbeddingService;
import com.test.rag.service.generation.GenerationService;
import com.test.rag.service.loader.DocumentLoaderService;
import com.test.rag.service.retrieval.RetrievalService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.test.rag.controller.RagController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagController.class)
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private DocumentLoaderService documentLoaderService;
    @MockBean private ChunkingService chunkingService;
    @MockBean private EmbeddingService embeddingService;
    @MockBean private VectorStoreService vectorStoreService;
    @MockBean private RetrievalService retrievalService;
    @MockBean private ContextBuilderService contextBuilderService;
    @MockBean private GenerationService generationService;

    @Test
    void corsAllowsAngularDevOrigin() throws Exception {
        mockMvc.perform(options("/api/rag/documents")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
