package com.example.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.exception.InvalidDriverCommandException;
import com.example.exception.InvalidItemProductIdException;
import com.example.exception.InvalidItemQuantityException;
import com.example.exception.InvalidItemWeightException;
import com.example.exception.InvalidOrderException;
import com.example.exception.InvalidQuoteException;
import com.example.exception.OrderNotFoundException;
import com.example.grpc.mapper.GrpcExceptionMapper;
import com.example.grpc.io.GrpcResponseSender;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.DriverCommandGrpcRequest;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponseList;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc.OrderServiceImplBase;
import com.example.grpcdemo.contract.order.v1.OrderStatus;
import com.example.grpcdemo.contract.order.v1.QuoteGrpcResponse;
import com.example.grpcdemo.contract.order.v1.QuoteItemGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackingEventsGrpcResponse;
import com.example.model.StoredOrder;
import com.example.repository.InMemoryOrderRepository;
import com.google.protobuf.Empty;

import io.grpc.stub.StreamObserver;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.CREATED;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.DELIVERED;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.ON_THE_WAY;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.PICKED_UP;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.PREPARING;
import static java.util.Objects.requireNonNull;

public class OrderGrpcService extends OrderServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderGrpcService.class);

    private static final GrpcResponseSender RESPONSE_SENDER = new GrpcResponseSender(GrpcExceptionMapper::toGrpcException);

    private final InMemoryOrderRepository orderRepository;

    public OrderGrpcService(InMemoryOrderRepository orderRepository) {
        this.orderRepository = requireNonNull(orderRepository, "orderRepository must not be null");
    }

    @Override
    public void createOrder(CreateOrderGrpcRequest request, StreamObserver<CreateOrderGrpcResponse> responseObserver) {
        RESPONSE_SENDER.sendOne(
            responseObserver,
            () -> {
                LOGGER.info("Received request to Create Order for customer '{}'", request.getCustomerName());

                if (request.getCustomerName().isBlank()) {
                    throw new InvalidOrderException("Customer name is required");
                }

                if (request.getAddress().isBlank()) {
                    throw new InvalidOrderException("Address is required");
                }

                if (request.getItemsList().isEmpty()) {
                    throw new InvalidOrderException("At least one order item is required");
                }

                StoredOrder storedOrder = orderRepository.createOrder(request.getCustomerName(), request.getAddress(), request.getItemsList());
                LOGGER.info("Created Order for customer '{}'. orderId={}", request.getCustomerName(), storedOrder.orderId());

                return CreateOrderGrpcResponse.newBuilder()
                    .setOrderId(storedOrder.orderId())
                    .setCustomerName(storedOrder.customerName())
                    .setAddress(storedOrder.address())
                    .setStatus(storedOrder.status())
                    .addAllItems(storedOrder.items())
                    .build();
            }
        );
    }

    @Override
    public void getOrder(GetOrderGrpcRequest request, StreamObserver<GetOrderGrpcResponse> responseObserver) {
        RESPONSE_SENDER.sendOne(
            responseObserver,
            () -> {
                if (request.getOrderId().isBlank()) {
                    throw new InvalidOrderException("order_id is required");
                }

                return orderRepository.findById(request.getOrderId())
                    .map(OrderGrpcService::buildGetOrderResponse)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found: " + request.getOrderId()));
            }
        );
    }

    @Override
    public void listOrders(Empty request, StreamObserver<GetOrderGrpcResponseList> responseObserver) {
        RESPONSE_SENDER.sendOne(
            responseObserver,
            () -> {
                List<StoredOrder> allOrders = orderRepository.findAll();

                return GetOrderGrpcResponseList.newBuilder()
                    .addAllOrders(allOrders.stream()
                        .map(OrderGrpcService::buildGetOrderResponse)
                        .toList()
                    )
                    .build();
            }
        );
    }

    @Override
    public void trackOrder(TrackOrderGrpcRequest request, StreamObserver<TrackingEventsGrpcResponse> responseObserver) {
        RESPONSE_SENDER.sendMultiple(
            responseObserver,
            () -> {
                if (request.getOrderId().isBlank()) {
                    throw new InvalidOrderException("order_id is required");
                }

                StoredOrder order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new OrderNotFoundException("Order not found: " + request.getOrderId()));

                return Stream.of(
                    buildTrackingEvent(order.orderId(), CREATED, "Order received"),
                    buildTrackingEvent(order.orderId(), PREPARING, "Order is being prepared"),
                    buildTrackingEvent(order.orderId(), ON_THE_WAY, "Order is on the way")
                );
            });
    }

    @Override
    public StreamObserver<QuoteItemGrpcRequest> calculateQuote(StreamObserver<QuoteGrpcResponse> responseObserver) {
        List<QuoteItemGrpcRequest> items = new ArrayList<>();

        return new StreamObserver<>() {
            @Override
            public void onNext(QuoteItemGrpcRequest item) {
                items.add(item);
            }

            @Override
            public void onError(Throwable error) {
                // The client stream has terminated with an error.
                // Do not send another response from here.
            }

            @Override
            public void onCompleted() {
                RESPONSE_SENDER.sendOne(
                    responseObserver,
                    () -> {
                        if (items.isEmpty()) {
                            throw new InvalidQuoteException("At least one quote item is required");
                        }

                        items.forEach(OrderGrpcService::validateItem);

                        double totalWeight = items.stream()
                            .mapToDouble(item -> item.getWeight() * item.getQuantity())
                            .reduce(0.0, Double::sum);

                        double deliveryPrice = 5.00 + totalWeight * 1.25;
                        int estimatedMinutes = 30 + (int) Math.ceil(totalWeight);

                        return QuoteGrpcResponse.newBuilder()
                            .setDeliveryPrice(deliveryPrice)
                            .setEstimatedDeliveryTimeMinutes(estimatedMinutes)
                            .build();
                    }
                );
            }
        };
    }

    @Override
    public StreamObserver<DriverCommandGrpcRequest> liveTracking(StreamObserver<TrackingEventsGrpcResponse> responseObserver) {
        return new StreamObserver<>() {
            private boolean responseTerminated;

            @Override
            public void onNext(DriverCommandGrpcRequest command) {
                if (!responseTerminated) {
                    responseTerminated = !RESPONSE_SENDER.sendNext(responseObserver, () -> toTrackingEvent(command));
                }
            }

            @Override
            public void onError(Throwable error) {
                // The client stream failed or was cancelled; the response stream is terminal
                responseTerminated = true;
            }

            @Override
            public void onCompleted() {
                if (!responseTerminated) {
                    responseTerminated = true;
                    responseObserver.onCompleted();
                }
            }
        };
    }

    private static GetOrderGrpcResponse buildGetOrderResponse(StoredOrder storedOrder) {
        return GetOrderGrpcResponse.newBuilder()
            .setOrderId(storedOrder.orderId())
            .setCustomerName(storedOrder.customerName())
            .setAddress(storedOrder.address())
            .setStatus(storedOrder.status())
            .addAllItems(storedOrder.items())
            .build();
    }

    private TrackingEventsGrpcResponse buildTrackingEvent(String orderId, OrderStatus status, String message) {
        return TrackingEventsGrpcResponse.newBuilder()
            .setOrderId(orderId)
            .setStatus(status)
            .setMessage(message)
            .setTimestamp(System.currentTimeMillis())
            .build();
    }

    private static void validateItem(QuoteItemGrpcRequest item) {
        if (item.getProductId().isBlank()) {
            throw new InvalidItemProductIdException("product id is required");
        }

        if (item.getQuantity() <= 0) {
            throw new InvalidItemQuantityException("Item quantity should be positive number");
        }

        if (!Double.isFinite(item.getWeight()) || item.getWeight() <= 0 || item.getWeight() > 100) {
            throw new InvalidItemWeightException("Item weight should be between 0 and 100");
        }
    }

    private TrackingEventsGrpcResponse toTrackingEvent(DriverCommandGrpcRequest command) {
        if (command.getOrderId().isBlank()) {
            throw new InvalidOrderException("order_id is required");
        }

        StoredOrder order = orderRepository.findById(command.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + command.getOrderId()));

        return switch (command.getCommand()) {
            case ACCEPT_ORDER -> buildTrackingEvent(order.orderId(), PREPARING, "Driver accepted order");
            case PICK_UP_ORDER -> buildTrackingEvent(order.orderId(), PICKED_UP, "Order picked up");
            case START_DELIVERY -> buildTrackingEvent(order.orderId(), ON_THE_WAY, "Delivery started");
            case COMPLETE_DELIVERY -> buildTrackingEvent(order.orderId(), DELIVERED, "Delivery completed");
            case DRIVER_COMMAND_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                throw new InvalidDriverCommandException("A valid driver command is required");
        };
    }
}
