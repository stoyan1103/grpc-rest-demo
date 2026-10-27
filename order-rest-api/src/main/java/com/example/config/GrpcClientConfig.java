package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.client.OrderGrpcClient;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc.OrderServiceStub;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc.OrderServiceBlockingStub;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel orderServiceManagedChannel(
        @Value("${grpc.order-service.host}") String host,
        @Value("${grpc.order-service.port}") int port
    ) {
        return NettyChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build();
    }

    @Bean
    public OrderServiceBlockingStub orderServiceBlockingStub(ManagedChannel orderServiceManagedChannel) {
        return OrderServiceGrpc.newBlockingStub(orderServiceManagedChannel);
    }

    @Bean
    public OrderServiceStub orderServiceAsyncStub(ManagedChannel orderServiceManagedChannel) {
        return OrderServiceGrpc.newStub(orderServiceManagedChannel);
    }

    @Bean
    public OrderGrpcClient orderGrpcClient(OrderServiceBlockingStub blockingStub, OrderServiceStub asyncStub) {
        return new OrderGrpcClient(blockingStub, asyncStub);
    }
}
