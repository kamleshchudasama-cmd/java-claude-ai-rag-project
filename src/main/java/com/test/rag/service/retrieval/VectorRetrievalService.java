package com.test.rag.service.retrieval;

import com.test.rag.config.RagProperties;
import com.test.rag.model.ScoredChunk;
import com.test.rag.service.queryembedding.QueryEmbeddingService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorRetrievalService implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(VectorRetrievalService.class);

    private final VectorStoreService vectorStoreService;
    private final RagProperties props;
    private final QueryEmbeddingService queryEmbeddingService;

    public VectorRetrievalService(VectorStoreService vectorStoreService, RagProperties props,
                                   QueryEmbeddingService queryEmbeddingService) {
        this.vectorStoreService = vectorStoreService;
        this.props = props;
        this.queryEmbeddingService = queryEmbeddingService;
    }

    @Override
    public List<ScoredChunk> retrieve(String userQuery) {
        if (!props.isRetrievalEnabled()) {
            log.info("Retrieval disabled — skipping vector search for query='{}'", userQuery);
            return List.of();
        }

        int topK = props.getTopK();
        double threshold = props.getMinSimilarity().doubleValue();

        float[] embedding = queryEmbeddingService.embed(userQuery);
        List<ScoredChunk> results = vectorStoreService.search(embedding, topK, threshold);

        log.info("Retrieve query='{}' topK={} returned={} scores={}",
                userQuery,
                topK,
                results.size(),
                results.stream().map(c -> c.similarityScore().toString()).toList());

        return results;
    }
}
