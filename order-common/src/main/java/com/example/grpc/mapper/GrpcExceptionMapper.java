package com.example.grpc.mapper;

import java.nio.charset.StandardCharsets;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import static io.grpc.Metadata.BINARY_BYTE_MARSHALLER;
import static io.grpc.Status.INTERNAL;

public final class GrpcExceptionMapper {

    private static final Metadata.Key<byte[]> ERROR_NAME_TRAILER =
        Metadata.Key.of("error_name-bin", BINARY_BYTE_MARSHALLER);

    public static StatusRuntimeException toGrpcException(RuntimeException exception) {
        OrderGrpcError error = OrderGrpcError.fromException(exception);
        Status status = error == null ? INTERNAL : error.status();
        String description = error == null ? "Internal server error" : exception.getMessage();

        Metadata trailers = new Metadata();
        if (error != null) {
            trailers.put(ERROR_NAME_TRAILER, error.name().getBytes(StandardCharsets.UTF_8));
        }

        return status
            .withDescription(description)
            .withCause(exception)
            .asRuntimeException(trailers);
    }

    public static RuntimeException toException(StatusRuntimeException grpcException) {
        String errorName = getErrorName(grpcException);
        if (errorName == null) {
            return grpcException;
        }

        OrderGrpcError error = OrderGrpcError.fromName(errorName);
        if (error == null || error.status().getCode() != grpcException.getStatus().getCode()) {
            return grpcException;
        }

        String message = grpcException.getStatus().getDescription();
        if (message == null || message.isBlank()) {
            message = "gRPC call failed with status code " + grpcException.getStatus().getCode();
        }

        return error.createException(message, grpcException);
    }

    private static String getErrorName(StatusRuntimeException grpcException) {
        Metadata trailers = grpcException.getTrailers();
        if (trailers == null) {
            return null;
        }

        byte[] errorName = trailers.get(ERROR_NAME_TRAILER);
        return errorName == null ? null : new String(errorName, StandardCharsets.UTF_8);
    }

    private GrpcExceptionMapper() {
    }
}
