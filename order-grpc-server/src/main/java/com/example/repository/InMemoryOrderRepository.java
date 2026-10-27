package com.example.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.example.grpcdemo.contract.order.v1.OrderItemGrpc;
import com.example.model.StoredOrder;

import static com.example.grpcdemo.contract.order.v1.OrderStatus.CREATED;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;

public class InMemoryOrderRepository {

    private final Map<String, StoredOrder> orders = new ConcurrentHashMap<>();

    public StoredOrder createOrder(String customerName, String address, List<OrderItemGrpc> items) {
        String orderId = randomUUID().toString();

        StoredOrder order = new StoredOrder(orderId, customerName, address, CREATED, items);

        orders.put(orderId, order);
        return order;
    }

    public Optional<StoredOrder> findById(String orderId) {
        return ofNullable(orders.get(orderId));
    }

    public List<StoredOrder> findAll() {
        return List.copyOf(orders.values());
    }
}
