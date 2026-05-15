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
