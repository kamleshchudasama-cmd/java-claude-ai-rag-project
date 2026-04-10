package com.test.rag.service.generation;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.RagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Objects;

@Service
public class GeminiGenerationService implements GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GeminiGenerationService.class);

    private final ChatClient chatClient;
    private final RagProperties props;

    public GeminiGenerationService(ChatClient chatClient, RagProperties props) {
        this.chatClient = chatClient;
        this.props = props;
    }

    @Override
    @Retryable(
            retryFor = HttpClientErrorException.TooManyRequests.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 31_000, multiplier = 1.5)
    )
    public RagResponse generate(BuiltContext context) {
        long start = System.currentTimeMillis();

        ChatResponse chatResponse = chatClient.prompt()
                .system(context.systemPrompt())
                .user(context.userMessage())
                .options(OpenAiChatOptions.builder()
                        .temperature(props.getTemperature().doubleValue())
                        .maxCompletionTokens(props.getMaxOutputTokens())
                        .build())
                .call()
                .chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        long totalTokens = Objects.nonNull(chatResponse.getMetadata().getUsage())
                ? chatResponse.getMetadata().getUsage().getTotalTokens() : 0;

        List<Citation> usedCitations = context.citations().stream()
                .filter(c -> answer.contains("[" + c.ref() + "]"))
                .toList();

        log.info("Generation totalTokens={} citations={} latencyMs={}",
                totalTokens, usedCitations.size(), System.currentTimeMillis() - start);

        return new RagResponse(answer, usedCitations, (int) totalTokens);
    }
}
