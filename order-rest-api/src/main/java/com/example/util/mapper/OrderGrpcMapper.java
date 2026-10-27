package com.example.util.mapper;

import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcRequest;
import com.example.grpcdemo.contract.order.v1.CreateOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.DriverCommandGrpcRequest;
import com.example.grpcdemo.contract.order.v1.DriverCommandType;
import com.example.grpcdemo.contract.order.v1.GetOrderGrpcResponse;
import com.example.grpcdemo.contract.order.v1.OrderItemGrpc;
import com.example.grpcdemo.contract.order.v1.OrderStatus;
import com.example.grpcdemo.contract.order.v1.QuoteGrpcResponse;
import com.example.grpcdemo.contract.order.v1.QuoteItemGrpcRequest;
import com.example.grpcdemo.contract.order.v1.TrackingEventsGrpcResponse;
import com.example.grpcdemo.rest.generated.model.CreateOrderRequest;
import com.example.grpcdemo.rest.generated.model.CreateOrderResponse;
import com.example.grpcdemo.rest.generated.model.GetOrderResponse;
import com.example.grpcdemo.rest.generated.model.LiveTrackingCommandRequest;
import com.example.grpcdemo.rest.generated.model.OrderItem;
import com.example.grpcdemo.rest.generated.model.QuoteItemRequest;
import com.example.grpcdemo.rest.generated.model.QuoteResponse;
import com.example.grpcdemo.rest.generated.model.TrackingEventResponse;

public final class OrderGrpcMapper {

    private OrderGrpcMapper() {
    }

    public static CreateOrderGrpcRequest toGrpc(CreateOrderRequest request) {
        return CreateOrderGrpcRequest.newBuilder()
            .setCustomerName(request.getCustomerName())
            .setAddress(request.getAddress())
            .addAllItems(
                request.getItems()
                    .stream()
                    .map(OrderGrpcMapper::toGrpcOrderItem)
                    .toList()
            )
            .build();
    }

    public static CreateOrderResponse toDomain(CreateOrderGrpcResponse response) {
        return new CreateOrderResponse(response.getOrderId(), toDomainStatus(response.getStatus()));
    }

    public static GetOrderResponse toDomain(GetOrderGrpcResponse response) {
        return new GetOrderResponse(
            response.getOrderId(),
            response.getCustomerName(),
            response.getAddress(),
            toDomainStatus(response.getStatus()),
            response.getItemsList()
                .stream()
                .map(OrderGrpcMapper::toDomain)
                .toList()
        );
    }

    public static TrackingEventResponse toDomain(TrackingEventsGrpcResponse response) {
        return new TrackingEventResponse(
            response.getOrderId(),
            TrackingEventResponse.StatusEnum.fromValue(toDomainStatus(response.getStatus())),
            response.getMessage(),
            response.getTimestamp()
        );
    }

    public static QuoteItemGrpcRequest toGrpc(QuoteItemRequest request) {
        return QuoteItemGrpcRequest.newBuilder()
            .setProductId(request.getProductId())
            .setQuantity(request.getQuantity())
            .setWeight(request.getWeight())
            .build();
    }

    public static QuoteResponse toDomain(QuoteGrpcResponse response) {
        return new QuoteResponse(response.getDeliveryPrice(), response.getEstimatedDeliveryTimeMinutes());
    }

    public static DriverCommandGrpcRequest toGrpc(String orderId, LiveTrackingCommandRequest request) {
        return DriverCommandGrpcRequest.newBuilder()
            .setOrderId(orderId)
            .setCommand(DriverCommandType.valueOf(request.getCommand().getValue()))
            .build();
    }

    private static OrderItemGrpc toGrpcOrderItem(OrderItem restItem) {
        return OrderItemGrpc.newBuilder().setProductId(restItem.getProductId()).setName(restItem.getName()).setQuantity(restItem.getQuantity()).setPrice(restItem.getPrice()).build();
    }

    private static OrderItem toDomain(OrderItemGrpc grpcItem) {
        return new OrderItem(
            grpcItem.getProductId(),
            grpcItem.getName(),
            grpcItem.getQuantity(),
            grpcItem.getPrice()
        );
    }

    private static String toDomainStatus(OrderStatus grpcStatus) {
        return switch (grpcStatus) {
            case CREATED -> "CREATED";
            case PREPARING -> "PREPARING";
            case PICKED_UP -> "PICKED_UP";
            case ON_THE_WAY -> "ON_THE_WAY";
            case DELIVERED -> "DELIVERED";
            case CANCELLED -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }
}
