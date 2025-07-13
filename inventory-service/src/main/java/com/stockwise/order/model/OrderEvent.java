package com.stockwise.order.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderEvent {
    private String eventType;  // ORDER_CREATED, ORDER_FULFILLED, ORDER_CANCELLED
    private Long orderId;
    private String customerId;
    private List<OrderItem> items;
    private Instant timestamp;

    // Конструкторы
    public OrderEvent() {}

    public OrderEvent(String eventType, Long orderId, String customerId, List<OrderItem> items, Instant timestamp) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.customerId = customerId;
        this.items = items;
        this.timestamp = timestamp;
    }

    // Геттеры и сеттеры
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    // Вложенный класс для элементов заказа
    public static class OrderItem {
        private UUID productId;
        private int quantity;

        public OrderItem() {}

        public OrderItem(UUID productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        // Геттеры и сеттеры
        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}