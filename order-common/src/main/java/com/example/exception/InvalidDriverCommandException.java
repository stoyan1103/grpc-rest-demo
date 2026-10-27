package com.example.exception;

public class InvalidDriverCommandException extends RuntimeException {
    public InvalidDriverCommandException(String message) {
        super(message);
    }

    public InvalidDriverCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
