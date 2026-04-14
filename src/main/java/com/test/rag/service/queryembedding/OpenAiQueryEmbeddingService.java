package com.test.rag.service.queryembedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class OpenAiQueryEmbeddingService implements QueryEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQueryEmbeddingService.class);
    private static final int LOG_QUERY_MAX_CHARS = 200;

    private final EmbeddingModel embeddingModel;

    public OpenAiQueryEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String userQuery) {
        long start = System.currentTimeMillis();
        float[] raw = embeddingModel.embed(userQuery);
        float[] normalized = normalize(raw);

        String truncated = userQuery.length() > LOG_QUERY_MAX_CHARS
                ? userQuery.substring(0, LOG_QUERY_MAX_CHARS) + "…"
                : userQuery;
        log.info("QueryEmbedding query='{}' dims={} latencyMs={}",
                truncated, normalized.length, System.currentTimeMillis() - start);

        return normalized;
    }

    private float[] normalize(float[] vector) {
        float sumSq = 0;
        for (float v : vector) sumSq += v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm < 1e-6f) return vector;
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = vector[i] / norm;
        return out;
    }
}