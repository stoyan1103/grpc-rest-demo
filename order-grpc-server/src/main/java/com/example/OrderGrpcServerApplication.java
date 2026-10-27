package com.example;

import java.io.IOException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.GrpcServerProperties;
import com.example.repository.InMemoryOrderRepository;
import com.example.service.OrderGrpcService;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;

public class OrderGrpcServerApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderGrpcServerApplication.class);

    public static void main(String[] args) throws Exception {
        GrpcServerProperties props = GrpcServerProperties.fromEnvironment(System.getenv());
        Server server = createServer(props);
        startAndAwait(props, server, Runtime.getRuntime()::addShutdownHook);
    }

    static void startAndAwait(GrpcServerProperties props, Server server, Consumer<Thread> shutdownHookRegistrar)
        throws IOException, InterruptedException {
        server.start();

        LOGGER.info("gRPC server started on port {}", props.port());

        shutdownHookRegistrar.accept(new Thread(() -> {
            LOGGER.info("Shutting down gRPC server...");
            server.shutdown();

            LOGGER.info("gRPC server stopped.");
        }));

        server.awaitTermination();
    }

    static Server createServer(GrpcServerProperties props) {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        OrderGrpcService orderGrpcService = new OrderGrpcService(orderRepository);
        HealthStatusManager healthStatusManager = new HealthStatusManager();

        return NettyServerBuilder
            .forPort(props.port())
            .addService(orderGrpcService)
            .addService(healthStatusManager.getHealthService())
            .addService(ProtoReflectionServiceV1.newInstance())
            .build();
    }
}
