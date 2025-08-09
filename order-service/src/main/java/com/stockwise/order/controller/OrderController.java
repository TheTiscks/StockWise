package com.stockwise.order.controller;

import com.stockwise.order.model.Order;
import com.stockwise.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // CRUD операции для заказов
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody Order order) {
        return ResponseEntity.ok(orderService.createOrder(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<Order> getOrderByNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByOrderNumber(orderNumber));
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getAllOrders(page, size));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody Order order) {
        return ResponseEntity.ok(orderService.updateOrder(id, order));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    // Операции со статусом заказа
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Order> confirmOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.confirmOrder(id));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<Order> startProcessing(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.startProcessing(id));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<Order> shipOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.shipOrder(id));
    }

    @PostMapping("/{id}/deliver")
    public ResponseEntity<Order> deliverOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.deliverOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    // Запросы и фильтрация
    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<List<Order>> getOrdersBySupplier(@PathVariable Long supplierId) {
        return ResponseEntity.ok(orderService.getOrdersBySupplier(supplierId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getOrdersByStatus(@PathVariable String status) {
        Order.OrderStatus orderStatus = Order.OrderStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(orderService.getOrdersByStatus(orderStatus));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Order>> getOrdersByProduct(@PathVariable String productId) {
        return ResponseEntity.ok(orderService.getOrdersByProduct(productId));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Order>> getPendingOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getPendingOrders(page, size));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<Order>> getOverdueOrders() {
        return ResponseEntity.ok(orderService.getOverdueOrders());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<Order>> getOrdersByDateRange(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        return ResponseEntity.ok(orderService.getOrdersByDateRange(startDate, endDate));
    }

    // Статистика
    @GetMapping("/stats")
    public ResponseEntity<OrderStats> getOrderStats() {
        List<Order> pendingOrders = orderService.getOrdersByStatus(Order.OrderStatus.PENDING);
        List<Order> confirmedOrders = orderService.getOrdersByStatus(Order.OrderStatus.CONFIRMED);
        List<Order> inProgressOrders = orderService.getOrdersByStatus(Order.OrderStatus.IN_PROGRESS);
        List<Order> shippedOrders = orderService.getOrdersByStatus(Order.OrderStatus.SHIPPED);
        List<Order> deliveredOrders = orderService.getOrdersByStatus(Order.OrderStatus.DELIVERED);
        List<Order> cancelledOrders = orderService.getOrdersByStatus(Order.OrderStatus.CANCELLED);
        List<Order> overdueOrders = orderService.getOverdueOrders();

        OrderStats stats = new OrderStats(
                pendingOrders.size(),
                confirmedOrders.size(),
                inProgressOrders.size(),
                shippedOrders.size(),
                deliveredOrders.size(),
                cancelledOrders.size(),
                overdueOrders.size()
        );

        return ResponseEntity.ok(stats);
    }

    // Вспомогательный класс для статистики
    public static class OrderStats {
        private int pendingCount;
        private int confirmedCount;
        private int inProgressCount;
        private int shippedCount;
        private int deliveredCount;
        private int cancelledCount;
        private int overdueCount;

        public OrderStats(int pendingCount, int confirmedCount, int inProgressCount,
                         int shippedCount, int deliveredCount, int cancelledCount, int overdueCount) {
            this.pendingCount = pendingCount;
            this.confirmedCount = confirmedCount;
            this.inProgressCount = inProgressCount;
            this.shippedCount = shippedCount;
            this.deliveredCount = deliveredCount;
            this.cancelledCount = cancelledCount;
            this.overdueCount = overdueCount;
        }

        // Getters and setters
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
        public int getConfirmedCount() { return confirmedCount; }
        public void setConfirmedCount(int confirmedCount) { this.confirmedCount = confirmedCount; }
        public int getInProgressCount() { return inProgressCount; }
        public void setInProgressCount(int inProgressCount) { this.inProgressCount = inProgressCount; }
        public int getShippedCount() { return shippedCount; }
        public void setShippedCount(int shippedCount) { this.shippedCount = shippedCount; }
        public int getDeliveredCount() { return deliveredCount; }
        public void setDeliveredCount(int deliveredCount) { this.deliveredCount = deliveredCount; }
        public int getCancelledCount() { return cancelledCount; }
        public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }
        public int getOverdueCount() { return overdueCount; }
        public void setOverdueCount(int overdueCount) { this.overdueCount = overdueCount; }
    }
}
