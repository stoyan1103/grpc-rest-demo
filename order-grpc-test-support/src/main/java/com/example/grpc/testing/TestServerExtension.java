package com.example.grpc.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.Objects.requireNonNull;

/**
 * Creates a {@link TestServer} for each test and runs registered shutdown hooks after the test.
 */
public final class TestServerExtension implements BeforeEachCallback, AfterEachCallback {

    private static final String NOT_STARTED_MESSAGE = "The test server has not been initialized";
    private static final String HOOK_REQUIRED_MESSAGE = "shutdownHook must not be null";

    private final List<Thread> shutdownHooks = new ArrayList<>();
    private final Set<Thread> executedShutdownHooks = new HashSet<>();
    private TestServer testServer;

    @Override
    public void beforeEach(ExtensionContext context) {
        testServer = new TestServer();
        shutdownHooks.clear();
        executedShutdownHooks.clear();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        runShutdownHooks();
    }

    public TestServer server() {
        if (testServer == null) {
            throw new IllegalStateException(NOT_STARTED_MESSAGE);
        }
        return testServer;
    }

    public void registerShutdownHook(Thread shutdownHook) {
        shutdownHooks.add(requireNonNull(shutdownHook, HOOK_REQUIRED_MESSAGE));
        server().recordShutdownHookRegistered();
    }

    public List<Thread> registeredShutdownHooks() {
        return List.copyOf(shutdownHooks);
    }

    public void runShutdownHooks() {
        for (Thread shutdownHook : shutdownHooks) {
            if (executedShutdownHooks.add(shutdownHook)) {
                shutdownHook.run();
            }
        }
    }
}
