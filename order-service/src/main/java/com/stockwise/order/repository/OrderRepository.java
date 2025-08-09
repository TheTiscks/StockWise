package com.stockwise.order.repository;

import com.stockwise.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Order findByOrderNumber(@Param("orderNumber") String orderNumber);
    
    @Query("SELECT o FROM Order o WHERE o.supplierId = :supplierId")
    List<Order> findBySupplierId(@Param("supplierId") Long supplierId);
    
    @Query("SELECT o FROM Order o WHERE o.status = :status")
    List<Order> findByStatus(@Param("status") Order.OrderStatus status);
    
    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :startDate AND :endDate")
    List<Order> findByOrderDateBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT o FROM Order o WHERE o.productId = :productId")
    List<Order> findByProductId(@Param("productId") String productId);
    
    @Query("SELECT o FROM Order o WHERE o.supplierId = :supplierId AND o.status = :status")
    List<Order> findBySupplierIdAndStatus(
            @Param("supplierId") Long supplierId,
            @Param("status") Order.OrderStatus status);
    
    @Query("SELECT o FROM Order o WHERE o.expectedDeliveryDate <= :date AND o.status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')")
    List<Order> findOverdueOrders(@Param("date") LocalDateTime date);
    
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' ORDER BY o.orderDate ASC")
    Page<Order> findPendingOrders(Pageable pageable);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Long countByStatus(@Param("status") Order.OrderStatus status);
}
