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