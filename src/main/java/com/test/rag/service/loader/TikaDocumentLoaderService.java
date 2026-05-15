package com.test.rag.service.loader;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.DocumentParseException;
import com.test.rag.model.ParsedDocument;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service("tikaLoader")
public class TikaDocumentLoaderService implements DocumentLoaderService {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentLoaderService.class);
    private static final String OCTET_STREAM = "application/octet-stream";

    private final AutoDetectParser parser;
    private final int maxContentChars;

    public TikaDocumentLoaderService(AutoDetectParser parser, RagProperties properties) {
        this.parser = parser;
        this.maxContentChars = properties.getMaxContentChars();
    }

    @Override
    public ParsedDocument load(Path filePath) {
        String filename = filePath.getFileName().toString();
        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new DocumentParseException("Cannot read file: " + filename, e);
        }
        try (InputStream is = Files.newInputStream(filePath)) {
            return parse(is, filename, fileSize);
        } catch (IOException e) {
            throw new DocumentParseException("Cannot open file: " + filename, e);
        }
    }

    @Override
    public ParsedDocument load(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String filename = (Objects.nonNull(originalFilename) && !originalFilename.isBlank())
                ? originalFilename
                : "unknown";
        long fileSize = file.getSize();
        try (InputStream is = file.getInputStream()) {
            return parse(is, filename, fileSize);
        } catch (IOException e) {
            throw new DocumentParseException("Cannot read uploaded file: " + filename, e);
        }
    }

    private ParsedDocument parse(InputStream inputStream, String filename, long fileSize) {
        if (fileSize == 0) {
            throw new DocumentParseException("No content extracted from: " + filename);
        }

        long startMs = System.currentTimeMillis();
        Metadata tikaMetadata = new Metadata();
        tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        BodyContentHandler handler = new BodyContentHandler(maxContentChars);

        try {
            parser.parse(inputStream, handler, tikaMetadata, new ParseContext());
        } catch (TikaException e) {
            throw new DocumentParseException("Failed to parse: " + filename, e);
        } catch (SAXException | IOException e) {
            throw new DocumentParseException("Failed to parse: " + filename, e);
        }

        String content = handler.toString().strip();
        if (content.isEmpty()) {
            // Issue 3 fix: distinguish unsupported types (EmptyParser returns no content for
            // application/octet-stream) from parseable files that genuinely have no text.
            // Checking content-type metadata is more stable than inspecting exception messages.
            String detectedType = tikaMetadata.get(Metadata.CONTENT_TYPE);
            if (Objects.nonNull(detectedType) && detectedType.startsWith(OCTET_STREAM)) {
                throw new DocumentParseException("Unsupported type: " + detectedType);
            }
            throw new DocumentParseException("No content extracted from: " + filename);
        }

        String detectedContentType = tikaMetadata.get(Metadata.CONTENT_TYPE);
        long latencyMs = System.currentTimeMillis() - startMs;
        log.info("Parsed file='{}' contentType='{}' chars={} latencyMs={}",
                filename, detectedContentType, content.length(), latencyMs);

        String sourceId = computeSourceId(filename, fileSize);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("filename", filename);
        metadata.put("source_id", sourceId);
        putIfNonNull(metadata, "content-type", detectedContentType);
        putIfNonNull(metadata, "author", tikaMetadata.get(TikaCoreProperties.CREATOR));
        putIfNonNull(metadata, "created-date", tikaMetadata.get(TikaCoreProperties.CREATED));
        metadata.put("upload-timestamp", Instant.now().toString());
        metadata.put("file-size-bytes", String.valueOf(fileSize));

        return new ParsedDocument(content, Collections.unmodifiableMap(metadata), sourceId);
    }

    private void putIfNonNull(Map<String, String> map, String key, String value) {
        if (Objects.nonNull(value)) {
            map.put(key, value);
        }
    }

    private String computeSourceId(String filename, long fileSize) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Issue 8 (minor) fix: separator prevents "a"+123 == "a1"+23 collision
            byte[] hash = digest.digest((filename + ":" + fileSize).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
