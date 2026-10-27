package com.example.exception;

public class InvalidItemWeightException extends RuntimeException {
    public InvalidItemWeightException(String message) {
        super(message);
    }

    public InvalidItemWeightException(String message, Throwable cause) {
        super(message, cause);
    }
}
