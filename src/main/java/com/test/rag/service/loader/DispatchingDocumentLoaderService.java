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
