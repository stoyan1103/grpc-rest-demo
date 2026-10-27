package com.example.web;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.client.OrderGrpcClient;
import com.example.exception.InvalidDriverCommandException;
import com.example.exception.InvalidItemProductIdException;
import com.example.exception.InvalidItemQuantityException;
import com.example.exception.InvalidItemWeightException;
import com.example.exception.InvalidOrderException;
import com.example.exception.InvalidQuoteException;
import com.example.exception.OrderNotFoundException;
import com.example.grpcdemo.rest.generated.model.CreateOrderRequest;
import com.example.grpcdemo.rest.generated.model.CreateOrderResponse;
import com.example.grpcdemo.rest.generated.model.GetOrderResponse;
import com.example.grpcdemo.rest.generated.model.LiveTrackingCommandRequest;
import com.example.grpcdemo.rest.generated.model.OrderItem;
import com.example.grpcdemo.rest.generated.model.QuoteItemRequest;
import com.example.grpcdemo.rest.generated.model.QuoteResponse;
import com.example.grpcdemo.rest.generated.model.TrackingEventResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import static com.example.grpc.testing.OrderGrpcTestData.ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.CUSTOMER_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.ITEM_PRICE;
import static com.example.grpc.testing.OrderGrpcTestData.ITEM_QUANTITY;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ID;
import static com.example.grpc.testing.OrderGrpcTestData.PRODUCT_ID;
import static com.example.grpc.testing.OrderGrpcTestData.PRODUCT_NAME;
import static com.example.grpcdemo.rest.generated.model.LiveTrackingCommandRequest.CommandEnum.ACCEPT_ORDER;
import static com.example.grpcdemo.rest.generated.model.TrackingEventResponse.StatusEnum.PREPARING;
import static io.grpc.Status.Code.ALREADY_EXISTS;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static io.grpc.Status.Code.INVALID_ARGUMENT;
import static io.grpc.Status.Code.NOT_FOUND;
import static io.grpc.Status.Code.PERMISSION_DENIED;
import static io.grpc.Status.Code.UNAUTHENTICATED;
import static io.grpc.Status.Code.UNAVAILABLE;
import static io.grpc.Status.Code.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrdersController.class)
class OrdersControllerWebMvcTest {

    private static final String ORDER_NOT_FOUND_MESSAGE = "Order not found: " + ORDER_ID;
    private static final String INVALID_REQUEST_BODY_MESSAGE =
        "Request body is malformed or contains an unsupported value";
    private static final String CREATED_STATUS = "CREATED";
    private static final OrderItem ITEM = new OrderItem(PRODUCT_ID, PRODUCT_NAME, ITEM_QUANTITY, ITEM_PRICE);
    private static final CreateOrderRequest CREATE_REQUEST = new CreateOrderRequest(CUSTOMER_NAME, ADDRESS, List.of(ITEM));
    private static final CreateOrderResponse CREATE_RESPONSE = new CreateOrderResponse(ORDER_ID, CREATED_STATUS);
    private static final GetOrderResponse GET_ORDER_RESPONSE = new GetOrderResponse(
        ORDER_ID,
        CUSTOMER_NAME,
        ADDRESS,
        CREATED_STATUS,
        List.of(ITEM)
    );
    private static final TrackingEventResponse TRACKING_EVENT = new TrackingEventResponse(
        ORDER_ID,
        PREPARING,
        "Order is being prepared",
        1_750_000_000_123L
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderGrpcClient orderGrpcClient;

    @Test
    void givenValidRequest_whenCreateOrder_thenCreatedResponseIsReturned() throws Exception {
        when(orderGrpcClient.createOrder(any(CreateOrderRequest.class))).thenReturn(CREATE_RESPONSE);

        mockMvc.perform(post("/orders")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(CREATE_REQUEST)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(ORDER_ID))
            .andExpect(jsonPath("$.status").value(CREATED_STATUS));

        verify(orderGrpcClient).createOrder(CREATE_REQUEST);
    }

    @ParameterizedTest
    @MethodSource("invalidRequestProvider")
    void givenInvalidRequest_whenCallingOrdersApi_thenBadRequestIsReturnedAndGrpcIsNotCalled(
        String uri,
        String body,
        String expectedField
    ) throws Exception {
        mockMvc.perform(post(uri)
                .contentType(APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains(expectedField));

        verifyNoInteractions(orderGrpcClient);
    }

    private static Stream<Arguments> invalidRequestProvider() {
        return Stream.of(
            Arguments.of("/orders", "{}", "customerName"),
            Arguments.of("/orders", """
                {"customerName":"Valid Name","address":"Sofia, Bulgaria","items":[]}
                """, "items"),
            Arguments.of("/orders", """
                {"customerName":"Valid Name","address":"Sofia, Bulgaria","items":[{}]}
                """, "productId"),
            Arguments.of("/orders/quote", "[]", "quoteItemRequest"),
            Arguments.of("/orders/quote", "[{}]", "productId"),
            Arguments.of("/orders/" + ORDER_ID + "/live-tracking", "[]", "liveTrackingCommandRequest"),
            Arguments.of("/orders/" + ORDER_ID + "/live-tracking", "[{}]", "command")
        );
    }

    @Test
    void givenUnknownLiveTrackingCommand_whenLiveTrackOrder_thenBadRequestIsReturnedAndGrpcIsNotCalled()
        throws Exception {
        mockMvc.perform(post("/orders/{orderId}/live-tracking", ORDER_ID)
                .contentType(APPLICATION_JSON)
                .content("""
                    [{"command":"NOT_A_COMMAND"}]
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value(INVALID_REQUEST_BODY_MESSAGE));

        verifyNoInteractions(orderGrpcClient);
    }

    @Test
    void givenOrderId_whenGetOrder_thenOrderResponseIsReturned() throws Exception {
        when(orderGrpcClient.getOrder(ORDER_ID)).thenReturn(GET_ORDER_RESPONSE);

        mockMvc.perform(get("/orders/{orderId}", ORDER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(ORDER_ID))
            .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID));

        verify(orderGrpcClient).getOrder(ORDER_ID);
    }

    @Test
    void whenListOrders_thenOrderResponsesAreReturned() throws Exception {
        when(orderGrpcClient.getOrders()).thenReturn(List.of(GET_ORDER_RESPONSE));

        mockMvc.perform(get("/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderId").value(ORDER_ID));

        verify(orderGrpcClient).getOrders();
    }

    @Test
    void givenOrderId_whenGetTrackingEvents_thenTrackingEventsAreReturned() throws Exception {
        when(orderGrpcClient.getTrackingEvents(ORDER_ID)).thenReturn(List.of(TRACKING_EVENT));

        mockMvc.perform(get("/orders/{orderId}/tracking", ORDER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderId").value(ORDER_ID))
            .andExpect(jsonPath("$[0].timestamp").value(1_750_000_000_123L));

        verify(orderGrpcClient).getTrackingEvents(ORDER_ID);
    }

    @Test
    void givenDriverCommands_whenLiveTrackOrder_thenTrackingEventsAreReturned() throws Exception {
        List<LiveTrackingCommandRequest> commands = List.of(
            new LiveTrackingCommandRequest(ACCEPT_ORDER)
        );
        when(orderGrpcClient.liveTrackOrder(ORDER_ID, commands)).thenReturn(List.of(TRACKING_EVENT));

        mockMvc.perform(post("/orders/{orderId}/live-tracking", ORDER_ID)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(commands)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("PREPARING"));

        verify(orderGrpcClient).liveTrackOrder(ORDER_ID, commands);
    }

    @Test
    void givenQuoteItems_whenCalculateQuote_thenQuoteResponseIsReturned() throws Exception {
        List<QuoteItemRequest> requests = List.of(new QuoteItemRequest(PRODUCT_ID, 2, 1.5));
        when(orderGrpcClient.calculateQuote(requests)).thenReturn(new QuoteResponse(17.5, 40));

        mockMvc.perform(post("/orders/quote")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(requests)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryPrice").value(17.5))
            .andExpect(jsonPath("$.estimatedDeliveryTimeMinutes").value(40));

        verify(orderGrpcClient).calculateQuote(requests);
    }

    @ParameterizedTest
    @MethodSource("orderExceptionProvider")
    void givenOrderException_whenGettingOrder_thenSpecificHttpErrorIsReturned(
        RuntimeException exception,
        int expectedStatus
    ) throws Exception {
        when(orderGrpcClient.getOrder(ORDER_ID)).thenThrow(exception);

        mockMvc.perform(get("/orders/{orderId}", ORDER_ID))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.code").value(expectedStatus))
            .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    private static Stream<Arguments> orderExceptionProvider() {
        return Stream.of(
            Arguments.of(new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE), 404),
            Arguments.of(new InvalidOrderException("Invalid order"), 400),
            Arguments.of(new InvalidItemProductIdException("Invalid product ID"), 400),
            Arguments.of(new InvalidItemQuantityException("Invalid quantity"), 400),
            Arguments.of(new InvalidItemWeightException("Invalid item weight"), 400),
            Arguments.of(new InvalidQuoteException("Invalid quote"), 400),
            Arguments.of(new InvalidDriverCommandException("Invalid driver command"), 400)
        );
    }

    @ParameterizedTest
    @MethodSource("grpcStatusCases")
    void givenGrpcStatusException_whenListingOrders_thenSafeHttpErrorIsReturned(
        Status.Code grpcCode,
        int expectedStatus,
        String expectedMessage
    ) throws Exception {
        StatusRuntimeException grpcException = Status.fromCode(grpcCode)
            .withDescription("internal gRPC detail")
            .asRuntimeException();
        when(orderGrpcClient.getOrders()).thenThrow(grpcException);

        mockMvc.perform(get("/orders"))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.code").value(expectedStatus))
            .andExpect(jsonPath("$.message").value(expectedMessage))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .doesNotContain("internal gRPC detail"));
    }

    private static Stream<Arguments> grpcStatusCases() {
        return Stream.of(
            Arguments.of(INVALID_ARGUMENT, 400, "Invalid order request"),
            Arguments.of(NOT_FOUND, 404, "Order not found"),
            Arguments.of(ALREADY_EXISTS, 409, "Order already exists"),
            Arguments.of(PERMISSION_DENIED, 403, "Permission denied"),
            Arguments.of(UNAUTHENTICATED, 401, "Authentication required"),
            Arguments.of(UNAVAILABLE, 503, "Order service unavailable"),
            Arguments.of(DEADLINE_EXCEEDED, 504, "Order service timed out"),
            Arguments.of(UNKNOWN, 500, "Internal Server Error")
        );
    }

    @Test
    void givenUnhandledRuntimeException_whenListingOrders_thenInternalDetailsAreNotExposed() throws Exception {
        when(orderGrpcClient.getOrders()).thenThrow(new RuntimeException("secret implementation detail"));

        mockMvc.perform(get("/orders"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Internal Server Error"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .doesNotContain("secret implementation detail"));

        verify(orderGrpcClient).getOrders();
    }

    @Test
    void givenIllegalStateException_whenListingOrders_thenInternalDetailsAreNotExposed() throws Exception {
        when(orderGrpcClient.getOrders()).thenThrow(new IllegalStateException("internal service state"));

        mockMvc.perform(get("/orders"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.message").value("Internal Server Error"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .doesNotContain("internal service state"));

        verify(orderGrpcClient).getOrders();
    }
}
