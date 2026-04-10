package com.test.rag.service.embedding;

import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;

import java.util.List;

public interface EmbeddingService {
    List<EmbeddedChunk> embed(List<DocumentChunk> chunks);
}
