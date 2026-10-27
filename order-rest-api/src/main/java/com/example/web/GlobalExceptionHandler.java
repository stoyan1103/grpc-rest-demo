package com.example.web;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.example.grpcdemo.rest.generated.model.ErrorResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import static com.example.web.GlobalExceptionHandlerHelper.toPublicMessage;
import static java.util.stream.Collectors.joining;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_REQUEST_MESSAGE = "Invalid request";
    private static final String INVALID_REQUEST_BODY_MESSAGE =
        "Request body is malformed or contains an unsupported value";

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleRequestValidationException(Exception exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, toValidationMessage(exception));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception
    ) {
        if (exception.isForReturnValue()) {
            LOGGER.error("Controller returned a value that failed validation", exception);
            return GlobalExceptionHandlerHelper.toResponseEntity(
                INTERNAL_SERVER_ERROR,
                INTERNAL_SERVER_ERROR.getReasonPhrase()
            );
        }

        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, toValidationMessage(exception));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequestBody(HttpMessageNotReadableException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, INVALID_REQUEST_BODY_MESSAGE);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException exception) {
        LOGGER.error("Illegal application state while handling API request", exception);
        return GlobalExceptionHandlerHelper.toResponseEntity(
            INTERNAL_SERVER_ERROR,
            INTERNAL_SERVER_ERROR.getReasonPhrase()
        );
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGrpcException(StatusRuntimeException exception) {
        Status.Code grpcCode = exception.getStatus().getCode();
        HttpStatus httpStatus = toHttpStatus(grpcCode);

        LOGGER.error("gRPC call failed with status {}", grpcCode, exception);
        return GlobalExceptionHandlerHelper.toResponseEntity(httpStatus, toPublicMessage(grpcCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception exception) {
        LOGGER.error("Unhandled API exception", exception);
        return GlobalExceptionHandlerHelper.toResponseEntity(
            INTERNAL_SERVER_ERROR,
            INTERNAL_SERVER_ERROR.getReasonPhrase()
        );
    }

    private static HttpStatus toHttpStatus(Status.Code grpcCode) {
        return switch (grpcCode) {
            case INVALID_ARGUMENT -> BAD_REQUEST;
            case NOT_FOUND -> NOT_FOUND;
            case ALREADY_EXISTS -> CONFLICT;
            case PERMISSION_DENIED -> FORBIDDEN;
            case UNAUTHENTICATED -> UNAUTHORIZED;
            case UNAVAILABLE -> SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> GATEWAY_TIMEOUT;
            default -> INTERNAL_SERVER_ERROR;
        };
    }

    private static String toValidationMessage(Exception exception) {
        Stream<String> messages;

        switch (exception) {
            case MethodArgumentNotValidException methodArgumentException ->
                messages = methodArgumentException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage());
            case ConstraintViolationException constraintViolationException ->
                messages = constraintViolationException.getConstraintViolations().stream()
                    .map(violation -> simplifyPropertyPath(violation.getPropertyPath().toString())
                        + ": " + violation.getMessage());
            case HandlerMethodValidationException methodValidationException -> messages = Stream.concat(
                methodValidationException.getParameterValidationResults().stream()
                    .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> validationMessage(
                            error instanceof FieldError fieldError
                                ? fieldError.getField()
                                : parameterName(result.getMethodParameter().getParameterName()),
                            error
                        ))),
                methodValidationException.getCrossParameterValidationResults().stream()
                    .map(error -> validationMessage("request", error))
            );
            default -> {
                return INVALID_REQUEST_MESSAGE;
            }
        }

        String message = messages
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .sorted()
            .collect(joining("; "));
        return message.isBlank() ? INVALID_REQUEST_MESSAGE : message;
    }

    private static String simplifyPropertyPath(String propertyPath) {
        int methodSeparator = propertyPath.indexOf('.');
        return methodSeparator < 0 ? propertyPath : propertyPath.substring(methodSeparator + 1);
    }

    private static String parameterName(String parameterName) {
        return parameterName == null ? "request" : parameterName;
    }

    private static String validationMessage(String name, MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? null : name + ": " + message;
    }
}
