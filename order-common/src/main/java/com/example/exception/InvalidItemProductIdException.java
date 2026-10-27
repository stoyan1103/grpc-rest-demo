package com.example.exception;

public class InvalidItemProductIdException extends RuntimeException {

    public InvalidItemProductIdException(String message) {
        super(message);
    }

    public InvalidItemProductIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
