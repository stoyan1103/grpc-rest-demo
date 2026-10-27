package com.example.grpc.testing;

import com.example.grpcdemo.contract.order.v1.OrderItemGrpc;

/** Shared sample values for tests that exercise the order gRPC API. */
public final class OrderGrpcTestData {

    public static final String ORDER_ID = "order-123";
    public static final String SECOND_ORDER_ID = "order-456";
    public static final String CUSTOMER_NAME = "Ada Lovelace";
    public static final String ADDRESS = "Sofia, Bulgaria";
    public static final String SECOND_CUSTOMER_NAME = "Grace Hopper";
    public static final String SECOND_ADDRESS = "Varna, Bulgaria";
    public static final String PRODUCT_ID = "product-1";
    public static final String SECOND_PRODUCT_ID = "product-2";
    public static final String PRODUCT_NAME = "Coffee";
    public static final int ITEM_QUANTITY = 2;
    public static final double ITEM_PRICE = 4.50;

    public static final OrderItemGrpc ORDER_ITEM = OrderItemGrpc.newBuilder()
        .setProductId(PRODUCT_ID)
        .setName(PRODUCT_NAME)
        .setQuantity(ITEM_QUANTITY)
        .setPrice(ITEM_PRICE)
        .build();

    private OrderGrpcTestData() {
    }
}
