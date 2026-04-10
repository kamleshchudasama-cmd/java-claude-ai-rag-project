package com.test.rag.service.loader;

import com.test.rag.model.ParsedDocument;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Parses raw files (PDF, DOCX, HTML, TXT) into extracted text and metadata.
 * Does NOT perform chunking, embedding, or persistence.
 */
public interface DocumentLoaderService {

    ParsedDocument load(Path filePath);

    ParsedDocument load(MultipartFile file);
}
