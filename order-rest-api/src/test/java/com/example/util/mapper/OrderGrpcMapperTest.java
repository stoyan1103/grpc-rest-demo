package com.example.util.mapper;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.DriverCommandGrpcRequest;
import com.example.grpcdemo.contract.order.v1.DriverCommandType;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.OrderStatus;
import com.example.grpcdemo.contract.order.v1.QuoteGrpcResponse;
import com.example.grpcdemo.contract.order.v1.QuoteItemGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackingEventsGrpcResponse;
import com.example.grpcdemo.rest.generated.model.CreateOrderRequest;
import com.example.grpcdemo.rest.generated.model.GetOrderResponse;
import com.example.grpcdemo.rest.generated.model.LiveTrackingCommandRequest;
import com.example.grpcdemo.rest.generated.model.OrderItem;
import com.example.grpcdemo.rest.generated.model.QuoteItemRequest;
import com.example.grpcdemo.rest.generated.model.QuoteResponse;
import com.example.grpcdemo.rest.generated.model.TrackingEventResponse;

import static com.example.grpc.testing.OrderGrpcTestData.ADDRESS;
import static com.example.grpc.testing.OrderGrpcTestData.CUSTOMER_NAME;
import static com.example.grpc.testing.OrderGrpcTestData.ITEM_PRICE;
import static com.example.grpc.testing.OrderGrpcTestData.ITEM_QUANTITY;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ID;
import static com.example.grpc.testing.OrderGrpcTestData.ORDER_ITEM;
import static com.example.grpc.testing.OrderGrpcTestData.PRODUCT_ID;
import static com.example.grpc.testing.OrderGrpcTestData.PRODUCT_NAME;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.CANCELLED;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.CREATED;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.DELIVERED;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.ON_THE_WAY;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.PICKED_UP;
import static com.example.grpcdemo.contract.order.v1.OrderStatus.PREPARING;
import static org.assertj.core.api.Assertions.assertThat;

class OrderGrpcMapperTest {

    private static final OrderItem REST_ITEM = new OrderItem(PRODUCT_ID, PRODUCT_NAME, ITEM_QUANTITY, ITEM_PRICE);

    @Test
    void givenRestCreateRequest_whenToGrpc_thenAllFieldsAndItemsAreMapped() {
        CreateOrderRequest request = new CreateOrderRequest(CUSTOMER_NAME, ADDRESS, List.of(REST_ITEM));

        assertThat(OrderGrpcMapper.toGrpc(request)).isEqualTo(CreateOrderGrpcRequest.newBuilder()
            .setCustomerName(CUSTOMER_NAME)
            .setAddress(ADDRESS)
            .addItems(ORDER_ITEM)
            .build());
    }

    @ParameterizedTest
    @MethodSource("orderStatusProvider")
    void givenGrpcStatus_whenToDomain_thenStatusIsMapped(OrderStatus grpcStatus, String expectedStatus) {
        CreateOrderGrpcResponse response = CreateOrderGrpcResponse.newBuilder()
            .setOrderId(ORDER_ID)
            .setStatus(grpcStatus)
            .build();

        assertThat(OrderGrpcMapper.toDomain(response).getStatus()).isEqualTo(expectedStatus);
    }

    private static Stream<Arguments> orderStatusProvider() {
        return Stream.of(
            Arguments.of(CREATED, "CREATED"),
            Arguments.of(PREPARING, "PREPARING"),
            Arguments.of(PICKED_UP, "PICKED_UP"),
            Arguments.of(ON_THE_WAY, "ON_THE_WAY"),
            Arguments.of(DELIVERED, "DELIVERED"),
            Arguments.of(CANCELLED, "CANCELLED")
        );
    }

    @Test
    void givenUnknownGrpcStatus_whenToDomain_thenUnknownStatusIsReturned() {
        GetOrderGrpcResponse response = GetOrderGrpcResponse.newBuilder()
            .setOrderId(ORDER_ID)
            .setStatusValue(999)
            .build();

        assertThat(OrderGrpcMapper.toDomain(response).getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void givenGrpcOrderWithItems_whenToDomain_thenAllFieldsAndItemsAreMapped() {
        GetOrderGrpcResponse response = GetOrderGrpcResponse.newBuilder()
            .setOrderId(ORDER_ID)
            .setCustomerName(CUSTOMER_NAME)
            .setAddress(ADDRESS)
            .setStatus(PREPARING)
            .addItems(ORDER_ITEM)
            .build();

        assertThat(OrderGrpcMapper.toDomain(response)).isEqualTo(new GetOrderResponse(
            ORDER_ID,
            CUSTOMER_NAME,
            ADDRESS,
            "PREPARING",
            List.of(REST_ITEM)
        ));
    }

    @Test
    void givenGrpcTrackingEvent_whenToDomain_thenFieldsAreMapped() {
        TrackingEventsGrpcResponse response = TrackingEventsGrpcResponse.newBuilder()
            .setOrderId(ORDER_ID)
            .setStatus(ON_THE_WAY)
            .setMessage("Driver is on the way")
            .setTimestamp(1_750_000_000_123L)
            .build();

        assertThat(OrderGrpcMapper.toDomain(response)).isEqualTo(new TrackingEventResponse(
            ORDER_ID,
            TrackingEventResponse.StatusEnum.ON_THE_WAY,
            "Driver is on the way",
            1_750_000_000_123L
        ));
    }

    @Test
    void givenRestQuoteItem_whenToGrpc_thenAllFieldsAreMapped() {
        QuoteItemRequest request = new QuoteItemRequest(PRODUCT_ID, 3, 2.5);

        assertThat(OrderGrpcMapper.toGrpc(request)).isEqualTo(QuoteItemGrpcRequest.newBuilder()
            .setProductId(PRODUCT_ID)
            .setQuantity(3)
            .setWeight(2.5)
            .build());
    }

    @Test
    void givenGrpcQuote_whenToDomain_thenPriceAndMinutesAreMapped() {
        QuoteGrpcResponse response = QuoteGrpcResponse.newBuilder()
            .setDeliveryPrice(17.5)
            .setEstimatedDeliveryTimeMinutes(40)
            .build();

        assertThat(OrderGrpcMapper.toDomain(response)).isEqualTo(new QuoteResponse(17.5, 40));
    }

    @ParameterizedTest
    @MethodSource("driverCommandProvider")
    void givenRestDriverCommand_whenToGrpc_thenOrderAndCommandAreMapped(
        LiveTrackingCommandRequest.CommandEnum restCommand,
        DriverCommandType grpcCommand
    ) {
        DriverCommandGrpcRequest expected = DriverCommandGrpcRequest.newBuilder()
            .setOrderId(ORDER_ID)
            .setCommand(grpcCommand)
            .build();

        assertThat(OrderGrpcMapper.toGrpc(ORDER_ID, new LiveTrackingCommandRequest(restCommand))).isEqualTo(expected);
    }

    private static Stream<Arguments> driverCommandProvider() {
        return Stream.of(
            Arguments.of(LiveTrackingCommandRequest.CommandEnum.ACCEPT_ORDER, DriverCommandType.ACCEPT_ORDER),
            Arguments.of(LiveTrackingCommandRequest.CommandEnum.PICK_UP_ORDER, DriverCommandType.PICK_UP_ORDER),
            Arguments.of(LiveTrackingCommandRequest.CommandEnum.START_DELIVERY, DriverCommandType.START_DELIVERY),
            Arguments.of(LiveTrackingCommandRequest.CommandEnum.COMPLETE_DELIVERY, DriverCommandType.COMPLETE_DELIVERY)
        );
    }
}
