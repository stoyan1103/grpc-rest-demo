package com.example.grpc.io;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.example.grpc.testing.TestStreamObserver;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import static com.example.grpc.testing.TestStreamObserver.Signal.ON_COMPLETED;
import static com.example.grpc.testing.TestStreamObserver.Signal.ON_ERROR;
import static com.example.grpc.testing.TestStreamObserver.Signal.ON_NEXT;
import static org.assertj.core.api.Assertions.assertThat;

class GrpcResponseSenderTest {

    private static final String SINGLE_RESPONSE = "response";
    private static final String FIRST_RESPONSE = "first";
    private static final String SECOND_RESPONSE = "second";
    private static final String THIRD_RESPONSE = "third";
    private static final String FAILING_RESPONSE = "failing";
    private static final String STREAM_EVENT = "event";
    private static final String INVALID_REQUEST_MESSAGE = "invalid request";
    private static final String STREAM_FAILURE_MESSAGE = "failed while producing response";
    private static final String INVALID_EVENT_MESSAGE = "invalid event";
    private static final String GRPC_ERROR_MESSAGE = "order does not exist";

    private static final List<String> MULTIPLE_RESPONSES = List.of(
        FIRST_RESPONSE,
        SECOND_RESPONSE,
        THIRD_RESPONSE
    );
    private static final StatusRuntimeException MAPPED_EXCEPTION = Status.INVALID_ARGUMENT.asRuntimeException();

    private final List<RuntimeException> exceptionsPassedToMapper = new ArrayList<>();

    private TestStreamObserver<String> responseObserver;

    private GrpcResponseSender classUnderTest;

    @BeforeEach
    void setUp() {
        exceptionsPassedToMapper.clear();
        responseObserver = new TestStreamObserver<>();
        classUnderTest = prepareGrpcResponseSender();
    }

    @Test
    void givenSuccessfulSupplier_whenSendOne_thenResponseIsSentAndCompleted() {
        classUnderTest.sendOne(responseObserver, () -> SINGLE_RESPONSE);

        assertThat(responseObserver.values()).containsExactly(SINGLE_RESPONSE);
        assertThat(responseObserver.errors()).isEmpty();
        assertThat(responseObserver.signals()).containsExactly(ON_NEXT, ON_COMPLETED);
    }

    @Test
    void givenSupplierFailure_whenSendOne_thenFailureIsMappedAndStreamIsNotCompleted() {
        RuntimeException applicationException = new IllegalArgumentException(INVALID_REQUEST_MESSAGE);

        classUnderTest.sendOne(responseObserver, () -> {
            throw applicationException;
        });

        assertThat(responseObserver.values()).isEmpty();
        assertThat(responseObserver.errors()).containsExactly(MAPPED_EXCEPTION);
        assertThat(responseObserver.signals()).containsExactly(ON_ERROR);
        assertThat(exceptionsPassedToMapper).containsExactly(applicationException);
    }

    @ParameterizedTest
    @EnumSource(SenderOperation.class)
    void givenGrpcFailure_whenSend_thenExistingGrpcExceptionIsPreserved(SenderOperation operation) {
        StatusRuntimeException grpcException = Status.NOT_FOUND.withDescription(GRPC_ERROR_MESSAGE).asRuntimeException();

        switch (operation) {
            case SEND_ONE -> classUnderTest.sendOne(responseObserver, () -> {
                throw grpcException;
            });
            case SEND_MULTIPLE -> classUnderTest.sendMultiple(responseObserver, () -> {
                throw grpcException;
            });
            case SEND_NEXT -> classUnderTest.sendNext(responseObserver, () -> {
                throw grpcException;
            });
        }

        assertThat(responseObserver.values()).isEmpty();
        assertThat(responseObserver.errors()).containsExactly(grpcException);
        assertThat(responseObserver.signals()).containsExactly(ON_ERROR);
        assertThat(exceptionsPassedToMapper).isEmpty();
    }

    @Test
    void givenNonEmptyStream_whenSendMultiple_thenEveryResponseIsSentInOrderAndCompleted() {
        AtomicBoolean streamClosed = new AtomicBoolean();

        classUnderTest.sendMultiple(
            responseObserver,
            () -> MULTIPLE_RESPONSES.stream().onClose(() -> streamClosed.set(true))
        );

        assertThat(responseObserver.values()).containsExactlyElementsOf(MULTIPLE_RESPONSES);
        assertThat(responseObserver.errors()).isEmpty();
        assertThat(responseObserver.signals()).containsExactly(
            ON_NEXT,
            ON_NEXT,
            ON_NEXT,
            ON_COMPLETED
        );
        assertThat(streamClosed).isTrue();
    }

    @Test
    void givenEmptyStream_whenSendMultiple_thenResponseStreamIsCompletedWithoutValues() {
        classUnderTest.sendMultiple(responseObserver, Stream::empty);

        assertThat(responseObserver.values()).isEmpty();
        assertThat(responseObserver.errors()).isEmpty();
        assertThat(responseObserver.signals()).containsExactly(ON_COMPLETED);
    }

    @Test
    void givenStreamFailure_whenSendMultiple_thenStreamIsClosedAndFailureIsMapped() {
        RuntimeException applicationException = new IllegalStateException(STREAM_FAILURE_MESSAGE);
        AtomicBoolean streamClosed = new AtomicBoolean();
        Stream<String> responses = Stream.of(FIRST_RESPONSE, FAILING_RESPONSE)
            .peek(value -> {
                if (value.equals(FAILING_RESPONSE)) {
                    throw applicationException;
                }
            })
            .onClose(() -> streamClosed.set(true));

        classUnderTest.sendMultiple(responseObserver, () -> responses);

        assertThat(responseObserver.values()).containsExactly(FIRST_RESPONSE);
        assertThat(responseObserver.errors()).containsExactly(MAPPED_EXCEPTION);
        assertThat(responseObserver.signals()).containsExactly(ON_NEXT, ON_ERROR);
        assertThat(exceptionsPassedToMapper).containsExactly(applicationException);
        assertThat(streamClosed).isTrue();
    }

    @Test
    void givenSuccessfulSupplier_whenSendNext_thenResponseIsSentWithoutCompletingStream() {
        boolean sentSuccessfully = classUnderTest.sendNext(responseObserver, () -> STREAM_EVENT);

        assertThat(sentSuccessfully).isTrue();
        assertThat(responseObserver.values()).containsExactly(STREAM_EVENT);
        assertThat(responseObserver.errors()).isEmpty();
        assertThat(responseObserver.signals()).containsExactly(ON_NEXT);
    }

    @Test
    void givenSupplierFailure_whenSendNext_thenFailureIsMappedAndStreamIsNotCompleted() {
        RuntimeException applicationException = new IllegalArgumentException(INVALID_EVENT_MESSAGE);

        boolean sentSuccessfully = classUnderTest.sendNext(responseObserver, () -> {
            throw applicationException;
        });

        assertThat(sentSuccessfully).isFalse();
        assertThat(responseObserver.values()).isEmpty();
        assertThat(responseObserver.errors()).containsExactly(MAPPED_EXCEPTION);
        assertThat(responseObserver.signals()).containsExactly(ON_ERROR);
        assertThat(exceptionsPassedToMapper).containsExactly(applicationException);
    }

    private GrpcResponseSender prepareGrpcResponseSender() {
        return new GrpcResponseSender(exception -> {
            exceptionsPassedToMapper.add(exception);
            return MAPPED_EXCEPTION;
        });
    }

    private enum SenderOperation {
        SEND_ONE,
        SEND_MULTIPLE,
        SEND_NEXT
    }

}
