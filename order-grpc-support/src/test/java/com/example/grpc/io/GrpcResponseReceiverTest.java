package com.example.grpc.io;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcResponseReceiverTest {

    private static final String SINGLE_RESPONSE = "response";
    private static final String FIRST_RESPONSE = "first";
    private static final String SECOND_RESPONSE = "second";
    private static final String FIRST_ITEM_REQUEST = "item-1";
    private static final String SECOND_ITEM_REQUEST = "item-2";
    private static final String QUOTE_RESPONSE = "quote";
    private static final String FIRST_COMMAND_REQUEST = "command-1";
    private static final String SECOND_COMMAND_REQUEST = "command-2";
    private static final String SINGLE_COMMAND_REQUEST = "command";
    private static final String UNKNOWN_ORDER_REQUEST = "unknown-order";
    private static final String EVENT_RESPONSE_PREFIX = "event-for-";
    private static final String MAPPED_ERROR_MESSAGE = "mapped order error";
    private static final String NON_GRPC_ERROR_MESSAGE = "test failure";
    private static final String EMPTY_RESPONSE_ERROR_MESSAGE = "gRPC call completed without a response";
    private static final String UNEXPECTED_REQUEST_STREAM_ERROR_MESSAGE = "The receiver should complete, not fail, its request stream";
    private static final String INTERRUPTED_WAIT_ERROR_MESSAGE = "Interrupted while waiting for gRPC response";
    private static final String GRPC_CALL_FAILURE_MESSAGE = "gRPC call failed";

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration NO_RESPONSE_TIMEOUT = Duration.ofMillis(1);
    private static final StatusRuntimeException INVALID_ARGUMENT_EXCEPTION = Status.INVALID_ARGUMENT.asRuntimeException();
    private static final StatusRuntimeException NOT_FOUND_EXCEPTION = Status.NOT_FOUND.asRuntimeException();
    private static final IllegalArgumentException MAPPED_EXCEPTION = new IllegalArgumentException(MAPPED_ERROR_MESSAGE);

    private final GrpcResponseReceiver classUnderTest = new GrpcResponseReceiver(grpcException -> MAPPED_EXCEPTION);

    @Test
    void givenSuccessfulSupplier_whenReceiveOne_thenValueIsReturned() {
        String response = classUnderTest.receiveOne(() -> SINGLE_RESPONSE);

        assertThat(response).isEqualTo(SINGLE_RESPONSE);
    }

    @Test
    void givenGrpcFailure_whenReceiveOne_thenApplicationExceptionIsRestored() {
        assertThatThrownBy(() -> classUnderTest.receiveOne(() -> {
            throw INVALID_ARGUMENT_EXCEPTION;
        })).isSameAs(MAPPED_EXCEPTION);
    }

    @Test
    void givenSuccessfulSupplier_whenReceiveMultiple_thenAllValuesAreReturnedInOrder() {
        List<String> responses = classUnderTest.receiveMultiple(() -> List.of(FIRST_RESPONSE, SECOND_RESPONSE).iterator());

        assertThat(responses).containsExactly(FIRST_RESPONSE, SECOND_RESPONSE);
    }

    @Test
    void givenGrpcFailure_whenReceiveMultiple_thenApplicationExceptionIsRestored() {
        assertThatThrownBy(() -> classUnderTest.receiveMultiple(() -> {
            throw NOT_FOUND_EXCEPTION;
        })).isSameAs(MAPPED_EXCEPTION);
    }

    @Test
    void givenClientStreamCompletesWithOneResponse_whenSendMultipleAndReceiveOne_thenResponseIsReturned() {
        List<String> receivedRequests = new ArrayList<>();

        String response = classUnderTest.sendMultipleAndReceiveOne(
            startFakeCall(
                (request, responseObserver) -> receivedRequests.add(request),
                responseObserver -> {
                    responseObserver.onNext(QUOTE_RESPONSE);
                    responseObserver.onCompleted();
                }
            ),
            requestObserver -> {
                requestObserver.onNext(FIRST_ITEM_REQUEST);
                requestObserver.onNext(SECOND_ITEM_REQUEST);
            },
            TEST_TIMEOUT
        );

        assertThat(receivedRequests).containsExactly(FIRST_ITEM_REQUEST, SECOND_ITEM_REQUEST);
        assertThat(response).isEqualTo(QUOTE_RESPONSE);
    }

    @Test
    void givenClientStreamCompletesWithoutResponse_whenSendMultipleAndReceiveOne_thenIllegalStateIsRaised() {
        assertThatThrownBy(() -> classUnderTest.sendMultipleAndReceiveOne(
            startCallThatIgnoresRequests(StreamObserver::onCompleted),
            requestObserver -> requestObserver.onNext(FIRST_ITEM_REQUEST),
            TEST_TIMEOUT
        ))
            .isExactlyInstanceOf(IllegalStateException.class)
            .hasMessage(EMPTY_RESPONSE_ERROR_MESSAGE);
    }

    @Test
    void givenGrpcErrorDuringClientStreamingCall_whenSendMultipleAndReceiveOne_thenApplicationExceptionIsRestored() {
        assertThatThrownBy(() -> classUnderTest.sendMultipleAndReceiveOne(
            startCallThatIgnoresRequests(responseObserver -> responseObserver.onError(INVALID_ARGUMENT_EXCEPTION)),
            requestObserver -> requestObserver.onNext(FIRST_ITEM_REQUEST),
            TEST_TIMEOUT
        )).isSameAs(MAPPED_EXCEPTION);
    }

    @Test
    void givenBidirectionalCall_whenSendMultipleAndReceiveMultiple_thenAllResponsesAreCollected() {
        List<String> receivedRequests = new ArrayList<>();

        List<String> responses = classUnderTest.sendMultipleAndReceiveMultiple(
            startFakeCall(
                (request, responseObserver) -> {
                    receivedRequests.add(request);
                    responseObserver.onNext(EVENT_RESPONSE_PREFIX + request);
                },
                StreamObserver::onCompleted
            ),
            requestObserver -> {
                requestObserver.onNext(FIRST_COMMAND_REQUEST);
                requestObserver.onNext(SECOND_COMMAND_REQUEST);
            },
            TEST_TIMEOUT
        );

        assertThat(receivedRequests).containsExactly(FIRST_COMMAND_REQUEST, SECOND_COMMAND_REQUEST);
        assertThat(responses).containsExactly(
            EVENT_RESPONSE_PREFIX + FIRST_COMMAND_REQUEST,
            EVENT_RESPONSE_PREFIX + SECOND_COMMAND_REQUEST
        );
    }

    @Test
    void givenBidirectionalCallCompletesWithoutResponses_whenSendMultipleAndReceiveMultiple_thenEmptyListIsReturned() {
        List<String> responses = classUnderTest.sendMultipleAndReceiveMultiple(
            startCallThatIgnoresRequests(StreamObserver::onCompleted),
            requestObserver -> requestObserver.onNext(SINGLE_COMMAND_REQUEST),
            TEST_TIMEOUT
        );

        assertThat(responses).isEmpty();
    }

    @Test
    void givenGrpcErrorDuringBidirectionalCall_whenSendMultipleAndReceiveMultiple_thenApplicationExceptionIsRestored() {
        assertThatThrownBy(() -> classUnderTest.sendMultipleAndReceiveMultiple(
            startCallThatIgnoresRequests(responseObserver -> responseObserver.onError(NOT_FOUND_EXCEPTION)),
            requestObserver -> requestObserver.onNext(UNKNOWN_ORDER_REQUEST),
            TEST_TIMEOUT
        )).isSameAs(MAPPED_EXCEPTION);
    }

    @Test
    void givenNonGrpcFailureDuringBidirectionalCall_whenSendMultipleAndReceiveMultiple_thenOriginalFailureIsPreserved() {
        IllegalStateException failure = new IllegalStateException(NON_GRPC_ERROR_MESSAGE);

        assertThatThrownBy(() -> classUnderTest.sendMultipleAndReceiveMultiple(
            startFakeCall(
                (request, responseObserver) -> responseObserver.onError(failure),
                responseObserver -> {
                }
            ),
            requestObserver -> requestObserver.onNext(SINGLE_COMMAND_REQUEST),
            TEST_TIMEOUT
        )).isSameAs(failure);
    }

    @ParameterizedTest
    @EnumSource(RuntimeFailureStage.class)
    void givenRuntimeFailureDuringCall_whenSendMultipleAndReceiveMultiple_thenFailureIsPropagated(RuntimeFailureStage failureStage) {
        IllegalStateException failure = new IllegalStateException(NON_GRPC_ERROR_MESSAGE);

        assertThatThrownBy(() -> invokeCallWithRuntimeFailure(failureStage, failure)).isSameAs(failure);
    }

    @Test
    void givenThreadIsInterruptedWhileWaiting_whenSendMultipleAndReceiveMultiple_thenInterruptIsRestoredAndWrapped() {
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> classUnderTest.sendMultipleAndReceiveMultiple(
                startCallThatIgnoresRequests(responseObserver -> {
                }),
                requestObserver -> requestObserver.onNext(SINGLE_COMMAND_REQUEST),
                TEST_TIMEOUT
            ))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(INTERRUPTED_WAIT_ERROR_MESSAGE)
                .hasCauseInstanceOf(InterruptedException.class);
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test
    void givenCheckedFailureFromResponseObserver_whenSendMultipleAndReceiveMultiple_thenGrpcCallFailureWrapsCause() {
        IOException failure = new IOException(NON_GRPC_ERROR_MESSAGE);

        assertThatThrownBy(() -> classUnderTest.sendMultipleAndReceiveMultiple(
            startCallThatIgnoresRequests(responseObserver -> responseObserver.onError(failure)),
            requestObserver -> requestObserver.onNext(SINGLE_COMMAND_REQUEST),
            TEST_TIMEOUT
        ))
            .isExactlyInstanceOf(IllegalStateException.class)
            .hasMessage(GRPC_CALL_FAILURE_MESSAGE)
            .hasCause(failure);
    }

    @Test
    void givenNoResponseBeforeTimeout_whenSendMultipleAndReceiveMultiple_thenDeadlineExceededIsRaised() {
        assertThatThrownBy(() -> classUnderTest.sendMultipleAndReceiveMultiple(
            startCallThatIgnoresRequests(responseObserver -> {
            }),
            requestObserver -> requestObserver.onNext(SINGLE_COMMAND_REQUEST),
            NO_RESPONSE_TIMEOUT
        ))
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    }

    private void invokeCallWithRuntimeFailure(RuntimeFailureStage failureStage, RuntimeException failure) {
        Function<StreamObserver<String>, StreamObserver<String>> startCall =
            startCallThatIgnoresRequests(responseObserver -> {
            });
        Consumer<StreamObserver<String>> sendRequests = requestObserver -> {
        };

        switch (failureStage) {
            case START_CALL -> startCall = responseObserver -> {
                throw failure;
            };
            case SEND_REQUESTS -> sendRequests = requestObserver -> {
                throw failure;
            };
            case COMPLETE_REQUEST_STREAM -> startCall = startCallThatIgnoresRequests(responseObserver -> {
                throw failure;
            });
        }

        classUnderTest.sendMultipleAndReceiveMultiple(startCall, sendRequests, TEST_TIMEOUT);
    }

    private static Function<StreamObserver<String>, StreamObserver<String>> startFakeCall(
        BiConsumer<String, StreamObserver<String>> onRequest,
        Consumer<StreamObserver<String>> onRequestCompleted
    ) {
        return responseObserver -> new FakeRequestObserver(responseObserver, onRequest, onRequestCompleted);
    }

    private static Function<StreamObserver<String>, StreamObserver<String>> startCallThatIgnoresRequests(
        Consumer<StreamObserver<String>> onRequestCompleted
    ) {
        return startFakeCall((request, responseObserver) -> {
        }, onRequestCompleted);
    }

    private enum RuntimeFailureStage {
        START_CALL,
        SEND_REQUESTS,
        COMPLETE_REQUEST_STREAM
    }

    private record FakeRequestObserver(
        StreamObserver<String> responseObserver,
        BiConsumer<String, StreamObserver<String>> onRequest,
        Consumer<StreamObserver<String>> onRequestCompleted
    ) implements StreamObserver<String> {

        @Override
        public void onNext(String request) {
            onRequest.accept(request, responseObserver);
        }

        @Override
        public void onError(Throwable error) {
            throw new AssertionError(UNEXPECTED_REQUEST_STREAM_ERROR_MESSAGE, error);
        }

        @Override
        public void onCompleted() {
            onRequestCompleted.accept(responseObserver);
        }
    }
}
