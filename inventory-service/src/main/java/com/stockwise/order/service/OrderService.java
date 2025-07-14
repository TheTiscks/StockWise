package com.stockwise.order.service;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.order-events}")
    private String orderTopic;

    @Transactional
    public Order placeOrder(Order order) {
        order.setStatus(OrderStatus.CREATED);
        order.setOrderDate(Instant.now());
        Order savedOrder = orderRepository.save(order);

        // Отправляем событие в Kafka
        sendOrderEvent(savedOrder, "ORDER_CREATED");

        return savedOrder;
    }

    @Transactional
    public Order fulfillOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.setStatus(OrderStatus.FULFILLED);
        Order updatedOrder = orderRepository.save(order);

        // Отправляем событие в Kafka
        sendOrderEvent(updatedOrder, "ORDER_FULFILLED");

        return updatedOrder;
    }

    private void sendOrderEvent(Order order, String eventType) {
        try {
            OrderEvent event = new OrderEvent(
                    eventType,
                    order.getId(),
                    order.getCustomerId(),
                    order.getItems(),
                    Instant.now()
            );

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(orderTopic, order.getId().toString(), eventJson);
        } catch (JsonProcessingException e) {
            throw new OrderProcessingException("Failed to serialize order event", e);
        }
    }
}

@Transactional
public Order cancelOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

    if (order.getStatus() != OrderStatus.CREATED) {
        throw new OrderCancellationException("Only created orders can be cancelled");
    }

    order.setStatus(OrderStatus.CANCELLED);
    Order updated = orderRepository.save(order);

    // Отправляем событие отмены
    sendOrderEvent(updated, "ORDER_CANCELLED");

    return updated;
}

// Реализуем компенсирующие транзакции
@Transactional
public void compensateOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

    // Возвращаем товар на склад
    for (OrderItem item : order.getItems()) {
        // Отправляем событие для сервиса инвентаря
        kafkaTemplate.send("inventory-compensation",
                item.getProductId().toString(),
                item.getQuantity());
    }

    order.setStatus(OrderStatus.COMPENSATED);
    orderRepository.save(order);
}

// Шаблон Saga для распределенных транзакций
@Transactional
public void processOrderSaga(Order order) {
    // Шаг 1: Создание заказа
    order.setStatus(OrderStatus.CREATED);
    Order created = orderRepository.save(order);
    sendOrderEvent(created, "ORDER_CREATED");

    try {
        // Шаг 2: Резервирование товара (синхронный вызов)
        inventoryService.reserveItems(order.getItems());

        // Шаг 3: Подтверждение заказа
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        sendOrderEvent(order, "ORDER_CONFIRMED");
    } catch (Exception e) {
        // Компенсирующее действие
        compensateOrder(order.getId());
    }
}