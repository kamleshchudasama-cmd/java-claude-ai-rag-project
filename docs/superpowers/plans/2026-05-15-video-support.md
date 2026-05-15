# Video Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add video file ingestion to the RAG system via the existing `POST /api/rag/ingest` endpoint — audio is transcribed by OpenAI Whisper and stored in PGVector alongside other document types.

**Architecture:** A new `DispatchingDocumentLoaderService` (marked `@Primary`) routes uploads by MIME type: `video/*` goes to a new `VideoDocumentLoaderService` that calls `OpenAiVideoTranscriptionService` (Whisper API), all others go to the existing `TikaDocumentLoaderService`. The transcript then flows through the unchanged chunking → embedding → PGVector pipeline. The Angular UI's ingest and documents components are updated to accept and display video files.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Spring AI 1.0.0 (`OpenAiAudioTranscriptionModel`), Maven, Angular 18, Angular Material

---

## File Map

**New (backend)**
- `src/main/java/com/test/rag/service/transcription/VideoTranscriptionService.java` — interface: `transcribe(MultipartFile) → String`
- `src/main/java/com/test/rag/service/transcription/OpenAiVideoTranscriptionService.java` — calls Whisper via `OpenAiAudioTranscriptionModel`
- `src/main/java/com/test/rag/service/loader/VideoDocumentLoaderService.java` — validates size, calls transcription, returns `ParsedDocument`
- `src/main/java/com/test/rag/service/loader/DispatchingDocumentLoaderService.java` — `@Primary`, routes by MIME type

**Modified (backend)**
- `src/main/java/com/test/rag/config/RagProperties.java` — add `maxVideoSizeBytes` field + getter/setter
- `src/main/java/com/test/rag/service/loader/TikaDocumentLoaderService.java` — change `@Service` to `@Service("tikaLoader")`
- `src/main/resources/application.properties` — add Whisper model property + `rag.max-video-size-bytes`

**New (backend tests)**
- `src/test/java/com/test/rag/service/transcription/OpenAiVideoTranscriptionServiceTest.java`
- `src/test/java/com/test/rag/service/loader/VideoDocumentLoaderServiceTest.java`
- `src/test/java/com/test/rag/service/loader/DispatchingDocumentLoaderServiceTest.java`

**Modified (frontend)**
- `angular-ui/src/app/features/ingest/ingest.component.ts` — add video MIME types, update icon/badge helpers, update error message
- `angular-ui/src/app/features/ingest/ingest.component.html` — update drop-zone hint text and `accept` attribute
- `angular-ui/src/app/features/documents/documents.component.ts` — add video icon/badge helpers

**Modified (frontend tests)**
- `angular-ui/src/app/features/ingest/ingest.component.spec.ts` — add video acceptance/rejection tests
- `angular-ui/src/app/features/documents/documents.component.spec.ts` — add video icon/badge tests

---

## Task 1: Add `maxVideoSizeBytes` to `RagProperties` and `application.properties`

**Files:**
- Modify: `src/main/java/com/test/rag/config/RagProperties.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Add field, getter, and setter to `RagProperties`**

In `src/main/java/com/test/rag/config/RagProperties.java`, add after the `// --- Document loading ---` block:

```java
// --- Video ---
private long maxVideoSizeBytes = 5_242_880L;
```

And add getter/setter after `getMaxContentChars` / `setMaxContentChars`:

```java
public long getMaxVideoSizeBytes() { return maxVideoSizeBytes; }
public void setMaxVideoSizeBytes(long maxVideoSizeBytes) { this.maxVideoSizeBytes = maxVideoSizeBytes; }
```

- [ ] **Step 2: Add Whisper and video-size properties to `application.properties`**

Append to `src/main/resources/application.properties`:

```properties
# ── Video transcription ───────────────────────────────────────────────────────
spring.ai.openai.audio.transcription.options.model=whisper-1
rag.max-video-size-bytes=5242880
```

- [ ] **Step 3: Run the full build to confirm nothing is broken**

```bash
mvn test
```

Expected: `BUILD SUCCESS`, all existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/test/rag/config/RagProperties.java src/main/resources/application.properties
git commit -m "feat: add maxVideoSizeBytes to RagProperties and whisper-1 config"
```

---

## Task 2: Add qualifier to `TikaDocumentLoaderService`

**Files:**
- Modify: `src/main/java/com/test/rag/service/loader/TikaDocumentLoaderService.java:32`

Spring will fail to wire `DispatchingDocumentLoaderService` in a later task unless the two `DocumentLoaderService` beans have distinct qualifier names.

- [ ] **Step 1: Change the `@Service` annotation**

In `TikaDocumentLoaderService.java` line 32, replace:

```java
@Service
public class TikaDocumentLoaderService implements DocumentLoaderService {
```

with:

```java
@Service("tikaLoader")
public class TikaDocumentLoaderService implements DocumentLoaderService {
```

- [ ] **Step 2: Run existing loader tests to confirm nothing broke**

```bash
mvn test -Dtest=DocumentLoaderServiceTest
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/test/rag/service/loader/TikaDocumentLoaderService.java
git commit -m "refactor: name TikaDocumentLoaderService bean 'tikaLoader' for qualifier injection"
```

---

## Task 3: `VideoTranscriptionService` interface + `OpenAiVideoTranscriptionService` (TDD)

**Files:**
- Create: `src/main/java/com/test/rag/service/transcription/VideoTranscriptionService.java`
- Create: `src/main/java/com/test/rag/service/transcription/OpenAiVideoTranscriptionService.java`
- Create: `src/test/java/com/test/rag/service/transcription/OpenAiVideoTranscriptionServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/test/rag/service/transcription/OpenAiVideoTranscriptionServiceTest.java`:

```java
package com.test.rag.service.transcription;

import com.test.rag.exception.DocumentParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiVideoTranscriptionServiceTest {

    @Mock
    private OpenAiAudioTranscriptionModel transcriptionModel;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AudioTranscriptionResponse mockResponse;

    private OpenAiVideoTranscriptionService service;

    @BeforeEach
    void setUp() {
        service = new OpenAiVideoTranscriptionService(transcriptionModel);
    }

    @Test
    void transcribe_returnsTranscriptFromWhisper() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn("Hello world transcript");

        String result = service.transcribe(file);

        assertThat(result).isEqualTo("Hello world transcript");
    }

    @Test
    void transcribe_throwsDocumentParseException_whenTranscriptIsBlank() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "silent.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn("   ");

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("No transcript extracted from");
    }

    @Test
    void transcribe_throwsDocumentParseException_whenTranscriptIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "null.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn(null);

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("No transcript extracted from");
    }

    @Test
    void transcribe_whenWhisperThrowsRuntimeException_propagates() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fail.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenThrow(new RuntimeException("Whisper API error"));

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Whisper API error");
    }

    @Test
    void transcribe_exceptionMessageContainsFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "myvideo.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn("");

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("myvideo.mp4");
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails with a compilation error**

```bash
mvn test -Dtest=OpenAiVideoTranscriptionServiceTest
```

Expected: `COMPILATION ERROR` — `OpenAiVideoTranscriptionService` does not exist yet.

- [ ] **Step 3: Create the `VideoTranscriptionService` interface**

Create `src/main/java/com/test/rag/service/transcription/VideoTranscriptionService.java`:

```java
package com.test.rag.service.transcription;

import org.springframework.web.multipart.MultipartFile;

public interface VideoTranscriptionService {
    String transcribe(MultipartFile file);
}
```

- [ ] **Step 4: Create `OpenAiVideoTranscriptionService`**

Create `src/main/java/com/test/rag/service/transcription/OpenAiVideoTranscriptionService.java`:

```java
package com.test.rag.service.transcription;

import com.test.rag.exception.DocumentParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

@Service
public class OpenAiVideoTranscriptionService implements VideoTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVideoTranscriptionService.class);

    private final OpenAiAudioTranscriptionModel transcriptionModel;

    public OpenAiVideoTranscriptionService(OpenAiAudioTranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @Override
    public String transcribe(MultipartFile file) {
        String filename = Objects.nonNull(file.getOriginalFilename())
                ? file.getOriginalFilename() : "video";
        long startMs = System.currentTimeMillis();

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to read video file: " + filename, e);
        }

        Resource audioResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        AudioTranscriptionResponse response = transcriptionModel.call(
                new AudioTranscriptionPrompt(audioResource));

        String transcript = response.getResult().getOutput();
        if (Objects.isNull(transcript) || transcript.isBlank()) {
            throw new DocumentParseException("No transcript extracted from: " + filename);
        }

        log.info("Transcribed video='{}' chars={} latencyMs={}",
                filename, transcript.length(), System.currentTimeMillis() - startMs);
        return transcript;
    }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

```bash
mvn test -Dtest=OpenAiVideoTranscriptionServiceTest
```

Expected: `BUILD SUCCESS`, 5 tests pass.

> **Troubleshooting:** If compilation fails with `OpenAiAudioTranscriptionModel cannot be resolved`, the auto-configuration may need a nudge. Add to `SpringAiConfig.java`:
> ```java
> @Bean
> public OpenAiAudioTranscriptionModel audioTranscriptionModel(OpenAiConnectionProperties connectionProps) {
>     return new OpenAiAudioTranscriptionModel(new OpenAiAudioApi(connectionProps.getApiKey()));
> }
> ```
> Only do this if the bean is missing at startup.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/transcription/ src/test/java/com/test/rag/service/transcription/
git commit -m "feat: add VideoTranscriptionService and OpenAiVideoTranscriptionService (Whisper)"
```

---

## Task 4: `VideoDocumentLoaderService` (TDD)

**Files:**
- Create: `src/main/java/com/test/rag/service/loader/VideoDocumentLoaderService.java`
- Create: `src/test/java/com/test/rag/service/loader/VideoDocumentLoaderServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/test/rag/service/loader/VideoDocumentLoaderServiceTest.java`:

```java
package com.test.rag.service.loader;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.DocumentParseException;
import com.test.rag.model.ParsedDocument;
import com.test.rag.service.transcription.VideoTranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoDocumentLoaderServiceTest {

    @Mock
    private VideoTranscriptionService transcriptionService;

    private VideoDocumentLoaderService service;

    @BeforeEach
    void setUp() {
        service = new VideoDocumentLoaderService(transcriptionService, new RagProperties());
    }

    @Test
    void load_returnsTranscriptAsContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture.mp4", "video/mp4", new byte[1024]);
        when(transcriptionService.transcribe(file)).thenReturn("This is a Spring AI lecture");

        ParsedDocument result = service.load(file);

        assertThat(result.content()).isEqualTo("This is a Spring AI lecture");
    }

    @Test
    void load_metadataContainsFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "talk.mp4", "video/mp4", new byte[1024]);
        when(transcriptionService.transcribe(file)).thenReturn("transcript");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata().get("filename")).isEqualTo("talk.mp4");
    }

    @Test
    void load_metadataContainsContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "vid.mp4", "video/mp4", new byte[1024]);
        when(transcriptionService.transcribe(file)).thenReturn("transcript");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata().get("content-type")).isEqualTo("video/mp4");
    }

    @Test
    void load_metadataContainsSourceId() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "vid.mp4", "video/mp4", new byte[1024]);
        when(transcriptionService.transcribe(file)).thenReturn("transcript");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata()).containsKey("source_id");
        assertThat(result.metadata().get("source_id")).isEqualTo(result.sourceId());
    }

    @Test
    void load_computesSourceIdFromFilenameAndSize() {
        byte[] content = new byte[2048];
        MockMultipartFile file = new MockMultipartFile(
                "file", "clip.mp4", "video/mp4", content);
        when(transcriptionService.transcribe(file)).thenReturn("clip transcript");

        ParsedDocument result = service.load(file);

        assertThat(result.sourceId()).isEqualTo(sha256("clip.mp4:" + content.length));
    }

    @Test
    void load_returnedMetadataIsUnmodifiable() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "vid.mp4", "video/mp4", new byte[100]);
        when(transcriptionService.transcribe(file)).thenReturn("transcript");

        ParsedDocument result = service.load(file);

        assertThatThrownBy(() -> result.metadata().put("injected", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void load_throwsDocumentParseException_whenFileSizeExceedsLimit() {
        // Default limit is 5 MB (5_242_880 bytes). Use 6 MB.
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.mp4", "video/mp4", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> service.load(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("Video exceeds 5 MB limit")
                .hasMessageContaining("big.mp4");
    }

    @Test
    void load_acceptsFileAtExactSizeLimit() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "exact.mp4", "video/mp4", new byte[5 * 1024 * 1024]);
        when(transcriptionService.transcribe(file)).thenReturn("transcript");

        assertThat(service.load(file)).isNotNull();
    }

    @Test
    void load_pathVariant_throwsDocumentParseException() {
        assertThatThrownBy(() -> service.load(Path.of("/some/video.mp4")))
                .isInstanceOf(DocumentParseException.class);
    }

    @Test
    void load_nullOriginalFilename_usesUnknownFallback() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "video/mp4", new byte[100]);
        when(transcriptionService.transcribe(file)).thenReturn("transcript");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata().get("filename")).isEqualTo("unknown");
    }

    // --- helper ---

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```bash
mvn test -Dtest=VideoDocumentLoaderServiceTest
```

Expected: `COMPILATION ERROR` — `VideoDocumentLoaderService` does not exist yet.

- [ ] **Step 3: Create `VideoDocumentLoaderService`**

Create `src/main/java/com/test/rag/service/loader/VideoDocumentLoaderService.java`:

```java
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
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
mvn test -Dtest=VideoDocumentLoaderServiceTest
```

Expected: `BUILD SUCCESS`, 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/test/rag/service/loader/VideoDocumentLoaderService.java src/test/java/com/test/rag/service/loader/VideoDocumentLoaderServiceTest.java
git commit -m "feat: add VideoDocumentLoaderService — validates size, delegates to transcription"
```

---

## Task 5: `DispatchingDocumentLoaderService` (TDD)

**Files:**
- Create: `src/main/java/com/test/rag/service/loader/DispatchingDocumentLoaderService.java`
- Create: `src/test/java/com/test/rag/service/loader/DispatchingDocumentLoaderServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/test/rag/service/loader/DispatchingDocumentLoaderServiceTest.java`:

```java
package com.test.rag.service.loader;

import com.test.rag.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchingDocumentLoaderServiceTest {

    @Mock
    private DocumentLoaderService tikaLoader;

    @Mock
    private DocumentLoaderService videoLoader;

    private DispatchingDocumentLoaderService service;

    @BeforeEach
    void setUp() {
        service = new DispatchingDocumentLoaderService(tikaLoader, videoLoader);
    }

    @Test
    void load_videoMp4_routesToVideoLoader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "clip.mp4", "video/mp4", new byte[100]);
        ParsedDocument expected = new ParsedDocument("transcript", Map.of(), "id1");
        when(videoLoader.load(file)).thenReturn(expected);

        ParsedDocument result = service.load(file);

        assertThat(result).isSameAs(expected);
        verify(tikaLoader, never()).load(any(MockMultipartFile.class));
    }

    @Test
    void load_videoQuicktime_routesToVideoLoader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "movie.mov", "video/quicktime", new byte[100]);
        when(videoLoader.load(file)).thenReturn(new ParsedDocument("t", Map.of(), "id2"));

        service.load(file);

        verify(videoLoader).load(file);
        verify(tikaLoader, never()).load(any(MockMultipartFile.class));
    }

    @Test
    void load_videoWebm_routesToVideoLoader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "screen.webm", "video/webm", new byte[100]);
        when(videoLoader.load(file)).thenReturn(new ParsedDocument("t", Map.of(), "id3"));

        service.load(file);

        verify(videoLoader).load(file);
    }

    @Test
    void load_pdfFile_routesToTikaLoader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[100]);
        ParsedDocument expected = new ParsedDocument("pdf text", Map.of(), "id4");
        when(tikaLoader.load(file)).thenReturn(expected);

        ParsedDocument result = service.load(file);

        assertThat(result).isSameAs(expected);
        verify(videoLoader, never()).load(any(MockMultipartFile.class));
    }

    @Test
    void load_htmlFile_routesToTikaLoader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "page.html", "text/html", new byte[100]);
        when(tikaLoader.load(file)).thenReturn(new ParsedDocument("html", Map.of(), "id5"));

        service.load(file);

        verify(tikaLoader).load(file);
        verify(videoLoader, never()).load(any(MockMultipartFile.class));
    }

    @Test
    void load_nullContentType_routesToTikaLoader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.bin", null, new byte[100]);
        when(tikaLoader.load(file)).thenReturn(new ParsedDocument("content", Map.of(), "id6"));

        service.load(file);

        verify(tikaLoader).load(file);
        verify(videoLoader, never()).load(any(MockMultipartFile.class));
    }

    @Test
    void load_pathVariant_alwaysRoutesToTikaLoader() {
        java.nio.file.Path path = java.nio.file.Path.of("/docs/report.pdf");
        when(tikaLoader.load(path)).thenReturn(new ParsedDocument("pdf", Map.of(), "id7"));

        service.load(path);

        verify(tikaLoader).load(path);
        verify(videoLoader, never()).load(any(java.nio.file.Path.class));
    }
}
```

- [ ] **Step 2: Run to confirm compilation failure**

```bash
mvn test -Dtest=DispatchingDocumentLoaderServiceTest
```

Expected: `COMPILATION ERROR` — `DispatchingDocumentLoaderService` does not exist yet.

- [ ] **Step 3: Create `DispatchingDocumentLoaderService`**

Create `src/main/java/com/test/rag/service/loader/DispatchingDocumentLoaderService.java`:

```java
package com.test.rag.service.loader;

import com.test.rag.model.ParsedDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Objects;

@Primary
@Service
public class DispatchingDocumentLoaderService implements DocumentLoaderService {

    private final DocumentLoaderService tikaLoader;
    private final DocumentLoaderService videoLoader;

    public DispatchingDocumentLoaderService(
            @Qualifier("tikaLoader") DocumentLoaderService tikaLoader,
            @Qualifier("videoLoader") DocumentLoaderService videoLoader) {
        this.tikaLoader = tikaLoader;
        this.videoLoader = videoLoader;
    }

    @Override
    public ParsedDocument load(Path filePath) {
        return tikaLoader.load(filePath);
    }

    @Override
    public ParsedDocument load(MultipartFile file) {
        if (isVideo(file.getContentType())) {
            return videoLoader.load(file);
        }
        return tikaLoader.load(file);
    }

    private boolean isVideo(String contentType) {
        return Objects.nonNull(contentType) && contentType.startsWith("video/");
    }
}
```

- [ ] **Step 4: Run the dispatching tests and confirm they pass**

```bash
mvn test -Dtest=DispatchingDocumentLoaderServiceTest
```

Expected: `BUILD SUCCESS`, 7 tests pass.

- [ ] **Step 5: Run the full backend test suite**

```bash
mvn test
```

Expected: `BUILD SUCCESS`, all tests pass (including existing `DocumentLoaderServiceTest`, `RagControllerTest`, etc.).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/loader/DispatchingDocumentLoaderService.java src/test/java/com/test/rag/service/loader/DispatchingDocumentLoaderServiceTest.java
git commit -m "feat: add DispatchingDocumentLoaderService — routes video/* to Whisper, rest to Tika"
```

---

## Task 6: Frontend — ingest component

**Files:**
- Modify: `angular-ui/src/app/features/ingest/ingest.component.ts`
- Modify: `angular-ui/src/app/features/ingest/ingest.component.html`
- Modify: `angular-ui/src/app/features/ingest/ingest.component.spec.ts`

- [ ] **Step 1: Update `ALLOWED_TYPES`, helpers, and error message in `ingest.component.ts`**

In `angular-ui/src/app/features/ingest/ingest.component.ts`, replace the `ALLOWED_TYPES` constant:

```typescript
const ALLOWED_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/html',
  'text/markdown',
  'text/x-markdown',
  'video/mp4',
  'video/quicktime',
  'video/x-msvideo',
  'video/webm',
  'video/mpeg'
];
```

In `setFile()`, replace the error message string:

```typescript
this.errorMessage = `Unsupported file type: "${file.type || 'unknown'}". Use PDF, DOCX, HTML, MD, MP4, MOV, AVI, or WEBM.`;
```

In `iconFor()`, add before the final `return 'insert_drive_file';`:

```typescript
if (type.startsWith('video/')) return 'videocam';
```

In `typeBadge()`, add before the final `return 'File';`:

```typescript
if (type.startsWith('video/')) return 'VIDEO';
```

- [ ] **Step 2: Update `ingest.component.html`**

In `angular-ui/src/app/features/ingest/ingest.component.html`, replace the drop-types paragraph:

```html
<p class="drop-types">PDF · DOCX · HTML · MD · MP4 · MOV · WEBM · up to 50 MB (5 MB for video)</p>
```

Replace the hidden file input's `accept` attribute:

```html
<input #fileInput type="file" accept=".pdf,.docx,.html,.md,.mp4,.mov,.avi,.webm" class="hidden-input"
  (change)="onFileSelected($event)" />
```

- [ ] **Step 3: Add tests to `ingest.component.spec.ts`**

Append the following test cases inside the `describe('IngestComponent', ...)` block in `angular-ui/src/app/features/ingest/ingest.component.spec.ts`:

```typescript
it('accepts mp4 video file and transitions to fileSelected state', () => {
  const mp4File = new File([new ArrayBuffer(100)], 'lecture.mp4', { type: 'video/mp4' });
  (component as any).setFile(mp4File);
  fixture.detectChanges();
  expect(component.state).toBe('fileSelected');
});

it('accepts quicktime video file and transitions to fileSelected state', () => {
  const movFile = new File([new ArrayBuffer(100)], 'clip.mov', { type: 'video/quicktime' });
  (component as any).setFile(movFile);
  expect(component.state).toBe('fileSelected');
});

it('accepts webm video file and transitions to fileSelected state', () => {
  const webmFile = new File([new ArrayBuffer(100)], 'screen.webm', { type: 'video/webm' });
  (component as any).setFile(webmFile);
  expect(component.state).toBe('fileSelected');
});

it('iconFor returns videocam for video/mp4', () => {
  expect(component.iconFor('video/mp4')).toBe('videocam');
});

it('typeBadge returns VIDEO for video/mp4', () => {
  expect(component.typeBadge('video/mp4')).toBe('VIDEO');
});

it('typeBadge returns VIDEO for video/quicktime', () => {
  expect(component.typeBadge('video/quicktime')).toBe('VIDEO');
});
```

- [ ] **Step 4: Run Angular tests**

```bash
cd angular-ui && npm test -- --watch=false
```

Expected: all tests pass including the new video ones.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/features/ingest/
git commit -m "feat: add video file support to ingest component (MP4, MOV, WEBM, AVI)"
```

---

## Task 7: Frontend — documents component

**Files:**
- Modify: `angular-ui/src/app/features/documents/documents.component.ts`
- Modify: `angular-ui/src/app/features/documents/documents.component.spec.ts`

- [ ] **Step 1: Update `iconFor` and `typeBadge` in `documents.component.ts`**

In `angular-ui/src/app/features/documents/documents.component.ts`, in `iconFor()`, add before the final `return 'insert_drive_file';`:

```typescript
if (contentType?.startsWith('video/')) return 'videocam';
```

In `typeBadge()`, add before the final `return 'File';`:

```typescript
if (contentType?.startsWith('video/')) return 'VIDEO';
```

- [ ] **Step 2: Add tests to `documents.component.spec.ts`**

Append the following test cases inside the `describe('DocumentsComponent', ...)` block in `angular-ui/src/app/features/documents/documents.component.spec.ts`:

```typescript
it('iconFor returns videocam for video/mp4', () => {
  expect(component.iconFor('video/mp4')).toBe('videocam');
});

it('iconFor returns videocam for video/quicktime', () => {
  expect(component.iconFor('video/quicktime')).toBe('videocam');
});

it('typeBadge returns VIDEO for video/mp4', () => {
  expect(component.typeBadge('video/mp4')).toBe('VIDEO');
});

it('doc card renders VIDEO badge for a video document', () => {
  const videoDoc: DocumentSummary = {
    ...mockDoc,
    filename: 'lecture.mp4',
    contentType: 'video/mp4'
  };
  fakeService.documents.set([videoDoc]);
  fixture.detectChanges();
  const meta: HTMLElement = fixture.nativeElement.querySelector('.doc-meta');
  expect(meta.textContent).toContain('VIDEO');
});
```

- [ ] **Step 3: Run Angular tests**

```bash
cd angular-ui && npm test -- --watch=false
```

Expected: all tests pass.

- [ ] **Step 4: Run full backend test suite one final time**

```bash
cd .. && mvn test
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/features/documents/
git commit -m "feat: add video icon and badge to documents component"
```

---

## Summary

After all tasks complete:
- Videos up to 5 MB can be uploaded via `POST /api/rag/ingest` (same endpoint)
- Audio is transcribed via OpenAI Whisper and stored in PGVector
- Query answers can cite video filenames just like documents
- The documents list shows `VIDEO` badge and `videocam` icon for video content types
- The ingest page accepts MP4, MOV, AVI, and WEBM files
