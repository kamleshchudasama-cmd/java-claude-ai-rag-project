package com.test.rag.service.chunking;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.ChunkingException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RecursiveChunkingService implements ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(RecursiveChunkingService.class);
    private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ", "! ", "? ", " ", "");
    private static final int CHARS_PER_TOKEN = 4;

    private final RagProperties props;

    public RecursiveChunkingService(RagProperties props) {
        this.props = props;
    }

    @Override
    public List<DocumentChunk> chunk(ParsedDocument document) {
        if (Objects.isNull(document.content()) || document.content().isBlank()) {
            throw new ChunkingException("Cannot chunk empty document");
        }

        int chunkSizeChars = props.getChunkSize() * CHARS_PER_TOKEN;
        int overlapChars   = props.getChunkOverlap() * CHARS_PER_TOKEN;
        int minChunkChars  = props.getMinChunkSize() * CHARS_PER_TOKEN;

        List<String> rawChunks = split(document.content(), chunkSizeChars);

        // Apply overlap
        List<String> withOverlap = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            if (i == 0) {
                withOverlap.add(rawChunks.get(i));
            } else {
                String prev = rawChunks.get(i - 1);
                String tail = prev.substring(Math.max(0, prev.length() - overlapChars));
                withOverlap.add(tail + rawChunks.get(i));
            }
        }

        // Map to DocumentChunk, discard below minChunkChars
        String sourceId = document.sourceId();
        List<DocumentChunk> result = new ArrayList<>();
        int index = 0;

        for (String content : withOverlap) {
            String trimmed = content.strip();
            if (trimmed.isEmpty() || trimmed.length() < minChunkChars) continue;

            int tokenCount = (int) Math.ceil((double) trimmed.length() / CHARS_PER_TOKEN);
            String chunkId = sha256(sourceId + index);

            Map<String, String> meta = new HashMap<>(document.metadata());
            meta.put("chunk_index", String.valueOf(index));

            result.add(new DocumentChunk(chunkId, trimmed, index, tokenCount, Map.copyOf(meta)));
            index++;
        }

        // If all chunks were discarded (very short doc), return one chunk for full content
        if (result.isEmpty()) {
            String trimmed = document.content().strip();
            int tokenCount = (int) Math.ceil((double) trimmed.length() / CHARS_PER_TOKEN);
            Map<String, String> meta = new HashMap<>(document.metadata());
            meta.put("chunk_index", "0");
            result.add(new DocumentChunk(sha256(sourceId + "0"), trimmed, 0, tokenCount, Map.copyOf(meta)));
        }

        int min = result.stream().mapToInt(DocumentChunk::tokenCount).min().orElse(0);
        int max = result.stream().mapToInt(DocumentChunk::tokenCount).max().orElse(0);
        double avg = result.stream().mapToInt(DocumentChunk::tokenCount).average().orElse(0);
        log.info("Chunked file='{}' chunks={} tokenMin={} tokenMax={} tokenAvg={}",
                document.metadata().getOrDefault("filename", "unknown"),
                result.size(), min, max, String.format("%.1f", avg));

        return result;
    }

    // Recursive character split: tries separators in order, merges small pieces
    private List<String> split(String text, int maxChars) {
        if (text.length() <= maxChars) return List.of(text);

        String separator = SEPARATORS.stream()
                .filter(s -> !s.isEmpty() && text.contains(s))
                .findFirst()
                .orElse("");

        String[] parts;
        if (separator.isEmpty()) {
            parts = new String[]{text.substring(0, maxChars), text.substring(maxChars)};
        } else {
            parts = text.split(java.util.regex.Pattern.quote(separator), -1);
        }

        // Merge parts greedily into chunks <= maxChars
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String joined = current.isEmpty() ? part : current + separator + part;
            if (joined.length() > maxChars && !current.isEmpty()) {
                chunks.add(current.toString());
                current = new StringBuilder(part);
            } else {
                current = new StringBuilder(joined);
            }
        }
        if (!current.isEmpty()) chunks.add(current.toString());

        // Recursively split any chunk still above maxChars
        List<String> result = new ArrayList<>();
        List<String> remaining = SEPARATORS.subList(
                SEPARATORS.indexOf(separator) + 1, SEPARATORS.size());
        for (String chunk : chunks) {
            if (chunk.length() > maxChars && !remaining.isEmpty()) {
                result.addAll(split(chunk, maxChars));
            } else {
                result.add(chunk);
            }
        }
        return result;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
