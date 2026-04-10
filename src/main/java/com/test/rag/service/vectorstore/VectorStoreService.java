package com.test.rag.service.vectorstore;

import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ScoredChunk;

import java.util.List;

/**
 * Single gateway for all PGVector reads and writes.
 * No other service may import VectorStore or run SQL.
 * search() accepts the original query text; Spring AI VectorStore embeds it internally.
 */
public interface VectorStoreService {
    void upsert(List<EmbeddedChunk> chunks);
    List<ScoredChunk> search(String query, int topK, double threshold);
    void deleteBySource(String filename);
}
