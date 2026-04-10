package com.test.rag.service.queryembedding;

public interface QueryEmbeddingService {
    float[] embed(String userQuery);
}
