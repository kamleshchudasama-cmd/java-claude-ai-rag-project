package com.test.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EmbeddingModel backed by Voyage AI (voyage-3, 1024 dims).
 *
 * Endpoint: POST https://api.voyageai.com/v1/embeddings
 * Auth:     Authorization: Bearer {VOYAGE_API_KEY}
 *
 * Retry is handled internally (not via @Retryable) because the EmbeddingModel
 * interface has a default embed(String) method that calls this.call() directly,
 * bypassing the Spring AOP proxy — making @Retryable ineffective for the query path.
 */
@Primary
@Component
public class VoyageEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingModel.class);
    private static final String MODEL = "voyage-3";
    private static final String BASE_URL = "https://api.voyageai.com/v1/embeddings";
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 21_000;

    private final RestClient restClient;

    public VoyageEmbeddingModel(@Value("${VOYAGE_API_KEY}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        long start = System.currentTimeMillis();

        Map<String, Object> response = callWithRetry(texts);

        long totalTokens = extractTokenCount(response);
        log.info("VoyageEmbedding model='{}' inputTokens={} count={} latencyMs={}",
                MODEL, totalTokens, texts.size(), System.currentTimeMillis() - start);

        return new EmbeddingResponse(buildEmbeddings(response), new EmbeddingResponseMetadata());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callWithRetry(List<String> texts) {
        Map<String, Object> body = Map.of("model", MODEL, "input", texts);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> response = restClient.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map.class);

                if (Objects.isNull(response) || Objects.isNull(response.get("data"))) {
                    throw new RuntimeException("Voyage AI returned null or missing 'data' field");
                }
                return response;

            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("Voyage AI rate limit exceeded after " + MAX_RETRIES + " attempts", e);
                }
                log.warn("Voyage AI 429 rate limit — attempt {}/{}. Waiting {}ms before retry...",
                        attempt, MAX_RETRIES, RETRY_DELAY_MS);
                sleep(RETRY_DELAY_MS);

            } catch (RestClientException e) {
                throw new RuntimeException("Voyage AI embedding call failed for batch size=" + texts.size(), e);
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during Voyage AI rate-limit wait", ie);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Embedding> buildEmbeddings(Map<String, Object> response) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        List<Embedding> embeddings = new ArrayList<>(data.size());

        for (Map<String, Object> item : data) {
            List<Number> values = (List<Number>) item.get("embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }
            int index = ((Number) item.get("index")).intValue();
            embeddings.add(new Embedding(vector, index));
        }
        return embeddings;
    }

    @SuppressWarnings("unchecked")
    private long extractTokenCount(Map<String, Object> response) {
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (Objects.isNull(usage)) return 0L;
        Number tokens = (Number) usage.get("total_tokens");
        return Objects.nonNull(tokens) ? tokens.longValue() : 0L;
    }
}
