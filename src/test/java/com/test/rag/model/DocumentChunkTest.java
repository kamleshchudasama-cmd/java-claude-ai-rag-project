package com.test.rag.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkTest {

    @Test
    void documentChunk_hasTokenCountField() {
        DocumentChunk chunk = new DocumentChunk("id1", "some content", 0, 3, Map.of("filename", "test.txt"));
        assertThat(chunk.tokenCount()).isEqualTo(3);
    }
}
