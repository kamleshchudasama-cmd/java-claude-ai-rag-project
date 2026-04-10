package com.test.rag.service.generation;

import com.test.rag.model.BuiltContext;
import com.test.rag.model.RagResponse;

public interface GenerationService {
    RagResponse generate(BuiltContext context);
}
