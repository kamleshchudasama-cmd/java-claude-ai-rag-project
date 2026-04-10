package com.test.rag.service.retrieval;

import com.test.rag.model.ScoredChunk;

import java.util.List;

public interface RetrievalService {
    List<ScoredChunk> retrieve(String userQuery);
}
