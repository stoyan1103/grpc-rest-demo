package com.example.exception;

public class InvalidQuoteException extends RuntimeException {
    public InvalidQuoteException(String message) {
        super(message);
    }

    public InvalidQuoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
