package com.test.rag.exception;

public class ChunkingException extends RuntimeException {

    public ChunkingException(String message) {
        super(message);
    }

    public ChunkingException(String message, Throwable cause) {
        super(message, cause);
    }
}
