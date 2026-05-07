package com.test.rag.service.embedding;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.EmbeddingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final EmbeddingBatchProcessor batchProcessor;
    private final RagProperties props;

    public OpenAiEmbeddingService(EmbeddingBatchProcessor batchProcessor, RagProperties props) {
        this.batchProcessor = batchProcessor;
        this.props = props;
    }

    @Override
    public List<EmbeddedChunk> embed(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return List.of();

        int batchSize = props.getEmbeddingBatchSize();
        List<EmbeddedChunk> result = new ArrayList<>();

        long delayMs = props.getEmbeddingRequestDelayMs();
        for (int start = 0; start < chunks.size(); start += batchSize) {
            if (start > 0 && delayMs > 0) {
                try {
                    log.info("Rate-limit delay {}ms before next embedding batch", delayMs);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new EmbeddingException("Embedding interrupted during rate-limit delay", ie);
                }
            }
            List<DocumentChunk> batch = chunks.subList(start, Math.min(start + batchSize, chunks.size()));
            result.addAll(batchProcessor.embedBatch(batch));
        }
        return result;
    }
}
