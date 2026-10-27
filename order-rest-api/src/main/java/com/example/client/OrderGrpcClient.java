package com.example.client;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.grpc.io.GrpcResponseReceiver;
import com.example.grpc.mapper.GrpcExceptionMapper;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.DriverCommandGrpcRequest;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponseList;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc.OrderServiceBlockingStub;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc.OrderServiceStub;
import com.example.grpcdemo.contract.order.v1.QuoteGrpcResponse;
import com.example.grpcdemo.contract.order.v1.QuoteItemGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackingEventsGrpcResponse;
import com.example.grpcdemo.rest.generated.model.CreateOrderRequest;
import com.example.grpcdemo.rest.generated.model.CreateOrderResponse;
import com.example.grpcdemo.rest.generated.model.GetOrderResponse;
import com.example.grpcdemo.rest.generated.model.LiveTrackingCommandRequest;
import com.example.grpcdemo.rest.generated.model.QuoteItemRequest;
import com.example.grpcdemo.rest.generated.model.QuoteResponse;
import com.example.grpcdemo.rest.generated.model.TrackingEventResponse;
import com.example.util.mapper.OrderGrpcMapper;
import com.google.protobuf.Empty;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class OrderGrpcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderGrpcClient.class);

    private static final GrpcResponseReceiver RESPONSE_RECEIVER = new GrpcResponseReceiver(GrpcExceptionMapper::toException);

    private final OrderServiceBlockingStub blockingStub;
    private final OrderServiceStub asyncStub;

    public OrderGrpcClient(OrderServiceBlockingStub blockingStub, OrderServiceStub asyncStub) {
        this.blockingStub = requireNonNull(blockingStub, "OrderServiceBlockingStub must not be null");
        this.asyncStub = requireNonNull(asyncStub, "OrderServiceStub must not be null");
    }

    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
        CreateOrderGrpcRequest grpcRequest = OrderGrpcMapper.toGrpc(createOrderRequest);

        LOGGER.info("Calling gRPC CreateOrder for customer={}", createOrderRequest.getCustomerName());

        CreateOrderGrpcResponse grpcResponse = RESPONSE_RECEIVER.receiveOne(() ->
            blockingStub.withDeadlineAfter(3, SECONDS)
                .createOrder(grpcRequest));

        LOGGER.info("gRPC CreateOrder completed. orderId={}", grpcResponse.getOrderId());

        return OrderGrpcMapper.toDomain(grpcResponse);
    }

    public GetOrderResponse getOrder(String orderId) {
        GetOrderGrpcRequest grpcRequest = GetOrderGrpcRequest.newBuilder()
            .setOrderId(orderId)
            .build();

        LOGGER.info("Calling gRPC GetOrder for orderId={}", orderId);

        GetOrderGrpcResponse grpcResponse = RESPONSE_RECEIVER.receiveOne(() ->
            blockingStub.withDeadlineAfter(3, SECONDS)
                .getOrder(grpcRequest)
        );

        LOGGER.info("gRPC GetOrder completed. orderId={}", grpcResponse.getOrderId());
        return OrderGrpcMapper.toDomain(grpcResponse);
    }

    public List<GetOrderResponse> getOrders() {
        LOGGER.info("Calling gRPC getOrders");

        GetOrderGrpcResponseList grpcResponse = RESPONSE_RECEIVER.receiveOne(() ->
            blockingStub.withDeadlineAfter(3, SECONDS)
                .listOrders(Empty.getDefaultInstance()));

        return grpcResponse.getOrdersList().stream()
            .map(OrderGrpcMapper::toDomain)
            .toList();
    }

    public List<TrackingEventResponse> getTrackingEvents(String orderId) {
        LOGGER.info("Calling gRPC getTrackingEvents");

        TrackOrderGrpcRequest request = TrackOrderGrpcRequest.newBuilder()
            .setOrderId(orderId)
            .build();

        List<TrackingEventsGrpcResponse> grpcEvents = RESPONSE_RECEIVER.receiveMultiple(() ->
            blockingStub.withDeadlineAfter(3, SECONDS)
                .trackOrder(request)
        );

        return grpcEvents.stream()
            .map(OrderGrpcMapper::toDomain)
            .toList();
    }

    public QuoteResponse calculateQuote(List<QuoteItemRequest> quoteItemRequests) {
        LOGGER.info("Calling gRPC calculateQuote");

        List<QuoteItemGrpcRequest> grpcRequests = quoteItemRequests.stream()
            .map(OrderGrpcMapper::toGrpc)
            .toList();

        QuoteGrpcResponse grpcResponse = RESPONSE_RECEIVER.sendMultipleAndReceiveOne(
            responseObserver -> asyncStub.withDeadlineAfter(3, SECONDS).calculateQuote(responseObserver),
            requestObserver -> grpcRequests.forEach(requestObserver::onNext),
            Duration.ofSeconds(4)
        );

        return OrderGrpcMapper.toDomain(grpcResponse);
    }

    public List<TrackingEventResponse> liveTrackOrder(String orderId, List<LiveTrackingCommandRequest> commands) {
        List<DriverCommandGrpcRequest> grpcDriverCommands = commands.stream()
            .map(command -> OrderGrpcMapper.toGrpc(orderId, command))
            .toList();

        List<TrackingEventsGrpcResponse> grpcEventsResponse = RESPONSE_RECEIVER.sendMultipleAndReceiveMultiple(
            responseObserver -> asyncStub.withDeadlineAfter(3, SECONDS).liveTracking(responseObserver),
            requestObserver -> grpcDriverCommands.forEach(requestObserver::onNext),
            Duration.ofSeconds(4)
        );

        return grpcEventsResponse.stream()
            .map(OrderGrpcMapper::toDomain)
            .toList();
    }
}
