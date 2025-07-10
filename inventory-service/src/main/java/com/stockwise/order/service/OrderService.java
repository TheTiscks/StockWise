package com.stockwise.order.service;

@Service
public class OrderService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kafka.topics.order-events}")
    private String orderTopic;

    public void placeOrder(Order order) {
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            kafkaTemplate.send(orderTopic, orderJson);
        } catch (JsonProcessingException e) {
            throw new OrderProcessingException("Failed to serialize order", e);
        }
    }
}