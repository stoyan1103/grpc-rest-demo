package com.example.grpc.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;

/** In-memory gRPC server test double that records lifecycle calls without opening a port. */
public final class TestServer extends Server {

    private final List<LifecycleEvent> lifecycleEvents = new ArrayList<>();
    private boolean shutdown;

    @Override
    public synchronized Server start() {
        lifecycleEvents.add(LifecycleEvent.STARTED);
        return this;
    }

    public synchronized void recordShutdownHookRegistered() {
        lifecycleEvents.add(LifecycleEvent.SHUTDOWN_HOOK_REGISTERED);
    }

    @Override
    public synchronized Server shutdown() {
        lifecycleEvents.add(LifecycleEvent.SHUTDOWN);
        shutdown = true;
        return this;
    }

    @Override
    public synchronized Server shutdownNow() {
        lifecycleEvents.add(LifecycleEvent.SHUTDOWN_NOW);
        shutdown = true;
        return this;
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown;
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) {
        return shutdown;
    }

    @Override
    public synchronized void awaitTermination() {
        lifecycleEvents.add(LifecycleEvent.AWAIT_TERMINATION);
    }

    public synchronized List<LifecycleEvent> lifecycleEvents() {
        return List.copyOf(lifecycleEvents);
    }

    public enum LifecycleEvent {
        STARTED,
        SHUTDOWN_HOOK_REGISTERED,
        AWAIT_TERMINATION,
        SHUTDOWN,
        SHUTDOWN_NOW
    }
}
