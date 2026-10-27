package com.example.grpc.mapper;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.exception.InvalidOrderException;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import static io.grpc.Metadata.BINARY_BYTE_MARSHALLER;
import static io.grpc.Status.Code.INTERNAL;
import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.PERMISSION_DENIED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class GrpcExceptionMapperTest {

    private static final String ERROR_MESSAGE = "Order data is invalid";
    private static final Metadata.Key<byte[]> ERROR_NAME_TRAILER =
        Metadata.Key.of("error_name-bin", BINARY_BYTE_MARSHALLER);

    @ParameterizedTest
    @EnumSource(OrderGrpcError.class)
    void givenKnownApplicationException_whenToGrpcException_thenStatusAndTrailerIdentifyError(OrderGrpcError error) {
        RuntimeException applicationException = error.createException(ERROR_MESSAGE, null);

        StatusRuntimeException grpcException = GrpcExceptionMapper.toGrpcException(applicationException);

        assertThat(grpcException.getStatus().getCode()).isEqualTo(error.status().getCode());
        assertThat(grpcException.getStatus().getDescription()).isEqualTo(ERROR_MESSAGE);
        assertThat(grpcException.getCause()).isSameAs(applicationException);
        assertThat(grpcException.getTrailers()).isNotNull();
        assertThat(grpcException.getTrailers().get(ERROR_NAME_TRAILER))
            .containsExactly(error.name().getBytes(UTF_8));
    }

    @Test
    void givenUnknownApplicationException_whenToGrpcException_thenInternalDetailsAreNotExposed() {
        RuntimeException applicationException = new IllegalStateException("database password is invalid");

        StatusRuntimeException grpcException = GrpcExceptionMapper.toGrpcException(applicationException);

        assertThat(grpcException.getStatus().getCode()).isEqualTo(INTERNAL);
        assertThat(grpcException.getStatus().getDescription()).isEqualTo("Internal server error");
        assertThat(grpcException.getStatus().getDescription()).doesNotContain(applicationException.getMessage());
        assertThat(grpcException.getCause()).isSameAs(applicationException);
        assertThat(grpcException.getTrailers()).isNotNull();
        assertThat(grpcException.getTrailers().get(ERROR_NAME_TRAILER)).isNull();
    }

    @ParameterizedTest
    @EnumSource(OrderGrpcError.class)
    void givenKnownGrpcError_whenToException_thenOriginalErrorTypeIsRestored(OrderGrpcError error) {
        RuntimeException originalException = error.createException(ERROR_MESSAGE, null);
        StatusRuntimeException grpcException = GrpcExceptionMapper.toGrpcException(originalException);

        RuntimeException applicationException = GrpcExceptionMapper.toException(grpcException);

        assertThat(applicationException)
            .isExactlyInstanceOf(originalException.getClass())
            .hasMessage(ERROR_MESSAGE)
            .hasCause(grpcException);
    }

    @ParameterizedTest
    @MethodSource("unmappedGrpcErrorsProvider")
    void givenGrpcErrorWithoutResolvableApplicationMapping_whenToException_thenGrpcExceptionIsPreserved(
        String scenario,
        StatusRuntimeException grpcException
    ) {
        RuntimeException applicationException = GrpcExceptionMapper.toException(grpcException);

        assertThat(applicationException).isSameAs(grpcException);
    }

    private static Stream<Arguments> unmappedGrpcErrorsProvider() {
        return Stream.of(
            Arguments.of(
                "no trailers",
                PERMISSION_DENIED.withDescription("Access denied").asRuntimeException()
            ),
            Arguments.of(
                "trailers without an error name",
                INVALID_ARGUMENT.withDescription(ERROR_MESSAGE).asRuntimeException(new Metadata())
            ),
            Arguments.of(
                "unknown error name",
                prepareGrpcException(INVALID_ARGUMENT, "FUTURE_ERROR")
            ),
            Arguments.of(
                "error name with mismatched status",
                prepareGrpcException(INVALID_ARGUMENT, OrderGrpcError.ORDER_NOT_FOUND.name())
            )
        );
    }

    @ParameterizedTest
    @MethodSource("missingOrBlankDescriptionsProvider")
    void givenKnownErrorWithNullOrBlankDescription_whenToException_thenSafeFallbackMessageIsUsed(
        String scenario,
        String description
    ) {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_NAME_TRAILER, OrderGrpcError.INVALID_ORDER.name().getBytes(UTF_8));
        Status status = INVALID_ARGUMENT;
        if (description != null) {
            status = status.withDescription(description);
        }
        StatusRuntimeException grpcException = status.asRuntimeException(trailers);

        RuntimeException applicationException = GrpcExceptionMapper.toException(grpcException);

        assertThat(applicationException)
            .isExactlyInstanceOf(InvalidOrderException.class)
            .hasMessage("gRPC call failed with status code " + INVALID_ARGUMENT.getCode())
            .hasCause(grpcException);
    }

    private static Stream<Arguments> missingOrBlankDescriptionsProvider() {
        return Stream.of(
            Arguments.of("null description", null),
            Arguments.of("blank description", "   ")
        );
    }

    private static StatusRuntimeException prepareGrpcException(Status status, String errorName) {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_NAME_TRAILER, errorName.getBytes(UTF_8));
        return status.withDescription(ERROR_MESSAGE).asRuntimeException(trailers);
    }
}
