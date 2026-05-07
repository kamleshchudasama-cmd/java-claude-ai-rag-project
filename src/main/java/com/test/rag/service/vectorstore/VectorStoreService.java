package com.test.rag.service.vectorstore;

import com.test.rag.model.DocumentSummary;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ScoredChunk;

import java.util.List;

/**
 * Single gateway for all PGVector reads and writes.
 * No other service may import VectorStore or run SQL.
 */
public interface VectorStoreService {
    void upsert(List<EmbeddedChunk> chunks);

    /**
     * Searches the vector store using a pre-normalized L2 embedding.
     */
    List<ScoredChunk> search(float[] queryEmbedding, int topK, double threshold);

    /**
     * Deletes all chunks belonging to the given source ID atomically.
     * The source ID is {@code SHA-256(filename + ":" + fileSize)} and is
     * returned in every {@link DocumentSummary} from {@link #listDocuments()}.
     *
     * @return {@code true} if one or more chunks were found and deleted;
     *         {@code false} if no chunks existed for that source ID.
     */
    boolean deleteBySource(String sourceId);

    /**
     * Returns one {@link DocumentSummary} per unique filename stored in the vector store.
     * Chunks are aggregated by filename; chunk count and token totals are rolled up.
     * Results are sorted alphabetically by filename.
     */
    List<DocumentSummary> listDocuments();
}
