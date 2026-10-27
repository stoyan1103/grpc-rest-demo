package com.example.grpc.testing;

import java.util.ArrayList;
import java.util.List;

import io.grpc.stub.StreamObserver;

public final class TestStreamObserver<T> implements StreamObserver<T> {

    private final List<T> values = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();
    private final List<Signal> signals = new ArrayList<>();

    @Override
    public synchronized void onNext(T value) {
        values.add(value);
        signals.add(Signal.ON_NEXT);
    }

    @Override
    public synchronized void onError(Throwable error) {
        errors.add(error);
        signals.add(Signal.ON_ERROR);
    }

    @Override
    public synchronized void onCompleted() {
        signals.add(Signal.ON_COMPLETED);
    }

    public synchronized List<T> values() {
        return List.copyOf(values);
    }

    public synchronized List<Throwable> errors() {
        return List.copyOf(errors);
    }

    public synchronized List<Signal> signals() {
        return List.copyOf(signals);
    }

    public synchronized long completionCount() {
        return signals.stream().filter(signal -> signal == Signal.ON_COMPLETED).count();
    }

    public enum Signal {
        ON_NEXT,
        ON_ERROR,
        ON_COMPLETED
    }
}
