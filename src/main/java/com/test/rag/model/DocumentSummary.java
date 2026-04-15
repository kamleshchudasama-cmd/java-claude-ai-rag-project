package com.test.rag.model;

/**
 * Aggregated view of a single ingested document as stored in the vector store.
 * One summary per unique source_id — chunk-level data is rolled up.
 *
 * Fields sourced from Tika-extracted metadata:
 *   contentType, author, createdDate
 *
 * Fields sourced from ingestion metadata (added at upload time):
 *   sourceId, uploadedAt, fileSizeBytes
 *
 * Fields computed across all chunks of the document:
 *   chunkCount, totalTokens
 */
public record DocumentSummary(
        String filename,
        String sourceId,
        String contentType,
        String author,
        String createdDate,
        String uploadedAt,
        long fileSizeBytes,
        int chunkCount,
        int totalTokens
) {}