package com.stockwise.supplier.repository;

import com.stockwise.supplier.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    
    @Query("SELECT s FROM Supplier s WHERE s.status = :status")
    List<Supplier> findByStatus(@Param("status") Supplier.SupplierStatus status);
    
    @Query("SELECT s FROM Supplier s WHERE s.status = :status AND EXISTS (SELECT c FROM Contract c WHERE c.supplier = s AND c.isActive = true)")
    List<Supplier> findByStatusAndContractsIsActiveTrue(@Param("status") Supplier.SupplierStatus status);
    
    @Query("SELECT s FROM Supplier s WHERE " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.contactEmail) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Supplier> findByNameContainingIgnoreCaseOrContactEmailContainingIgnoreCase(
            @Param("query") String query, 
            @Param("query") String query2, 
            Pageable pageable);
    
    @Query("SELECT s FROM Supplier s WHERE s.rating >= :minRating ORDER BY s.rating DESC")
    List<Supplier> findByRatingGreaterThanEqualOrderByRatingDesc(@Param("minRating") Double minRating);
    
    @Query("SELECT s FROM Supplier s WHERE s.deliveryTimeAvg <= :maxDeliveryDays")
    List<Supplier> findByDeliveryTimeAvgLessThanEqual(@Param("maxDeliveryDays") Integer maxDeliveryDays);
    
    @Query("SELECT COUNT(s) FROM Supplier s WHERE s.status = :status")
    Long countByStatus(@Param("status") Supplier.SupplierStatus status);
    
    @Query("SELECT COUNT(s) FROM Supplier s WHERE s.rating >= :minRating")
    Long countByRatingGreaterThanEqual(@Param("minRating") Double minRating);
}
