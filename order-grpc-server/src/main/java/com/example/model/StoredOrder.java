package com.example.model;

import java.util.List;

import com.example.grpcdemo.contract.order.v1.OrderItemGrpc;
import com.example.grpcdemo.contract.order.v1.OrderStatus;

public record StoredOrder(String orderId, String customerName, String address, OrderStatus status,
                          List<OrderItemGrpc> items) {
}
