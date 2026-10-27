package com.example.client;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.example.exception.InvalidQuoteException;
import com.example.exception.OrderNotFoundException;
import com.example.grpc.mapper.GrpcExceptionMapper;
import com.example.grpc.testing.TestInProcessGrpcServerExtension;
import com.example.grpc.testing.TestOrderGrpcService;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.DriverCommandGrpcRequest;
import com.example.grpcdemo.contract.order.v1.DriverCommandType;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.OrderServiceGrpc;
import com.example.grpcdemo.contract.order.v1.QuoteItemGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackOrderGrpcRequest;
import com.example.grpcdemo.rest.generated.model.CreateOrderRequest;
import com.example.grpcdemo.rest.generated.model.CreateOrderResponse;
import com.example.grpcdemo.rest.generated.model.GetOrderResponse;
import com.example.grpcdemo.rest.generated.model.LiveTrackingCommandRequest;
import com.example.grpcdemo.rest.generated.model.OrderItem;
import com.example.grpcdemo.rest.generated.model.QuoteItemRequest;
import com.example.grpcdemo.rest.generated.model.QuoteResponse;
import com.example.grpcdemo.rest.generated.model.TrackingEventResponse;
import com.google.protobuf.Empty;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import static com.example.grpc.testing.OrderGrpcTestData.ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.CUSTOMER_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.ITEM_PRICE;
import static com.example.grpc.testing.OrderGrpcTestData.ITEM_QUANTITY;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ID;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ITEM;
import static com.example.grpc.testing.OrderGrpcTestData.PRODUCT_ID;
import static com.example.grpc.testing.OrderGrpcTestData.PRODUCT_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_CUSTOMER_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_ORDER_ID;
import static com.example.grpc.testing.OrderGrpcTestData.SECOND_PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderGrpcClientTest {

    private static final OrderItem ITEM = new OrderItem(PRODUCT_ID, PRODUCT_NAME, ITEM_QUANTITY, ITEM_PRICE);

    private final TestOrderGrpcService testService = new TestOrderGrpcService();

    @RegisterExtension
    private final TestInProcessGrpcServerExtension grpcServer = new TestInProcessGrpcServerExtension(testService);

    private OrderGrpcClient classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest = prepareClassUnderTest();
    }

    @Test
    void givenCreateOrderRequest_whenCallGrpc_thenRequestAndResponseAreMapped() {
        CreateOrderRequest request = new CreateOrderRequest(CUSTOMER_NAME, ADDRESS, List.of(ITEM));

        CreateOrderResponse response = classUnderTest.createOrder(request);

        assertThat(testService.createRequests()).containsExactly(CreateOrderGrpcRequest.newBuilder()
            .setCustomerName(CUSTOMER_NAME)
            .setAddress(ADDRESS)
            .addItems(ORDER_ITEM)
            .build());
        assertThat(response).isEqualTo(new CreateOrderResponse(ORDER_ID, "CREATED"));
    }

    @Test
    void givenOrderId_whenGetOrder_thenRequestAndResponseAreMapped() {
        GetOrderResponse response = classUnderTest.getOrder(ORDER_ID);

        assertThat(testService.getRequests()).containsExactly(GetOrderGrpcRequest.newBuilder().setOrderId(ORDER_ID).build());
        assertThat(response).isEqualTo(new GetOrderResponse(
            ORDER_ID,
            CUSTOMER_NAME,
            ADDRESS,
            "CREATED",
            List.of(ITEM)
        ));
    }

    @Test
    void givenUnknownOrderError_whenGetOrder_thenTypedApplicationExceptionIsRestored() {
        testService.failGetOrderWith(GrpcExceptionMapper.toGrpcException(
            new OrderNotFoundException("Order not found: " + ORDER_ID)
        ));

        assertThatThrownBy(() -> classUnderTest.getOrder(ORDER_ID))
            .isExactlyInstanceOf(OrderNotFoundException.class)
            .hasMessage("Order not found: " + ORDER_ID)
            .hasCauseInstanceOf(StatusRuntimeException.class);
        assertThat(testService.getRequests()).containsExactly(GetOrderGrpcRequest.newBuilder().setOrderId(ORDER_ID).build());
    }

    @Test
    void whenListOrders_thenGrpcOrdersAreMappedToRestResponses() {
        List<GetOrderResponse> responses = classUnderTest.getOrders();

        assertThat(testService.listRequests()).containsExactly(Empty.getDefaultInstance());
        assertThat(responses).containsExactly(
            new GetOrderResponse(ORDER_ID, CUSTOMER_NAME, ADDRESS, "CREATED", List.of(ITEM)),
            new GetOrderResponse(SECOND_ORDER_ID, SECOND_CUSTOMER_NAME, SECOND_ADDRESS, "PREPARING", List.of())
        );
    }

    @Test
    void givenOrderId_whenGetTrackingEvents_thenServerStreamIsMapped() {
        List<TrackingEventResponse> responses = classUnderTest.getTrackingEvents(ORDER_ID);

        assertThat(testService.trackRequests()).containsExactly(TrackOrderGrpcRequest.newBuilder().setOrderId(ORDER_ID).build());
        assertThat(responses).containsExactly(
            new TrackingEventResponse(ORDER_ID, TrackingEventResponse.StatusEnum.CREATED, "Order received", 1000L),
            new TrackingEventResponse(ORDER_ID, TrackingEventResponse.StatusEnum.PREPARING, "Being prepared", 2000L)
        );
    }

    @Test
    void givenQuoteItems_whenCalculateQuote_thenRequestsAndSingleResponseAreMapped() {
        List<QuoteItemRequest> requests = List.of(
            new QuoteItemRequest(PRODUCT_ID, 2, 1.5),
            new QuoteItemRequest(SECOND_PRODUCT_ID, 1, 3.0)
        );

        QuoteResponse response = classUnderTest.calculateQuote(requests);

        assertThat(testService.quoteRequests()).containsExactly(
            QuoteItemGrpcRequest.newBuilder().setProductId(PRODUCT_ID).setQuantity(2).setWeight(1.5).build(),
            QuoteItemGrpcRequest.newBuilder().setProductId(SECOND_PRODUCT_ID).setQuantity(1).setWeight(3.0).build()
        );
        assertThat(response).isEqualTo(new QuoteResponse(17.5, 40));
    }

    @Test
    void givenQuoteValidationError_whenCalculateQuote_thenTypedApplicationExceptionIsRestored() {
        testService.failQuoteWith(GrpcExceptionMapper.toGrpcException(
            new InvalidQuoteException("At least one quote item is required")
        ));

        assertThatThrownBy(() -> classUnderTest.calculateQuote(List.of()))
            .isExactlyInstanceOf(InvalidQuoteException.class)
            .hasMessage("At least one quote item is required")
            .hasCauseInstanceOf(StatusRuntimeException.class);

        assertThat(testService.quoteRequests()).isEmpty();
    }

    @Test
    void givenDriverCommands_whenLiveTracking_thenBidirectionalRequestsAndResponsesAreMapped() {
        List<LiveTrackingCommandRequest> commands = List.of(
            new LiveTrackingCommandRequest(LiveTrackingCommandRequest.CommandEnum.ACCEPT_ORDER),
            new LiveTrackingCommandRequest(LiveTrackingCommandRequest.CommandEnum.PICK_UP_ORDER)
        );

        List<TrackingEventResponse> responses = classUnderTest.liveTrackOrder(ORDER_ID, commands);

        assertThat(testService.driverCommands()).containsExactly(
            DriverCommandGrpcRequest.newBuilder().setOrderId(ORDER_ID).setCommand(DriverCommandType.ACCEPT_ORDER).build(),
            DriverCommandGrpcRequest.newBuilder().setOrderId(ORDER_ID).setCommand(DriverCommandType.PICK_UP_ORDER).build()
        );
        assertThat(responses).containsExactly(
            new TrackingEventResponse(ORDER_ID, TrackingEventResponse.StatusEnum.PREPARING, "accepted", 3000L),
            new TrackingEventResponse(ORDER_ID, TrackingEventResponse.StatusEnum.PICKED_UP, "picked up", 4000L)
        );
    }

    @Test
    void givenOrderNotFoundError_whenLiveTracking_thenTypedApplicationExceptionIsRestored() {
        testService.failLiveTrackingWith(GrpcExceptionMapper.toGrpcException(
            new OrderNotFoundException("Order not found: " + ORDER_ID)
        ));
        List<LiveTrackingCommandRequest> commands = List.of(
            new LiveTrackingCommandRequest(LiveTrackingCommandRequest.CommandEnum.ACCEPT_ORDER)
        );

        assertThatThrownBy(() -> classUnderTest.liveTrackOrder(ORDER_ID, commands))
            .isExactlyInstanceOf(OrderNotFoundException.class)
            .hasMessage("Order not found: " + ORDER_ID)
            .hasCauseInstanceOf(StatusRuntimeException.class);

        assertThat(testService.driverCommands()).containsExactly(
            DriverCommandGrpcRequest.newBuilder().setOrderId(ORDER_ID).setCommand(DriverCommandType.ACCEPT_ORDER).build()
        );
    }

    private OrderGrpcClient prepareClassUnderTest() {
        ManagedChannel channel = grpcServer.channel();
        return new OrderGrpcClient(
            OrderServiceGrpc.newBlockingStub(channel),
            OrderServiceGrpc.newStub(channel)
        );
    }
}
