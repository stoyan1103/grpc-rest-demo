package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.example.config.GrpcServerProperties;
import com.example.grpc.testing.TestServerExtension;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc;

import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import static com.example.grpc.testing.TestServer.LifecycleEvent.AWAIT_TERMINATION;
import static com.example.grpc.testing.TestServer.LifecycleEvent.SHUTDOWN;
import static com.example.grpc.testing.TestServer.LifecycleEvent.SHUTDOWN_HOOK_REGISTERED;
import static com.example.grpc.testing.TestServer.LifecycleEvent.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

class OrderGrpcServerApplicationTest {

    @RegisterExtension
    private final TestServerExtension testServer = new TestServerExtension();

    @Test
    void whenCreateServer_thenApplicationServicesAreRegistered() {
        Server server = OrderGrpcServerApplication.createServer(new GrpcServerProperties(0));

        try {
            assertThat(server.getServices())
                .extracting(ServerServiceDefinition::getServiceDescriptor)
                .extracting(ServiceDescriptor::getName)
                .containsExactlyInAnyOrder(
                    OrderServiceGrpc.SERVICE_NAME,
                    HealthGrpc.SERVICE_NAME,
                    ServerReflectionGrpc.SERVICE_NAME
                );
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    void whenStartAndAwait_thenStartsRegistersShutdownHookAndWaits() throws Exception {
        OrderGrpcServerApplication.startAndAwait(
            new GrpcServerProperties(9090),
            testServer.server(),
            testServer::registerShutdownHook
        );

        assertThat(testServer.server().lifecycleEvents())
            .containsExactly(STARTED, SHUTDOWN_HOOK_REGISTERED, AWAIT_TERMINATION);
        assertThat(testServer.registeredShutdownHooks()).hasSize(1);

        testServer.runShutdownHooks();

        assertThat(testServer.server().lifecycleEvents())
            .containsExactly(STARTED, SHUTDOWN_HOOK_REGISTERED, AWAIT_TERMINATION, SHUTDOWN);
        assertThat(testServer.server().isShutdown()).isTrue();
    }
}
