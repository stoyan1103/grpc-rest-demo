package com.example.exception;

public class InvalidItemQuantityException extends RuntimeException {

    public InvalidItemQuantityException(String message) {
        super(message);
    }

    public InvalidItemQuantityException(String message, Throwable cause) {
        super(message, cause);
    }
}
