package com.example.grpc.testing;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import static java.util.Objects.requireNonNull;

/** Starts an in-process gRPC server for one test and closes its channel and server afterward. */
public final class TestInProcessGrpcServerExtension implements BeforeEachCallback, AfterEachCallback {

    private final BindableService service;
    private Server server;
    private ManagedChannel channel;

    public TestInProcessGrpcServerExtension(BindableService service) {
        this.service = requireNonNull(service, "service must not be null");
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(service)
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .build();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
        }
        if (server != null) {
            server.shutdownNow();
            server = null;
        }
    }

    public ManagedChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("The in-process gRPC server has not started");
        }
        return channel;
    }
}
