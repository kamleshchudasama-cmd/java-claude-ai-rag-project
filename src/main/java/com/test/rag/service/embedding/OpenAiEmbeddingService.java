package com.test.rag.service.embedding;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.EmbeddingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final RagProperties props;

    public OpenAiEmbeddingService(EmbeddingModel embeddingModel, RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.props = props;
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
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
            result.addAll(embedBatch(batch));
        }
        return result;
    }

    private List<EmbeddedChunk> embedBatch(List<DocumentChunk> batch) {
        List<String> texts = batch.stream().map(DocumentChunk::content).toList();
        long startMs = System.currentTimeMillis();
        EmbeddingResponse response;
        try {
            response = embeddingModel.embedForResponse(texts);
        } catch (RuntimeException e) {
            throw new EmbeddingException("Embedding API call failed for batch of " + batch.size(), e);
        }

        long tokens = Objects.nonNull(response.getMetadata().getUsage())
                ? response.getMetadata().getUsage().getTotalTokens() : 0;
        log.info("Embedded batchSize={} model='{}' tokens={} latencyMs={}",
                batch.size(),
                response.getMetadata().getModel(),
                tokens,
                System.currentTimeMillis() - startMs);

        List<EmbeddedChunk> result = new ArrayList<>();
        List<org.springframework.ai.embedding.Embedding> embeddings = response.getResults();
        for (int i = 0; i < batch.size(); i++) {
            float[] raw = embeddings.get(i).getOutput();
            result.add(new EmbeddedChunk(batch.get(i), normalize(raw)));
        }
        return result;
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