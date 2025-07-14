// Добавляем обработку компенсационных событий
@KafkaListener(topics = "inventory-compensation")
public void handleCompensationEvent(String productId, int quantity) {
    try {
        UUID productUuid = UUID.fromString(productId);
        inventoryService.adjustStockByProduct(productUuid, quantity);
    } catch (Exception e) {
        // Отправка в DLQ при ошибках
        kafkaTemplate.send("inventory-dlq", productId, quantity);
    }
}

// Механизм идемпотентности
@KafkaListener(topics = "${kafka.topics.order-events}")
public void handleOrderEvent(
        String message,
        @Header(KafkaHeaders.RECEIVED_KEY) String eventId) {

    // Проверка на дубликаты
    if (eventRepository.existsByEventId(eventId)) {
        log.info("Event with id {} already processed. Skipping.", eventId);
        return;
    }

    try {
        // Десериализация события
        OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

        // Обработка разных типов событий
        switch (event.getEventType()) {
            case "ORDER_FULFILLED":
                processOrderFulfilled(event);
                break;

            case "ORDER_CANCELLED":
                processOrderCancelled(event);
                break;

            case "ORDER_CREATED":
                log.info("Order {} created. No inventory action needed.", event.getOrderId());
                break;

            default:
                log.warn("Unknown event type: {}", event.getEventType());
        }

        // Сохранение ID обработанного события
        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.setEventId(eventId);
        processedEvent.setProcessedAt(Instant.now());
        eventRepository.save(processedEvent);

    } catch (JsonProcessingException e) {
        log.error("JSON parsing error: {}", e.getMessage());
    } catch (InventoryException e) {
        log.error("Inventory processing error: {}", e.getMessage());
        // Отправка в DLQ при бизнес-ошибках
        kafkaTemplate.send("inventory-dlq", eventId, message);
    } catch (Exception e) {
        log.error("Unexpected error: {}", e.getMessage());
    }
}

private void processOrderFulfilled(OrderEvent event) {
    for (OrderEvent.OrderItem item : event.getItems()) {
        try {
            inventoryService.adjustStockByProduct(
                    item.getProductId(),
                    -item.getQuantity()  // Уменьшаем количество товара
            );
            log.info("Reduced stock for product {} by {}",
                    item.getProductId(), item.getQuantity());
        } catch (ProductNotFoundException e) {
            log.warn("Product not found: {}", item.getProductId());
        }
    }
}

private void processOrderCancelled(OrderEvent event) {
    for (OrderEvent.OrderItem item : event.getItems()) {
        try {
            inventoryService.adjustStockByProduct(
                    item.getProductId(),
                    item.getQuantity()  // Возвращаем товар
            );
            log.info("Restored stock for product {} by {}",
                    item.getProductId(), item.getQuantity());
        } catch (ProductNotFoundException e) {
            log.warn("Product not found: {}", item.getProductId());
        }
    }
}