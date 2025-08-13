package com.stockwise.order.service;

import com.stockwise.order.model.Order;
import com.stockwise.order.model.OrderStats;
import com.stockwise.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    @Test
    void testCreateOrder() {
        // Given
        Order order = new Order();
        order.setSupplierId(1L);
        order.setProductId("test-product");
        order.setQuantity(10);
        order.setUnitPrice(100.0);

        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // When
        Order result = orderService.createOrder(order);

        // Then
        assertNotNull(result);
        assertNotNull(result.getOrderNumber());
        assertEquals(Order.OrderStatus.PENDING, result.getStatus());
        verify(orderRepository).save(any(Order.class));
        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
    }

    @Test
    void testGetOrderById() {
        // Given
        Long orderId = 1L;
        Order order = new Order();
        order.setId(orderId);
        order.setOrderNumber("ORD-001");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When
        Order result = orderService.getOrderById(orderId);

        // Then
        assertNotNull(result);
        assertEquals(orderId, result.getId());
        assertEquals("ORD-001", result.getOrderNumber());
    }

    @Test
    void testGetOrderStats() {
        // Given
        when(orderRepository.count()).thenReturn(100L);
        when(orderRepository.countByStatus(Order.OrderStatus.PENDING)).thenReturn(20L);
        when(orderRepository.countByStatus(Order.OrderStatus.CONFIRMED)).thenReturn(30L);
        when(orderRepository.countByStatus(Order.OrderStatus.DELIVERED)).thenReturn(40L);
        when(orderRepository.countByStatus(Order.OrderStatus.CANCELLED)).thenReturn(10L);

        // When
        OrderStats stats = orderService.getOrderStats();

        // Then
        assertNotNull(stats);
        assertEquals(100L, stats.getTotalOrders());
        assertEquals(20L, stats.getPendingOrders());
        assertEquals(30L, stats.getConfirmedOrders());
        assertEquals(40L, stats.getDeliveredOrders());
        assertEquals(10L, stats.getCancelledOrders());
        assertEquals(40.0, stats.getDeliveryRate());
        assertEquals(10.0, stats.getCancellationRate());
        assertEquals(50L, stats.getActiveOrders());
    }
}
