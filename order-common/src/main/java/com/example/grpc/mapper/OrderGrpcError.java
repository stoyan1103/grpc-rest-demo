package com.example.grpc.mapper;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.exception.InvalidDriverCommandException;
import com.example.exception.InvalidItemProductIdException;
import com.example.exception.InvalidItemQuantityException;
import com.example.exception.InvalidItemWeightException;
import com.example.exception.InvalidOrderException;
import com.example.exception.InvalidQuoteException;
import com.example.exception.OrderNotFoundException;

import io.grpc.Status;
import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.NOT_FOUND;

public enum OrderGrpcError {

    ORDER_NOT_FOUND(OrderNotFoundException.class, NOT_FOUND, OrderNotFoundException::new),
    INVALID_ORDER(InvalidOrderException.class, INVALID_ARGUMENT, InvalidOrderException::new),
    INVALID_QUOTE(InvalidQuoteException.class, INVALID_ARGUMENT, InvalidQuoteException::new),
    INVALID_ITEM_PRODUCT_ID(InvalidItemProductIdException.class, INVALID_ARGUMENT, InvalidItemProductIdException::new),
    INVALID_ITEM_QUANTITY(InvalidItemQuantityException.class, INVALID_ARGUMENT, InvalidItemQuantityException::new),
    INVALID_ITEM_WEIGHT(InvalidItemWeightException.class, INVALID_ARGUMENT, InvalidItemWeightException::new),
    INVALID_DRIVER_COMMAND(InvalidDriverCommandException.class, INVALID_ARGUMENT, InvalidDriverCommandException::new);

    private static final Map<Class<? extends RuntimeException>, OrderGrpcError> MAPPING_BY_EXCEPTION =
        Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(error -> error.exceptionClass, Function.identity()));

    private static final Map<String, OrderGrpcError> MAPPING_BY_NAME =
        Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(OrderGrpcError::name, Function.identity()));

    private final Class<? extends RuntimeException> exceptionClass;
    private final Status status;
    private final BiFunction<String, Throwable, RuntimeException> exceptionFactory;

    OrderGrpcError(
        Class<? extends RuntimeException> exceptionClass,
        Status status,
        BiFunction<String, Throwable, RuntimeException> exceptionFactory
    ) {
        this.exceptionClass = exceptionClass;
        this.status = status;
        this.exceptionFactory = exceptionFactory;
    }

    public static OrderGrpcError fromException(RuntimeException exception) {
        return MAPPING_BY_EXCEPTION.get(exception.getClass());
    }

    public static OrderGrpcError fromName(String name) {
        return MAPPING_BY_NAME.get(name);
    }

    public Status status() {
        return status;
    }

    public RuntimeException createException(String message, Throwable cause) {
        return exceptionFactory.apply(message, cause);
    }
}
