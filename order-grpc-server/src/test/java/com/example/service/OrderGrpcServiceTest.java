package com.example.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.grpc.mapper.OrderGrpcError;
import com.example.grpc.testing.TestStreamObserver;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.DriverCommandGrpcRequest;
import com.example.grpcdemo.contract.order.v1.DriverCommandType;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponseList;
import com.example.grpcdemo.contract.order.v1.OrderItemGrpc;
import com.example.grpcdemo.contract.order.v1.QuoteGrpcResponse;
import com.example.grpcdemo.contract.order.v1.QuoteItemGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackingEventsGrpcResponse;
import com.example.model.StoredOrder;
import com.example.repository.InMemoryOrderRepository;
import com.google.protobuf.Empty;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import static com.example.grpc.mapper.OrderGrpcError.INVALID_DRIVER_COMMAND;
import static com.example.grpc.mapper.OrderGrpcError.INVALID_ORDER;
import static com.example.grpc.mapper.OrderGrpcError.ORDER_NOT_FOUND;
import static com.example.grpc.testing.OrderGrpcTestData.ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.CUSTOMER_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ITEM;
import static com.example.grpc.testing.OrderGrpcTestData.PRODUCT_ID;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_CUSTOMER_NAME;
import static com.example.grpc.testing.TestStreamObserver.Signal.ON_COMPLETED;
import static com.example.grpc.testing.TestStreamObserver.Signal.ON_ERROR;
import static com.example.grpcdemo.contract.order.v1.DriverCommandType.DRIVER_COMMAND_TYPE_UNSPECIFIED_VALUE;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.CREATED;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.DELIVERED;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.ON_THE_WAY;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.PICKED_UP;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.PREPARING;
import static io.grpc.Metadata.BINARY_BYTE_MARSHALLER;
import static io.grpc.Status.Code.INVALID_ARGUMENT;
import static io.grpc.Status.Code.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

class OrderGrpcServiceTest {

    private static final String BLANK_VALUE = "";
    private static final String SECOND_PRODUCT_ID = "product-2";
    private static final String UNKNOWN_ORDER_ID = "missing-order";
    private static final int UNKNOWN_DRIVER_COMMAND_VALUE = 999;
    private static final int VALID_QUOTE_QUANTITY = 1;

    private static final int FIRST_QUOTE_QUANTITY = 2;
    private static final double FIRST_QUOTE_WEIGHT = 2.5;
    private static final int SECOND_QUOTE_QUANTITY = 4;
    private static final double SECOND_QUOTE_WEIGHT = 1.25;
    private static final double DELIVERY_PRICE = 17.5;
    private static final int ESTIMATED_DELIVERY_MINUTES = 40;
    private static final int NON_POSITIVE_QUANTITY = 0;
    private static final double WEIGHT_BELOW_MINIMUM = 0.0;
    private static final double WEIGHT_ABOVE_MAXIMUM = 100.1;
    private static final double VALID_QUOTE_WEIGHT = 1.0;
    private static final double NON_FINITE_WEIGHT = Double.NaN;

    private static final String ORDER_ID_REQUIRED_MESSAGE = "order_id is required";
    private static final String ORDER_NOT_FOUND_MESSAGE_PREFIX = "Order not found: ";
    private static final String ITEM_WEIGHT_RANGE_MESSAGE = "Item weight should be between 0 and 100";

    private static final Metadata.Key<byte[]> ERROR_NAME_TRAILER =
        Metadata.Key.of("error_name-bin", BINARY_BYTE_MARSHALLER);

    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final OrderGrpcService classUnderTest = new OrderGrpcService(orderRepository);

    @Test
    void givenValidRequest_whenCreatingOrder_thenResponseIsSentAndOrderIsStored() {
        TestStreamObserver<CreateOrderGrpcResponse> responseObserver = new TestStreamObserver<>();

        classUnderTest.createOrder(prepareCreateOrderRequest(CUSTOMER_NAME, ADDRESS, List.of(ORDER_ITEM)), responseObserver);

        assertThat(responseObserver.values()).singleElement().satisfies(response -> {
            assertThat(response.getOrderId()).isNotBlank();
            assertThat(response.getCustomerName()).isEqualTo(CUSTOMER_NAME);
            assertThat(response.getAddress()).isEqualTo(ADDRESS);
            assertThat(response.getStatus()).isEqualTo(CREATED);
            assertThat(response.getItemsList()).containsExactly(ORDER_ITEM);
            assertThat(orderRepository.findById(response.getOrderId())).isPresent();
        });
        assertCompletedOnce(responseObserver);
    }

    @ParameterizedTest
    @MethodSource("invalidCreateRequestsProvider")
    void givenInvalidRequest_whenCreatingOrder_thenInvalidOrderErrorIsSent(CreateOrderGrpcRequest request, String expectedMessage) {
        TestStreamObserver<CreateOrderGrpcResponse> responseObserver = new TestStreamObserver<>();

        classUnderTest.createOrder(request, responseObserver);

        assertGrpcError(responseObserver, INVALID_ARGUMENT, INVALID_ORDER, expectedMessage);
    }

    private static Stream<Arguments> invalidCreateRequestsProvider() {
        return Stream.of(
            Arguments.of(prepareCreateOrderRequest(BLANK_VALUE, ADDRESS, List.of(ORDER_ITEM)), "Customer name is required"),
            Arguments.of(prepareCreateOrderRequest(CUSTOMER_NAME, BLANK_VALUE, List.of(ORDER_ITEM)), "Address is required"),
            Arguments.of(prepareCreateOrderRequest(CUSTOMER_NAME, ADDRESS, List.of()), "At least one order item is required")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidGetOrderRequestsProvider")
    void givenInvalidRequest_whenGettingOrder_thenMappedGrpcErrorIsSent(
        GetOrderGrpcRequest request,
        Status.Code expectedStatus,
        OrderGrpcError expectedError,
        String expectedMessage
    ) {
        TestStreamObserver<GetOrderGrpcResponse> responseObserver = new TestStreamObserver<>();

        classUnderTest.getOrder(request, responseObserver);

        assertGrpcError(responseObserver, expectedStatus, expectedError, expectedMessage);
    }

    private static Stream<Arguments> invalidGetOrderRequestsProvider() {
        return Stream.of(
            Arguments.of(
                GetOrderGrpcRequest.getDefaultInstance(),
                INVALID_ARGUMENT,
                INVALID_ORDER,
                ORDER_ID_REQUIRED_MESSAGE
            ),
            Arguments.of(
                GetOrderGrpcRequest.newBuilder().setOrderId(UNKNOWN_ORDER_ID).build(),
                NOT_FOUND,
                ORDER_NOT_FOUND,
                ORDER_NOT_FOUND_MESSAGE_PREFIX + UNKNOWN_ORDER_ID
            )
        );
    }

    @Test
    void givenExistingOrder_whenGettingOrder_thenStoredOrderIsReturned() {
        StoredOrder storedOrder = orderRepository.createOrder(CUSTOMER_NAME, ADDRESS, List.of(ORDER_ITEM));
        TestStreamObserver<GetOrderGrpcResponse> responseObserver = new TestStreamObserver<>();

        classUnderTest.getOrder(GetOrderGrpcRequest.newBuilder().setOrderId(storedOrder.orderId()).build(), responseObserver);

        assertThat(responseObserver.values()).containsExactly(GetOrderGrpcResponse.newBuilder()
            .setOrderId(storedOrder.orderId())
            .setCustomerName(CUSTOMER_NAME)
            .setAddress(ADDRESS)
            .setStatus(CREATED)
            .addItems(ORDER_ITEM)
            .build());
        assertCompletedOnce(responseObserver);
    }

    @Test
    void givenStoredOrders_whenListingOrders_thenAllOrdersAreReturned() {
        StoredOrder firstOrder = orderRepository.createOrder(CUSTOMER_NAME, ADDRESS, List.of(ORDER_ITEM));
        StoredOrder secondOrder = orderRepository.createOrder(SECOND_CUSTOMER_NAME, SECOND_ADDRESS, List.of(ORDER_ITEM));
        TestStreamObserver<GetOrderGrpcResponseList> responseObserver = new TestStreamObserver<>();

        classUnderTest.listOrders(Empty.getDefaultInstance(), responseObserver);

        assertThat(responseObserver.values()).singleElement().satisfies(response ->
            assertThat(response.getOrdersList())
                .extracting(GetOrderGrpcResponse::getOrderId)
                .containsExactlyInAnyOrder(firstOrder.orderId(), secondOrder.orderId())
        );
        assertCompletedOnce(responseObserver);
    }

    @Test
    void givenExistingOrder_whenTrackingOrder_thenTrackingEventsAreStreamedInOrder() {
        StoredOrder storedOrder = orderRepository.createOrder(CUSTOMER_NAME, ADDRESS, List.of(ORDER_ITEM));
        TestStreamObserver<TrackingEventsGrpcResponse> responseObserver = new TestStreamObserver<>();

        classUnderTest.trackOrder(TrackOrderGrpcRequest.newBuilder().setOrderId(storedOrder.orderId()).build(), responseObserver);

        assertThat(responseObserver.values())
            .extracting(TrackingEventsGrpcResponse::getStatus)
            .containsExactly(CREATED, PREPARING, ON_THE_WAY);
        assertThat(responseObserver.values())
            .extracting(TrackingEventsGrpcResponse::getMessage)
            .containsExactly("Order received", "Order is being prepared", "Order is on the way");
        assertThat(responseObserver.values())
            .allSatisfy(event -> {
                assertThat(event.getOrderId()).isEqualTo(storedOrder.orderId());
                assertThat(event.getTimestamp()).isPositive();
            });
        assertCompletedOnce(responseObserver);
    }

    @ParameterizedTest
    @MethodSource("invalidTrackingRequestsProvider")
    void givenInvalidRequest_whenTrackingOrder_thenMappedGrpcErrorIsSent(
        TrackOrderGrpcRequest request,
        Status.Code expectedStatus,
        OrderGrpcError expectedError,
        String expectedMessage
    ) {
        TestStreamObserver<TrackingEventsGrpcResponse> responseObserver = new TestStreamObserver<>();

        classUnderTest.trackOrder(request, responseObserver);

        assertGrpcError(responseObserver, expectedStatus, expectedError, expectedMessage);
    }

    private static Stream<Arguments> invalidTrackingRequestsProvider() {
        return Stream.of(
            Arguments.of(
                TrackOrderGrpcRequest.getDefaultInstance(),
                INVALID_ARGUMENT,
                INVALID_ORDER,
                ORDER_ID_REQUIRED_MESSAGE
            ),
            Arguments.of(
                TrackOrderGrpcRequest.newBuilder().setOrderId(UNKNOWN_ORDER_ID).build(),
                NOT_FOUND,
                ORDER_NOT_FOUND,
                ORDER_NOT_FOUND_MESSAGE_PREFIX + UNKNOWN_ORDER_ID
            )
        );
    }

    @Test
    void givenValidQuoteItems_whenCalculatingQuote_thenTotalPriceAndDurationAreReturned() {
        TestStreamObserver<QuoteGrpcResponse> responseObserver = new TestStreamObserver<>();
        StreamObserver<QuoteItemGrpcRequest> requestObserver = classUnderTest.calculateQuote(responseObserver);

        requestObserver.onNext(prepareQuoteItemRequest(PRODUCT_ID, FIRST_QUOTE_QUANTITY, FIRST_QUOTE_WEIGHT));
        requestObserver.onNext(prepareQuoteItemRequest(SECOND_PRODUCT_ID, SECOND_QUOTE_QUANTITY, SECOND_QUOTE_WEIGHT));
        requestObserver.onCompleted();

        assertThat(responseObserver.values()).containsExactly(QuoteGrpcResponse.newBuilder()
            .setDeliveryPrice(DELIVERY_PRICE)
            .setEstimatedDeliveryTimeMinutes(ESTIMATED_DELIVERY_MINUTES)
            .build());
        assertCompletedOnce(responseObserver);
    }

    @ParameterizedTest
    @MethodSource("invalidQuoteRequestsProvider")
    void givenInvalidQuoteInput_whenCalculatingQuote_thenValidationErrorIsSent(
        List<QuoteItemGrpcRequest> requests,
        OrderGrpcError expectedError,
        String expectedMessage
    ) {
        TestStreamObserver<QuoteGrpcResponse> responseObserver = new TestStreamObserver<>();
        StreamObserver<QuoteItemGrpcRequest> requestObserver = classUnderTest.calculateQuote(responseObserver);
        requests.forEach(requestObserver::onNext);

        requestObserver.onCompleted();

        assertGrpcError(responseObserver, INVALID_ARGUMENT, expectedError, expectedMessage);
    }

    private static Stream<Arguments> invalidQuoteRequestsProvider() {
        return Stream.of(
            Arguments.of(List.<QuoteItemGrpcRequest>of(), OrderGrpcError.INVALID_QUOTE, "At least one quote item is required"),
            Arguments.of(
                List.of(prepareQuoteItemRequest(BLANK_VALUE, VALID_QUOTE_QUANTITY, VALID_QUOTE_WEIGHT)),
                OrderGrpcError.INVALID_ITEM_PRODUCT_ID,
                "product id is required"
            ),
            Arguments.of(
                List.of(prepareQuoteItemRequest(PRODUCT_ID, NON_POSITIVE_QUANTITY, VALID_QUOTE_WEIGHT)),
                OrderGrpcError.INVALID_ITEM_QUANTITY,
                "Item quantity should be positive number"
            ),
            Arguments.of(
                List.of(prepareQuoteItemRequest(PRODUCT_ID, VALID_QUOTE_QUANTITY, WEIGHT_BELOW_MINIMUM)),
                OrderGrpcError.INVALID_ITEM_WEIGHT,
                ITEM_WEIGHT_RANGE_MESSAGE
            ),
            Arguments.of(
                List.of(prepareQuoteItemRequest(PRODUCT_ID, VALID_QUOTE_QUANTITY, WEIGHT_ABOVE_MAXIMUM)),
                OrderGrpcError.INVALID_ITEM_WEIGHT,
                ITEM_WEIGHT_RANGE_MESSAGE
            ),
            Arguments.of(
                List.of(prepareQuoteItemRequest(PRODUCT_ID, VALID_QUOTE_QUANTITY, NON_FINITE_WEIGHT)),
                OrderGrpcError.INVALID_ITEM_WEIGHT,
                ITEM_WEIGHT_RANGE_MESSAGE
            )
        );
    }

    @Test
    void givenValidDriverCommands_whenLiveTracking_thenOneEventPerCommandIsStreamed() {
        StoredOrder storedOrder = orderRepository.createOrder(CUSTOMER_NAME, ADDRESS, List.of(ORDER_ITEM));
        TestStreamObserver<TrackingEventsGrpcResponse> responseObserver = new TestStreamObserver<>();
        StreamObserver<DriverCommandGrpcRequest> requestObserver = classUnderTest.liveTracking(responseObserver);

        requestObserver.onNext(prepareDriverCommandRequest(storedOrder.orderId(), DriverCommandType.ACCEPT_ORDER));
        requestObserver.onNext(prepareDriverCommandRequest(storedOrder.orderId(), DriverCommandType.PICK_UP_ORDER));
        requestObserver.onNext(prepareDriverCommandRequest(storedOrder.orderId(), DriverCommandType.START_DELIVERY));
        requestObserver.onNext(prepareDriverCommandRequest(storedOrder.orderId(), DriverCommandType.COMPLETE_DELIVERY));
        requestObserver.onCompleted();

        assertThat(responseObserver.values())
            .extracting(TrackingEventsGrpcResponse::getStatus)
            .containsExactly(PREPARING, PICKED_UP, ON_THE_WAY, DELIVERED);
        assertThat(responseObserver.values())
            .extracting(TrackingEventsGrpcResponse::getMessage)
            .containsExactly("Driver accepted order", "Order picked up", "Delivery started", "Delivery completed");
        assertThat(responseObserver.values())
            .allSatisfy(event -> {
                assertThat(event.getOrderId()).isEqualTo(storedOrder.orderId());
                assertThat(event.getTimestamp()).isPositive();
            });
        assertCompletedOnce(responseObserver);
    }

    @ParameterizedTest
    @MethodSource("invalidDriverOrderIdsProvider")
    void givenInvalidOrderId_whenLiveTracking_thenMappedGrpcErrorIsSent(
        String orderId,
        Status.Code expectedStatus,
        OrderGrpcError expectedError,
        String expectedMessage
    ) {
        TestStreamObserver<TrackingEventsGrpcResponse> responseObserver = new TestStreamObserver<>();
        StreamObserver<DriverCommandGrpcRequest> requestObserver = classUnderTest.liveTracking(responseObserver);

        requestObserver.onNext(prepareDriverCommandRequest(orderId, DriverCommandType.ACCEPT_ORDER));
        requestObserver.onCompleted();

        assertGrpcError(responseObserver, expectedStatus, expectedError, expectedMessage);
    }

    private static Stream<Arguments> invalidDriverOrderIdsProvider() {
        return Stream.of(
            Arguments.of(BLANK_VALUE, INVALID_ARGUMENT, INVALID_ORDER, ORDER_ID_REQUIRED_MESSAGE),
            Arguments.of(UNKNOWN_ORDER_ID, NOT_FOUND, ORDER_NOT_FOUND, ORDER_NOT_FOUND_MESSAGE_PREFIX + UNKNOWN_ORDER_ID)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDriverCommandValuesProvider")
    void givenInvalidDriverCommandAndCompletedRequestStream_whenLiveTracking_thenOnlyErrorIsSent(int commandValue) {
        StoredOrder storedOrder = orderRepository.createOrder(CUSTOMER_NAME, ADDRESS, List.of(ORDER_ITEM));
        TestStreamObserver<TrackingEventsGrpcResponse> responseObserver = new TestStreamObserver<>();
        StreamObserver<DriverCommandGrpcRequest> requestObserver = classUnderTest.liveTracking(responseObserver);
        DriverCommandGrpcRequest invalidCommand = DriverCommandGrpcRequest.newBuilder()
            .setOrderId(storedOrder.orderId())
            .setCommandValue(commandValue)
            .build();

        requestObserver.onNext(invalidCommand);
        requestObserver.onNext(prepareDriverCommandRequest(storedOrder.orderId(), DriverCommandType.ACCEPT_ORDER));
        requestObserver.onCompleted();

        assertGrpcError(
            responseObserver,
            INVALID_ARGUMENT,
            INVALID_DRIVER_COMMAND,
            "A valid driver command is required"
        );
    }

    @Test
    void givenRequestStreamErrorAndLaterCompletion_whenLiveTracking_thenResponseRemainsTerminal() {
        TestStreamObserver<TrackingEventsGrpcResponse> responseObserver = new TestStreamObserver<>();
        StreamObserver<DriverCommandGrpcRequest> requestObserver = classUnderTest.liveTracking(responseObserver);

        requestObserver.onError(new IllegalStateException("client request stream failed"));
        requestObserver.onCompleted();

        assertThat(responseObserver.signals()).isEmpty();
    }

    private static Stream<Integer> invalidDriverCommandValuesProvider() {
        return Stream.of(DRIVER_COMMAND_TYPE_UNSPECIFIED_VALUE, UNKNOWN_DRIVER_COMMAND_VALUE);
    }

    private static CreateOrderGrpcRequest prepareCreateOrderRequest(String customerName, String address, List<OrderItemGrpc> items) {
        return CreateOrderGrpcRequest.newBuilder()
            .setCustomerName(customerName)
            .setAddress(address)
            .addAllItems(items)
            .build();
    }

    private static QuoteItemGrpcRequest prepareQuoteItemRequest(String productId, int quantity, double weight) {
        return QuoteItemGrpcRequest.newBuilder()
            .setProductId(productId)
            .setQuantity(quantity)
            .setWeight(weight)
            .build();
    }

    private static DriverCommandGrpcRequest prepareDriverCommandRequest(String orderId, DriverCommandType command) {
        return DriverCommandGrpcRequest.newBuilder()
            .setOrderId(orderId)
            .setCommand(command)
            .build();
    }

    private static void assertCompletedOnce(TestStreamObserver<?> responseObserver) {
        assertThat(responseObserver.errors()).isEmpty();
        assertThat(responseObserver.completionCount()).isEqualTo(1);
        assertThat(responseObserver.signals()).endsWith(ON_COMPLETED);
    }

    private static void assertGrpcError(
        TestStreamObserver<?> responseObserver,
        Status.Code expectedStatus,
        OrderGrpcError expectedError,
        String expectedDescription
    ) {
        assertThat(responseObserver.values()).isEmpty();
        assertThat(responseObserver.errors()).hasSize(1);
        assertThat(responseObserver.completionCount()).isZero();
        assertThat(responseObserver.signals()).containsExactly(ON_ERROR);

        assertThat(responseObserver.errors().getFirst()).isInstanceOfSatisfying(StatusRuntimeException.class, grpcException -> {
            assertThat(grpcException.getStatus().getCode()).isEqualTo(expectedStatus);
            assertThat(grpcException.getStatus().getDescription()).isEqualTo(expectedDescription);
            assertThat(grpcException.getTrailers()).isNotNull();
            assertThat(grpcException.getTrailers().get(ERROR_NAME_TRAILER))
                .containsExactly(expectedError.name().getBytes(StandardCharsets.UTF_8));
        });
    }
}
