package com.example.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.grpcdemo.rest.generated.model.ErrorResponse;

import io.grpc.Status;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

public final class GlobalExceptionHandlerHelper {

    private GlobalExceptionHandlerHelper() {
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(HttpStatus status, String message) {
        ErrorResponse errorResponse = new ErrorResponse()
            .code(status.value())
            .message(message);

        return ResponseEntity.status(status).body(errorResponse);
    }

    public static String toPublicMessage(Status.Code grpcCode) {
        return switch (grpcCode) {
            case INVALID_ARGUMENT -> "Invalid order request";
            case NOT_FOUND -> "Order not found";
            case ALREADY_EXISTS -> "Order already exists";
            case PERMISSION_DENIED -> "Permission denied";
            case UNAUTHENTICATED -> "Authentication required";
            case UNAVAILABLE -> "Order service unavailable";
            case DEADLINE_EXCEEDED -> "Order service timed out";
            default -> INTERNAL_SERVER_ERROR.getReasonPhrase();
        };
    }
}
