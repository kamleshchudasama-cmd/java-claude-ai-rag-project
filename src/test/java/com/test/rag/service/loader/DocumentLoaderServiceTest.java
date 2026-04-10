package com.test.rag.service.loader;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.DocumentParseException;
import com.test.rag.model.ParsedDocument;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentLoaderServiceTest {

    @TempDir
    Path tempDir;

    private DocumentLoaderService service;

    @BeforeEach
    void setUp() {
        service = new TikaDocumentLoaderService(new AutoDetectParser(), new RagProperties());
    }

    // -------------------------------------------------------------------------
    // Valid file — Path overload
    // -------------------------------------------------------------------------

    @Test
    void load_txtFile_returnsNonEmptyContent() throws IOException {
        Path file = writeTxt("hello.txt", "Hello World from plain text");

        ParsedDocument result = service.load(file);

        assertThat(result.content()).isNotBlank();
        assertThat(result.content()).contains("Hello World from plain text");
    }

    @Test
    void load_txtFile_metadataContainsFilename() throws IOException {
        Path file = writeTxt("sample.txt", "Some text");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata()).containsKey("filename");
        assertThat(result.metadata().get("filename")).isEqualTo("sample.txt");
    }

    @Test
    void load_txtFile_metadataContainsContentType() throws IOException {
        Path file = writeTxt("doc.txt", "Content here");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata()).containsKey("content-type");
        assertThat(result.metadata().get("content-type")).startsWith("text/plain");
    }

    // -------------------------------------------------------------------------
    // HTML — boilerplate tag stripping
    // -------------------------------------------------------------------------

    @Test
    void load_htmlFile_extractsBodyText() throws IOException {
        Path file = writeHtml("page.html",
                "<!DOCTYPE html><html><head><title>Title</title></head>" +
                "<body><p>Hello HTML</p></body></html>");

        ParsedDocument result = service.load(file);

        assertThat(result.content()).contains("Hello HTML");
    }

    @Test
    void load_htmlFile_doesNotContainHtmlTags() throws IOException {
        Path file = writeHtml("page.html",
                "<html><body><p>Body text</p></body></html>");

        ParsedDocument result = service.load(file);

        assertThat(result.content()).doesNotContain("<body>", "<p>", "</p>", "</body>", "<html>");
    }

    @Test
    void load_htmlFile_headSectionIsNotIncludedInContent() throws IOException {
        Path file = writeHtml("page.html",
                "<html><head><title>Secret Title</title></head><body><p>Visible</p></body></html>");

        ParsedDocument result = service.load(file);

        assertThat(result.content()).contains("Visible");
        assertThat(result.content()).doesNotContain("Secret Title");
    }

    // -------------------------------------------------------------------------
    // Minimal PDF — spec: "Valid PDF → content non-empty, metadata has filename"
    // PDFBox (via Tika) uses repair mode for PDFs without a proper xref table.
    // -------------------------------------------------------------------------

    @Test
    void load_minimalPdf_returnsNonEmptyContent() throws IOException {
        Path pdf = writeMinimalPdf("sample.pdf");

        ParsedDocument result = service.load(pdf);

        assertThat(result.content()).isNotBlank();
    }

    @Test
    void load_minimalPdf_metadataContainsFilename() throws IOException {
        Path pdf = writeMinimalPdf("report.pdf");

        ParsedDocument result = service.load(pdf);

        assertThat(result.metadata().get("filename")).isEqualTo("report.pdf");
    }

    // -------------------------------------------------------------------------
    // sourceId — SHA-256(filename + ":" + fileSize)
    // -------------------------------------------------------------------------

    @Test
    void load_computesSourceIdAsSha256OfFilenameAndSize() throws IOException {
        Path file = writeTxt("test.txt", "Source ID test");
        long size = Files.size(file);

        ParsedDocument result = service.load(file);

        assertThat(result.sourceId()).isEqualTo(sha256("test.txt:" + size));
    }

    @Test
    void load_sourceIdIsDeterministicForSameFile() throws IOException {
        Path file = writeTxt("stable.txt", "Deterministic");

        ParsedDocument r1 = service.load(file);
        ParsedDocument r2 = service.load(file);

        assertThat(r1.sourceId()).isEqualTo(r2.sourceId());
    }

    @Test
    void load_differentFilenamesProduceDifferentSourceIds() throws IOException {
        Path a = writeTxt("a.txt", "same");
        Path b = writeTxt("b.txt", "same");

        ParsedDocument ra = service.load(a);
        ParsedDocument rb = service.load(b);

        assertThat(ra.sourceId()).isNotEqualTo(rb.sourceId());
    }

    // -------------------------------------------------------------------------
    // ParsedDocument immutability (issue 4)
    // -------------------------------------------------------------------------

    @Test
    void load_returnedMetadataIsUnmodifiable() throws IOException {
        Path file = writeTxt("immutable.txt", "Some content");

        ParsedDocument result = service.load(file);

        assertThatThrownBy(() -> result.metadata().put("injected", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // Content limit — maxContentChars (issue 2)
    // -------------------------------------------------------------------------

    @Test
    void load_contentExceedingMaxChars_throwsDocumentParseException() throws IOException {
        RagProperties limitedProps = new RagProperties();
        limitedProps.setMaxContentChars(10);
        DocumentLoaderService limitedService =
                new TikaDocumentLoaderService(new AutoDetectParser(), limitedProps);
        Path file = writeTxt("big.txt", "This content is definitely longer than ten characters");

        assertThatThrownBy(() -> limitedService.load(file))
                .isInstanceOf(DocumentParseException.class);
    }

    // -------------------------------------------------------------------------
    // Error handling — Path overload
    // -------------------------------------------------------------------------

    @Test
    void load_emptyFile_throwsDocumentParseException() throws IOException {
        Path empty = tempDir.resolve("empty.txt");
        Files.write(empty, new byte[0]);

        assertThatThrownBy(() -> service.load(empty))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("No content extracted from");
    }

    @Test
    void load_emptyFile_exceptionMessageContainsFilename() throws IOException {
        Path empty = tempDir.resolve("blank.txt");
        Files.write(empty, new byte[0]);

        assertThatThrownBy(() -> service.load(empty))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("blank.txt");
    }

    @Test
    void load_corruptPdf_throwsDocumentParseExceptionWrappingTikaException() throws IOException {
        // Starts with %PDF magic bytes so Tika attempts PDF parse — PDFBox throws TikaException
        Path corrupt = tempDir.resolve("corrupt.pdf");
        byte[] corruptBytes = ("%PDF-1.4\n\u0000\u0001\u0002corrupt\u0003\u0004\u0005\u0006")
                .getBytes(StandardCharsets.ISO_8859_1);
        Files.write(corrupt, corruptBytes);

        assertThatThrownBy(() -> service.load(corrupt))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("corrupt.pdf")
                .hasCauseInstanceOf(TikaException.class);
    }

    @Test
    void load_unsupportedBinaryFile_throwsDocumentParseExceptionWithUnsupportedTypeMessage() throws IOException {
        // Pure binary content — Tika detects as application/octet-stream
        Path bin = tempDir.resolve("data.bin");
        Files.write(bin, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});

        assertThatThrownBy(() -> service.load(bin))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("Unsupported type:");
    }

    // -------------------------------------------------------------------------
    // MultipartFile overload
    // -------------------------------------------------------------------------

    @Test
    void load_multipartFile_returnsNonEmptyContent() {
        MockMultipartFile file = multipart("hello.txt", "Hello from upload");

        ParsedDocument result = service.load(file);

        assertThat(result.content()).isNotBlank();
        assertThat(result.content()).contains("Hello from upload");
    }

    @Test
    void load_multipartFile_usesOriginalFilenameInMetadata() {
        MockMultipartFile file = multipart("report.txt", "Report content");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata().get("filename")).isEqualTo("report.txt");
    }

    @Test
    void load_multipartFile_computesSourceIdFromFilenameAndSize() {
        byte[] content = "Multipart source id".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "upload.txt", "text/plain", content);

        ParsedDocument result = service.load(file);

        assertThat(result.sourceId()).isEqualTo(sha256("upload.txt:" + content.length));
    }

    @Test
    void load_multipartFileEmptyContent_throwsDocumentParseException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.load(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("No content extracted from");
    }

    @Test
    void load_multipartFile_metadataContainsContentType() {
        MockMultipartFile file = multipart("notes.txt", "Some notes");

        ParsedDocument result = service.load(file);

        assertThat(result.metadata()).containsKey("content-type");
    }

    @Test
    void load_multipartFile_nullOriginalFilename_usesUnknownFallback() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "text/plain",
                "Some text content".getBytes(StandardCharsets.UTF_8));

        ParsedDocument result = service.load(file);

        assertThat(result.metadata().get("filename")).isEqualTo("unknown");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path writeTxt(String filename, String content) throws IOException {
        Path path = tempDir.resolve(filename);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeHtml(String filename, String html) throws IOException {
        Path path = tempDir.resolve(filename);
        Files.writeString(path, html, StandardCharsets.UTF_8);
        return path;
    }

    /**
     * Creates a minimal PDF without a proper xref table.
     * PDFBox (used by Tika) has repair mode enabled by default and can recover
     * the content stream, extracting any text operators present.
     */
    private Path writeMinimalPdf(String filename) throws IOException {
        String pdf =
                "%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R" +
                "/Resources<</Font<</F1<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>>>>>endobj\n" +
                "4 0 obj<</Length 44>>\nstream\n" +
                "BT /F1 12 Tf 100 700 Td (Hello PDF) Tj ET\n" +
                "endstream\nendobj\n" +
                "%%EOF\n";
        Path path = tempDir.resolve(filename);
        Files.writeString(path, pdf, StandardCharsets.US_ASCII);
        return path;
    }

    private MockMultipartFile multipart(String filename, String content) {
        return new MockMultipartFile(
                "file", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

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
