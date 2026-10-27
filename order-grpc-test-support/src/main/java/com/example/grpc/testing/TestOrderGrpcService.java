package com.example.grpc.testing;

import java.util.ArrayList;
import java.util.List;

import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.DriverCommandGrpcRequest;
import com.example.grpcdemo.contract.order.v1.DriverCommandType;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponseList;
import com.example.grpcdemo.contract.order.v1.OrderItemGrpc;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc;
import com.example.grpcdemo.contract.order.v1.OrderStatus;
import com.example.grpcdemo.contract.order.v1.QuoteGrpcResponse;
import com.example.grpcdemo.contract.order.v1.QuoteItemGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackingEventsGrpcResponse;
import com.google.protobuf.Empty;

import io.grpc.stub.StreamObserver;
import static com.example.grpc.testing.OrderGrpcTestData.ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.CUSTOMER_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ID;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ITEM;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_CUSTOMER_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_ORDER_ID;
import static java.util.Objects.requireNonNull;

/**
 * A controllable gRPC service stub for tests of clients that call the order API.
 */
public final class TestOrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private static final String FAILURE_ERROR_MESSAGE = "failure must not be null";

    private final List<CreateOrderGrpcRequest> createRequests = new ArrayList<>();
    private final List<GetOrderGrpcRequest> getRequests = new ArrayList<>();
    private final List<Empty> listRequests = new ArrayList<>();
    private final List<TrackOrderGrpcRequest> trackRequests = new ArrayList<>();
    private final List<QuoteItemGrpcRequest> quoteRequests = new ArrayList<>();
    private final List<DriverCommandGrpcRequest> driverCommands = new ArrayList<>();

    private Throwable getOrderFailure;
    private Throwable quoteFailure;
    private Throwable liveTrackingFailure;

    public List<CreateOrderGrpcRequest> createRequests() {
        return List.copyOf(createRequests);
    }

    public List<GetOrderGrpcRequest> getRequests() {
        return List.copyOf(getRequests);
    }

    public List<Empty> listRequests() {
        return List.copyOf(listRequests);
    }

    public List<TrackOrderGrpcRequest> trackRequests() {
        return List.copyOf(trackRequests);
    }

    public List<QuoteItemGrpcRequest> quoteRequests() {
        return List.copyOf(quoteRequests);
    }

    public List<DriverCommandGrpcRequest> driverCommands() {
        return List.copyOf(driverCommands);
    }

    public void failGetOrderWith(Throwable failure) {
        getOrderFailure = requireNonNull(failure, FAILURE_ERROR_MESSAGE);
    }

    public void failQuoteWith(Throwable failure) {
        quoteFailure = requireNonNull(failure, FAILURE_ERROR_MESSAGE);
    }

    public void failLiveTrackingWith(Throwable failure) {
        liveTrackingFailure = requireNonNull(failure, FAILURE_ERROR_MESSAGE);
    }

    @Override
    public void createOrder(CreateOrderGrpcRequest request, StreamObserver<CreateOrderGrpcResponse> responseObserver) {
        createRequests.add(request);
        responseObserver.onNext(CreateOrderGrpcResponse.newBuilder()
            .setOrderId(ORDER_ID)
            .setCustomerName(request.getCustomerName())
            .setAddress(request.getAddress())
            .setStatus(OrderStatus.CREATED)
            .addAllItems(request.getItemsList())
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getOrder(GetOrderGrpcRequest request, StreamObserver<GetOrderGrpcResponse> responseObserver) {
        getRequests.add(request);
        if (getOrderFailure != null) {
            responseObserver.onError(getOrderFailure);
            return;
        }

        responseObserver.onNext(prepareOrderResponse(ORDER_ID, CUSTOMER_NAME, ADDRESS, OrderStatus.CREATED, List.of(ORDER_ITEM)));
        responseObserver.onCompleted();
    }

    @Override
    public void listOrders(Empty request, StreamObserver<GetOrderGrpcResponseList> responseObserver) {
        listRequests.add(request);
        responseObserver.onNext(GetOrderGrpcResponseList.newBuilder()
            .addOrders(prepareOrderResponse(ORDER_ID, CUSTOMER_NAME, ADDRESS, OrderStatus.CREATED, List.of(ORDER_ITEM)))
            .addOrders(prepareOrderResponse(SECOND_ORDER_ID, SECOND_CUSTOMER_NAME, SECOND_ADDRESS, OrderStatus.PREPARING, List.of()))
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void trackOrder(TrackOrderGrpcRequest request, StreamObserver<TrackingEventsGrpcResponse> responseObserver) {
        trackRequests.add(request);
        responseObserver.onNext(prepareTrackingEvent(ORDER_ID, OrderStatus.CREATED, "Order received", 1000L));
        responseObserver.onNext(prepareTrackingEvent(ORDER_ID, OrderStatus.PREPARING, "Being prepared", 2000L));
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<QuoteItemGrpcRequest> calculateQuote(StreamObserver<QuoteGrpcResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(QuoteItemGrpcRequest request) {
                quoteRequests.add(request);
            }

            @Override
            public void onError(Throwable error) {
                // Client cancellation is not part of the normal client-mapping scenarios in these tests.
            }

            @Override
            public void onCompleted() {
                if (quoteFailure != null) {
                    responseObserver.onError(quoteFailure);
                    return;
                }

                responseObserver.onNext(QuoteGrpcResponse.newBuilder()
                    .setDeliveryPrice(17.5)
                    .setEstimatedDeliveryTimeMinutes(40)
                    .build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<DriverCommandGrpcRequest> liveTracking(StreamObserver<TrackingEventsGrpcResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(DriverCommandGrpcRequest command) {
                driverCommands.add(command);
                if (command.getCommand() == DriverCommandType.ACCEPT_ORDER) {
                    responseObserver.onNext(prepareTrackingEvent(ORDER_ID, OrderStatus.PREPARING, "accepted", 3000L));
                } else if (command.getCommand() == DriverCommandType.PICK_UP_ORDER) {
                    responseObserver.onNext(prepareTrackingEvent(ORDER_ID, OrderStatus.PICKED_UP, "picked up", 4000L));
                }
            }

            @Override
            public void onError(Throwable error) {
                // Client cancellation is not part of the normal client-mapping scenarios in these tests.
            }

            @Override
            public void onCompleted() {
                if (liveTrackingFailure != null) {
                    responseObserver.onError(liveTrackingFailure);
                    return;
                }
                responseObserver.onCompleted();
            }
        };
    }

    private static TrackingEventsGrpcResponse prepareTrackingEvent(String orderId, OrderStatus status, String message, long timestamp) {
        return TrackingEventsGrpcResponse.newBuilder()
            .setOrderId(orderId)
            .setStatus(status)
            .setMessage(message)
            .setTimestamp(timestamp)
            .build();
    }

    private static GetOrderGrpcResponse prepareOrderResponse(
        String orderId,
        String customerName,
        String address,
        OrderStatus status,
        List<OrderItemGrpc> items
    ) {
        return GetOrderGrpcResponse.newBuilder()
            .setOrderId(orderId)
            .setCustomerName(customerName)
            .setAddress(address)
            .setStatus(status)
            .addAllItems(items)
            .build();
    }
}
