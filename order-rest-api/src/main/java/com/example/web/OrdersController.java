package com.example.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.client.OrderGrpcClient;
import com.example.grpcdemo.rest.generated.api.OrdersApi;
import com.example.grpcdemo.rest.generated.model.CreateOrderRequest;
import com.example.grpcdemo.rest.generated.model.CreateOrderResponse;
import com.example.grpcdemo.rest.generated.model.GetOrderResponse;
import com.example.grpcdemo.rest.generated.model.LiveTrackingCommandRequest;
import com.example.grpcdemo.rest.generated.model.QuoteItemRequest;
import com.example.grpcdemo.rest.generated.model.QuoteResponse;
import com.example.grpcdemo.rest.generated.model.TrackingEventResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@RestController
public class OrdersController implements OrdersApi {

    private final OrderGrpcClient orderGrpcClient;

    @Autowired
    public OrdersController(OrderGrpcClient orderGrpcClient) {
        this.orderGrpcClient = requireNonNull(orderGrpcClient, "OrderGrpcClient must not be null");
    }

    // Keep parameter constraints aligned with the generated OrdersApi contract.
    @Override
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid CreateOrderRequest createOrderRequest) {
        return status(CREATED).body(orderGrpcClient.createOrder(createOrderRequest));
    }

    @Override
    public ResponseEntity<GetOrderResponse> getOrder(@Size(min = 1) String orderId) {
        return ok(orderGrpcClient.getOrder(orderId));
    }

    @Override
    public ResponseEntity<List<TrackingEventResponse>> getOrderTracking(@Size(min = 1) String orderId) {
        return ok(orderGrpcClient.getTrackingEvents(orderId));
    }

    @Override
    public ResponseEntity<List<GetOrderResponse>> getOrders() {
        return ok(orderGrpcClient.getOrders());
    }

    @Override
    public ResponseEntity<List<TrackingEventResponse>> liveTrackOrder(
        @Size(min = 1) String orderId,
        @Size(min = 1) @Valid List<@Valid LiveTrackingCommandRequest> liveTrackingCommandRequest
    ) {
        return ok(orderGrpcClient.liveTrackOrder(orderId, liveTrackingCommandRequest));
    }

    @Override
    public ResponseEntity<QuoteResponse> calculateQuote(
        @Size(min = 1) @Valid List<@Valid QuoteItemRequest> quoteItemRequest
    ) {
        return ok(orderGrpcClient.calculateQuote(quoteItemRequest));
    }

}
