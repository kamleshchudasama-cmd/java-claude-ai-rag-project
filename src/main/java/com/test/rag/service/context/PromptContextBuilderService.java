package com.test.rag.service.context;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PromptContextBuilderService implements ContextBuilderService {

    private static final Logger log = LoggerFactory.getLogger(PromptContextBuilderService.class);

    private final RagProperties props;

    public PromptContextBuilderService(RagProperties props) {
        this.props = props;
    }

    @Override
    public BuiltContext build(String userQuery, List<ScoredChunk> scoredChunks) {
        // Highest-scoring chunks first so truncation keeps the most relevant
        List<ScoredChunk> sorted = scoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::similarityScore).reversed())
                .toList();

        int maxTokens = props.getMaxContextTokens();
        StringBuilder contextBlock = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        int usedTokens = 0;
        int ref = 1;

        for (ScoredChunk sc : sorted) {
            String line = "[" + ref + "] " + sc.chunk().content();
            int lineTokens = (int) Math.ceil(line.length() / 4.0);
            if (usedTokens + lineTokens > maxTokens) break;

            contextBlock.append(line).append("\n\n");
            String filename = sc.chunk().metadata().getOrDefault("filename", "unknown");
            citations.add(new Citation(ref, filename, sc.chunk().chunkIndex(), sc.similarityScore(), sc.chunk().content()));
            usedTokens += lineTokens;
            ref++;
        }

        PromptTemplate template = new PromptTemplate(new ClassPathResource("prompts/rag-system.st"));
        String systemPrompt = template.render(Map.of("context", contextBlock.toString().strip()));

        log.info("ContextBuilder chunks={} citations={} contextTokens=~{}",
                scoredChunks.size(), citations.size(), usedTokens);

        return new BuiltContext(systemPrompt, userQuery, citations);
    }
}
