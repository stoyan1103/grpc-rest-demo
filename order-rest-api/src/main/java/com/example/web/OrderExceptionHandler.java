package com.example.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.exception.InvalidDriverCommandException;
import com.example.exception.InvalidItemProductIdException;
import com.example.exception.InvalidItemQuantityException;
import com.example.exception.InvalidItemWeightException;
import com.example.exception.InvalidOrderException;
import com.example.exception.InvalidQuoteException;
import com.example.exception.OrderNotFoundException;
import com.example.grpcdemo.rest.generated.model.ErrorResponse;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice(assignableTypes = OrdersController.class)
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class OrderExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFoundException(OrderNotFoundException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderException(InvalidOrderException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidItemProductIdException.class)
    public ResponseEntity<ErrorResponse> handleInvalidItemProductIdException(InvalidItemProductIdException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidItemQuantityException.class)
    public ResponseEntity<ErrorResponse> handleInvalidItemQuantityException(InvalidItemQuantityException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidItemWeightException.class)
    public ResponseEntity<ErrorResponse> handleInvalidItemWeightException(InvalidItemWeightException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidQuoteException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuoteException(InvalidQuoteException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidDriverCommandException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDriverCommandException(InvalidDriverCommandException exception) {
        return GlobalExceptionHandlerHelper.toResponseEntity(BAD_REQUEST, exception.getMessage());
    }
}
