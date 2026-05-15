package com.test.rag.service.loader;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.DocumentParseException;
import com.test.rag.model.ParsedDocument;
import com.test.rag.service.transcription.VideoTranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service("videoLoader")
public class VideoDocumentLoaderService implements DocumentLoaderService {

    private static final Logger log = LoggerFactory.getLogger(VideoDocumentLoaderService.class);

    private final VideoTranscriptionService transcriptionService;
    private final long maxVideoSizeBytes;

    public VideoDocumentLoaderService(VideoTranscriptionService transcriptionService,
                                      RagProperties properties) {
        this.transcriptionService = transcriptionService;
        this.maxVideoSizeBytes = properties.getMaxVideoSizeBytes();
    }

    @Override
    public ParsedDocument load(Path filePath) {
        throw new DocumentParseException("Path-based video loading not supported");
    }

    @Override
    public ParsedDocument load(MultipartFile file) {
        String filename = resolveFilename(file);
        long fileSize = file.getSize();

        if (fileSize > maxVideoSizeBytes) {
            throw new DocumentParseException("Video exceeds 5 MB limit: " + filename);
        }

        String transcript = transcriptionService.transcribe(file);
        String sourceId = computeSourceId(filename, fileSize);
        String contentType = file.getContentType();

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("filename", filename);
        metadata.put("source_id", sourceId);
        if (Objects.nonNull(contentType)) {
            metadata.put("content-type", contentType);
        }
        metadata.put("upload-timestamp", Instant.now().toString());
        metadata.put("file-size-bytes", String.valueOf(fileSize));

        log.info("Video ingested file='{}' contentType='{}' transcriptChars={}",
                filename, contentType, transcript.length());

        return new ParsedDocument(transcript, Collections.unmodifiableMap(metadata), sourceId);
    }

    private String resolveFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (Objects.nonNull(name) && !name.isBlank()) ? name : "unknown";
    }

    private String computeSourceId(String filename, long fileSize) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((filename + ":" + fileSize).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
