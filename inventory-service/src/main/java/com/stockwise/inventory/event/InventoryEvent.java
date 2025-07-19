package com.inventory.event;

import java.util.UUID;

public class InventoryEvent {
    private UUID productId;
    private int delta;
    private String reason;

    // Конструктор по умолчанию (необходим для Jackson)
    public InventoryEvent() {}

    // Параметризованный конструктор
    public InventoryEvent(UUID productId, int delta, String reason) {
        this.productId = productId;
        this.delta = delta;
        this.reason = reason;
    }

    // Геттеры и сеттеры
    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    // Метод для преобразования в JSON
    public String toJson() {
        return String.format(
                "{\"productId\":\"%s\",\"delta\":%d,\"reason\":\"%s\"}",
                productId, delta, reason
        );
    }

    // Переопределение toString() для удобства отладки
    @Override
    public String toString() {
        return "InventoryEvent{" +
                "productId=" + productId +
                ", delta=" + delta +
                ", reason='" + reason + '\'' +
                '}';
    }
}