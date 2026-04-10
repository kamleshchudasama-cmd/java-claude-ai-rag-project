package com.test.rag.controller;

import com.test.rag.model.BuiltContext;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST entry points for document ingestion and question answering.
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final DocumentLoaderService documentLoaderService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RetrievalService retrievalService;
    private final ContextBuilderService contextBuilderService;
    private final GenerationService generationService;

    public RagController(DocumentLoaderService documentLoaderService,
                         ChunkingService chunkingService,
                         EmbeddingService embeddingService,
                         VectorStoreService vectorStoreService,
                         RetrievalService retrievalService,
                         ContextBuilderService contextBuilderService,
                         GenerationService generationService) {
        this.documentLoaderService = documentLoaderService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.retrievalService = retrievalService;
        this.contextBuilderService = contextBuilderService;
        this.generationService = generationService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestParam("file") MultipartFile file) {
        ParsedDocument doc = documentLoaderService.load(file);
        List<DocumentChunk> chunks = chunkingService.chunk(doc);
        List<EmbeddedChunk> embedded = embeddingService.embed(chunks);
        vectorStoreService.upsert(embedded);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/query")
    public RagResponse query(@RequestParam("q") String question) {
        List<ScoredChunk> chunks = retrievalService.retrieve(question);
        BuiltContext context = contextBuilderService.build(question, chunks);
        return generationService.generate(context);
    }
}

