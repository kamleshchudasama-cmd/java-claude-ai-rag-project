package com.test.rag.service.context;

import com.test.rag.model.BuiltContext;
import com.test.rag.model.ScoredChunk;

import java.util.List;

public interface ContextBuilderService {
    BuiltContext build(String userQuery, List<ScoredChunk> scoredChunks);
}
