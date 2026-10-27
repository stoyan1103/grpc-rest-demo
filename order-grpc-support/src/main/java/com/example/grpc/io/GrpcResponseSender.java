package com.example.grpc.io;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import static java.util.Objects.requireNonNull;

public class GrpcResponseSender {

    private final Function<RuntimeException, StatusRuntimeException> mapper;

    public GrpcResponseSender(Function<RuntimeException, StatusRuntimeException> mapper) {
        this.mapper = requireNonNull(mapper, "mapper must not be null");
    }

    public <T> void sendOne(StreamObserver<T> responseObserver, Supplier<T> supplier) {
        try {
            T response = supplier.get();
            responseObserver.onNext(response);
        } catch (RuntimeException e) {
            responseObserver.onError(resolveException(e));
            return;
        }

        responseObserver.onCompleted();
    }

    public <T> void sendMultiple(StreamObserver<T> responseObserver, Supplier<Stream<T>> supplier) {
        try (Stream<T> responses = supplier.get()) {
            responses.forEach(responseObserver::onNext);
        } catch (RuntimeException e) {
            responseObserver.onError(resolveException(e));
            return;
        }

        responseObserver.onCompleted();
    }

    /**
     * Sends one stream item and reports whether it was delivered. On a runtime failure, sends the
     * mapped error to the observer and returns {@code false}.
     */
    public <T> boolean sendNext(StreamObserver<T> responseObserver, Supplier<T> supplier) {
        try {
            responseObserver.onNext(supplier.get());
            return true;
        } catch (RuntimeException e) {
            responseObserver.onError(resolveException(e));
            return false;
        }
    }

    private StatusRuntimeException resolveException(RuntimeException exception) {
        if (exception instanceof StatusRuntimeException grpcException) {
            return grpcException;
        }

        return mapper.apply(exception);
    }
}
