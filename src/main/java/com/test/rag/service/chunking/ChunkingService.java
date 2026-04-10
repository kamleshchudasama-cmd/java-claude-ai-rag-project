package com.test.rag.service.chunking;

import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ParsedDocument;

import java.util.List;

public interface ChunkingService {
    List<DocumentChunk> chunk(ParsedDocument document);
}
