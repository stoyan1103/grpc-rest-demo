package com.example.grpc.io;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import static io.grpc.Status.DEADLINE_EXCEEDED;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GrpcResponseReceiver {

    private final Function<StatusRuntimeException, RuntimeException> mapper;

    public GrpcResponseReceiver(Function<StatusRuntimeException, RuntimeException> mapper) {
        this.mapper = requireNonNull(mapper);
    }

    public <T> T receiveOne(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (StatusRuntimeException e) {
            throw mapper.apply(e);
        }
    }

    public <T> List<T> receiveMultiple(Supplier<Iterator<T>> supplier) {
        try {
            Iterator<T> iterator = supplier.get();
            List<T> responses = new ArrayList<>();

            iterator.forEachRemaining(responses::add);

            return List.copyOf(responses);
        } catch (StatusRuntimeException e) {
            throw mapper.apply(e);
        }
    }

    public <Request, Response> Response sendMultipleAndReceiveOne(
        Function<StreamObserver<Response>, StreamObserver<Request>> startCall,
        Consumer<StreamObserver<Request>> sendRequests,
        Duration timeout
    ) {
        requireNonNull(startCall, "startCall must not be null");
        requireNonNull(sendRequests, "sendRequests must not be null");
        requireNonNull(timeout, "timeout must not be null");

        CompletableFuture<Response> responseFuture = new CompletableFuture<>();

        StreamObserver<Response> responseObserver = new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) {
                responseFuture.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                completeExceptionally(responseFuture, error);
            }

            @Override
            public void onCompleted() {
                responseFuture.completeExceptionally(new IllegalStateException("gRPC call completed without a response"));
            }
        };

        return startSendAndAwait(startCall, sendRequests, responseObserver, responseFuture, timeout);
    }

    public <Request, Response> List<Response> sendMultipleAndReceiveMultiple(
        Function<StreamObserver<Response>, StreamObserver<Request>> startCall,
        Consumer<StreamObserver<Request>> sendRequests,
        Duration timeout
    ) {
        requireNonNull(startCall, "startCall must not be null");
        requireNonNull(sendRequests, "sendRequests must not be null");
        requireNonNull(timeout, "timeout must not be null");

        List<Response> responses = new ArrayList<>();
        CompletableFuture<List<Response>> responseFuture = new CompletableFuture<>();

        StreamObserver<Response> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(Response response) {
                responses.add(response);
            }

            @Override
            public void onError(Throwable error) {
                completeExceptionally(responseFuture, error);
            }

            @Override
            public void onCompleted() {
                responseFuture.complete(List.copyOf(responses));
            }
        };

        return startSendAndAwait(startCall, sendRequests, responseObserver, responseFuture, timeout);
    }

    private void completeExceptionally(CompletableFuture<?> future, Throwable error) {
        if (error instanceof StatusRuntimeException grpcException) {
            future.completeExceptionally(mapper.apply(grpcException));
        } else {
            future.completeExceptionally(error);
        }
    }

    private <Request, Response, Result> Result startSendAndAwait(
        Function<StreamObserver<Response>, StreamObserver<Request>> startCall,
        Consumer<StreamObserver<Request>> sendRequests,
        StreamObserver<Response> responseObserver,
        CompletableFuture<Result> responseFuture,
        Duration timeout
    ) {
        try {
            StreamObserver<Request> requestObserver = startCall.apply(responseObserver);
            sendRequests.accept(requestObserver);
            requestObserver.onCompleted();
        } catch (RuntimeException error) {
            completeExceptionally(responseFuture, error);
        }

        return await(responseFuture, timeout);
    }

    private <T> T await(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for gRPC response", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new IllegalStateException("gRPC call failed", cause);
        } catch (TimeoutException e) {
            throw DEADLINE_EXCEEDED
                .withDescription("Timed out waiting for gRPC response")
                .withCause(e)
                .asRuntimeException();
        }
    }
}
