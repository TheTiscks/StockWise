package com.stockwise.supplier.repository;

import com.stockwise.supplier.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    
    @Query("SELECT c FROM Contract c WHERE c.supplier.id = :supplierId AND c.isActive = true")
    List<Contract> findBySupplierIdAndIsActiveTrue(@Param("supplierId") Long supplierId);
    
    @Query("SELECT c FROM Contract c WHERE c.status = :status AND c.isActive = true")
    List<Contract> findByStatusAndIsActiveTrue(@Param("status") Contract.ContractStatus status);
    
    @Query("SELECT c FROM Contract c WHERE c.endDate BETWEEN :startDate AND :endDate")
    List<Contract> findExpiringContracts(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    @Query("SELECT c FROM Contract c WHERE c.supplier.id = :supplierId AND c.status = :status")
    List<Contract> findBySupplierIdAndStatus(
            @Param("supplierId") Long supplierId,
            @Param("status") Contract.ContractStatus status);
    
    @Query("SELECT c FROM Contract c WHERE c.minOrderQuantity <= :quantity AND " +
           "(c.maxOrderQuantity IS NULL OR c.maxOrderQuantity >= :quantity) AND " +
           "c.status = :status AND c.isActive = true " +
           "ORDER BY c.terms ASC")
    List<Contract> findSuitableContracts(
            @Param("quantity") Integer quantity,
            @Param("status") Contract.ContractStatus status);
}
