package com.stockwise.inventory.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topics.order-events}", groupId = "inventory-service-group")
    public void handleOrderEvent(String message) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            if ("ORDER_FULFILLED".equals(event.getEventType())) {
                for (OrderEvent.OrderItem item : event.getItems()) {
                    inventoryService.adjustStockByProduct(
                            item.getProductId(),
                            -item.getQuantity()  // Уменьшаем запас
                    );
                }
            }
            else if ("ORDER_CANCELLED".equals(event.getEventType())) {
                for (OrderEvent.OrderItem item : event.getItems()) {
                    inventoryService.adjustStockByProduct(
                            item.getProductId(),
                            item.getQuantity()  // Возвращаем товар
                    );
                }
            }
        } catch (JsonProcessingException e) {
            // Реальная система: добавить логирование и обработку ошибок
            System.err.println("Error processing Kafka message: " + e.getMessage());
        }
    }

    // Внутренний DTO для десериализации событий
    public static class OrderEvent {
        private String eventType;
        private List<OrderItem> items;

        // Геттеры/сеттеры
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public List<OrderItem> getItems() { return items; }
        public void setItems(List<OrderItem> items) { this.items = items; }

        public static class OrderItem {
            private UUID productId;
            private int quantity;

            // Геттеры/сеттеры
            public UUID getProductId() { return productId; }
            public void setProductId(UUID productId) { this.productId = productId; }
            public int getQuantity() { return quantity; }
            public void setQuantity(int quantity) { this.quantity = quantity; }
        }
    }
}