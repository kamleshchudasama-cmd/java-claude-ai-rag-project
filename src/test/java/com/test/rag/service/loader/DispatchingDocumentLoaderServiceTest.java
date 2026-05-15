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
