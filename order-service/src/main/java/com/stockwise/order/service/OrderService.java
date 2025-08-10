package com.stockwise.order.service;

import com.stockwise.order.model.Order;
import com.stockwise.order.model.OrderItem;
import com.stockwise.order.repository.OrderRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.order-events:order-events}")
    private String orderTopic;

    public OrderService(OrderRepository orderRepository,
                       KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }

    // CRUD операции для заказов
    @CacheEvict(value = "orders", allEntries = true)
    public Order createOrder(Order order) {
        // Генерируем номер заказа если не указан
        if (order.getOrderNumber() == null || order.getOrderNumber().isEmpty()) {
            order.setOrderNumber(generateOrderNumber());
        }
        
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.PENDING);
        
        Order savedOrder = orderRepository.save(order);
        sendOrderEvent(savedOrder, "ORDER_CREATED");
        return savedOrder;
    }

    @Cacheable(value = "orders", key = "#id")
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    @Cacheable(value = "orders", key = "#orderNumber")
    public Order getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    @Cacheable(value = "orders", key = "'all_' + #page + '_' + #size")
    public List<Order> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = orderRepository.findAll(pageable);
        return orderPage.getContent();
    }

    @CacheEvict(value = "orders", allEntries = true)
    public Order updateOrder(Long id, Order orderDetails) {
        Order order = getOrderById(id);
        
        // Обновляем только разрешенные поля
        order.setStatus(orderDetails.getStatus());
        order.setExpectedDeliveryDate(orderDetails.getExpectedDeliveryDate());
        order.setActualDeliveryDate(orderDetails.getActualDeliveryDate());
        order.setNotes(orderDetails.getNotes());
        
        Order updatedOrder = orderRepository.save(order);
        sendOrderEvent(updatedOrder, "ORDER_UPDATED");
        return updatedOrder;
    }

    @CacheEvict(value = "orders", allEntries = true)
    public void deleteOrder(Long id) {
        Order order = getOrderById(id);
        orderRepository.delete(order);
        sendOrderEvent(order, "ORDER_DELETED");
    }

    // Операции со статусом заказа
    @CacheEvict(value = "orders", allEntries = true)
    public Order confirmOrder(Long id) {
        Order order = getOrderById(id);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        Order confirmedOrder = orderRepository.save(order);
        sendOrderEvent(confirmedOrder, "ORDER_CONFIRMED");
        return confirmedOrder;
    }

    @CacheEvict(value = "orders", allEntries = true)
    public Order startProcessing(Long id) {
        Order order = getOrderById(id);
        order.setStatus(Order.OrderStatus.IN_PROGRESS);
        Order processingOrder = orderRepository.save(order);
        sendOrderEvent(processingOrder, "ORDER_IN_PROGRESS");
        return processingOrder;
    }

    @CacheEvict(value = "orders", allEntries = true)
    public Order shipOrder(Long id) {
        Order order = getOrderById(id);
        order.setStatus(Order.OrderStatus.SHIPPED);
        Order shippedOrder = orderRepository.save(order);
        sendOrderEvent(shippedOrder, "ORDER_SHIPPED");
        return shippedOrder;
    }

    @CacheEvict(value = "orders", allEntries = true)
    public Order deliverOrder(Long id) {
        Order order = getOrderById(id);
        order.setStatus(Order.OrderStatus.DELIVERED);
        order.setActualDeliveryDate(LocalDateTime.now());
        Order deliveredOrder = orderRepository.save(order);
        sendOrderEvent(deliveredOrder, "ORDER_DELIVERED");
        return deliveredOrder;
    }

    @CacheEvict(value = "orders", allEntries = true)
    public Order cancelOrder(Long id) {
        Order order = getOrderById(id);
        order.setStatus(Order.OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);
        sendOrderEvent(cancelledOrder, "ORDER_CANCELLED");
        return cancelledOrder;
    }

    // Запросы и фильтрация
    @Cacheable(value = "orders", key = "#supplierId")
    public List<Order> getOrdersBySupplier(Long supplierId) {
        return orderRepository.findBySupplierId(supplierId);
    }

    @Cacheable(value = "orders", key = "#status")
    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    @Cacheable(value = "orders", key = "#productId")
    public List<Order> getOrdersByProduct(String productId) {
        return orderRepository.findByProductId(productId);
    }

    @Cacheable(value = "orders", key = "'pending_' + #page + '_' + #size")
    public List<Order> getPendingOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> pendingOrdersPage = orderRepository.findPendingOrders(pageable);
        return pendingOrdersPage.getContent();
    }

    @Cacheable(value = "orders", key = "'overdue'")
    public List<Order> getOverdueOrders() {
        return orderRepository.findOverdueOrders(LocalDateTime.now());
    }

    @Cacheable(value = "orders", key = "#startDate + '_' + #endDate")
    public List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findByOrderDateBetween(startDate, endDate);
    }

    // Автоматические операции
    @Scheduled(cron = "0 0 9 * * *") // Ежедневно в 9 утра
    public void checkOverdueOrders() {
        List<Order> overdueOrders = getOverdueOrders();
        
        for (Order order : overdueOrders) {
            sendOverdueNotification(order);
        }
    }

    @Scheduled(cron = "0 0 6 * * *") // Ежедневно в 6 утра
    public void processPendingOrders() {
        List<Order> pendingOrders = getPendingOrders(0, 100);
        
        for (Order order : pendingOrders) {
            // Логика автоматической обработки заказов
            if (shouldAutoConfirm(order)) {
                confirmOrder(order.getId());
            }
        }
    }

    // Вспомогательные методы
    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean shouldAutoConfirm(Order order) {
        // Логика для определения, нужно ли автоматически подтверждать заказ
        // Например, если заказ от проверенного поставщика или сумма заказа меньше определенного лимита
        return order.getTotalPrice() != null && order.getTotalPrice().compareTo(BigDecimal.valueOf(10000)) < 0;
    }

    private void sendOverdueNotification(Order order) {
        OrderOverdueEvent event = new OrderOverdueEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getExpectedDeliveryDate()
        );
        
        sendOrderEvent(order, "ORDER_OVERDUE");
    }

    // Отправка событий в Kafka
    private void sendOrderEvent(Order order, String eventType) {
        try {
            OrderEvent event = new OrderEvent(eventType, order);
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(orderTopic, eventJson);
        } catch (Exception e) {
            System.err.println("Error sending order event: " + e.getMessage());
        }
    }

    // Вспомогательные классы для событий
    public static class OrderEvent {
        private String eventType;
        private Order order;
        
        public OrderEvent(String eventType, Order order) {
            this.eventType = eventType;
            this.order = order;
        }
        
        // Getters and setters
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public Order getOrder() { return order; }
        public void setOrder(Order order) { this.order = order; }
    }

    public static class OrderOverdueEvent {
        private Long orderId;
        private String orderNumber;
        private LocalDateTime expectedDeliveryDate;
        
        public OrderOverdueEvent(Long orderId, String orderNumber, LocalDateTime expectedDeliveryDate) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.expectedDeliveryDate = expectedDeliveryDate;
        }
        
        // Getters and setters
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public LocalDateTime getExpectedDeliveryDate() { return expectedDeliveryDate; }
        public void setExpectedDeliveryDate(LocalDateTime expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
    }
}
