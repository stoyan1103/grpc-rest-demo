package com.example.config;

import org.junit.jupiter.api.Test;

import com.example.client.OrderGrpcClient;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc.OrderServiceBlockingStub;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc.OrderServiceStub;

import io.grpc.ManagedChannel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrpcClientConfigTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9090;

    private final GrpcClientConfig classUnderTest = new GrpcClientConfig();

    @Test
    void givenHostAndPort_whenCreatingGrpcBeans_thenChannelStubsAndClientAreCreated() {
        ManagedChannel channel = classUnderTest.orderServiceManagedChannel(HOST, PORT);

        try {
            OrderServiceBlockingStub blockingStub = classUnderTest.orderServiceBlockingStub(channel);
            OrderServiceStub asyncStub = classUnderTest.orderServiceAsyncStub(channel);
            OrderGrpcClient client = classUnderTest.orderGrpcClient(blockingStub, asyncStub);

            assertThat(channel).isNotNull();
            assertThat(blockingStub).isNotNull();
            assertThat(asyncStub).isNotNull();
            assertThat(client).isNotNull();
            assertThat(channel.authority()).isEqualTo(HOST + ":" + PORT);
        } finally {
            assertNotNull(channel);
            channel.shutdownNow();
        }
    }
}
