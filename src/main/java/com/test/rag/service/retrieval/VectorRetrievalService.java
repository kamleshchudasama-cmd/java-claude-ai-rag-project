package com.test.rag.service.retrieval;

import com.test.rag.config.RagProperties;
import com.test.rag.model.ScoredChunk;
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

    public VectorRetrievalService(VectorStoreService vectorStoreService, RagProperties props) {
        this.vectorStoreService = vectorStoreService;
        this.props = props;
    }

    @Override
    public List<ScoredChunk> retrieve(String userQuery) {
        int topK = props.getTopK();
        double threshold = props.getMinSimilarity().doubleValue();

        List<ScoredChunk> results = vectorStoreService.search(userQuery, topK, threshold);

        log.info("Retrieve query='{}' topK={} returned={} scores={}",
                userQuery,
                topK,
                results.size(),
                results.stream().map(c -> String.format("%.3f", c.similarityScore())).toList());

        return results;
    }
}
