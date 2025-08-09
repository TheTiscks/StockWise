package com.stockwise.supplier.controller;

import com.stockwise.supplier.model.Supplier;
import com.stockwise.supplier.model.Contract;
import com.stockwise.supplier.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin(origins = "*")
public class SupplierController {
    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    // CRUD операции для поставщиков
    @PostMapping
    public ResponseEntity<Supplier> createSupplier(@Valid @RequestBody Supplier supplier) {
        return ResponseEntity.ok(supplierService.createSupplier(supplier));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @GetMapping
    public ResponseEntity<List<Supplier>> getAllSuppliers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(supplierService.getAllSuppliers(page, size));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody Supplier supplier) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, supplier));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }

    // Операции с контрактами
    @PostMapping("/{supplierId}/contracts")
    public ResponseEntity<Contract> addContract(
            @PathVariable Long supplierId,
            @Valid @RequestBody Contract contract) {
        return ResponseEntity.ok(supplierService.addContract(supplierId, contract));
    }

    @GetMapping("/{supplierId}/contracts")
    public ResponseEntity<List<Contract>> getActiveContracts(@PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierService.getActiveContracts(supplierId));
    }

    @PutMapping("/contracts/{contractId}")
    public ResponseEntity<Contract> updateContract(
            @PathVariable Long contractId,
            @Valid @RequestBody Contract contract) {
        return ResponseEntity.ok(supplierService.updateContract(contractId, contract));
    }

    @DeleteMapping("/contracts/{contractId}")
    public ResponseEntity<Void> deleteContract(@PathVariable Long contractId) {
        supplierService.deleteContract(contractId);
        return ResponseEntity.noContent().build();
    }

    // Дополнительные операции
    @GetMapping("/{id}/rating")
    public ResponseEntity<Double> getSupplierRating(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierRating(id));
    }

    @PostMapping("/{id}/rate")
    public ResponseEntity<Void> rateSupplier(
            @PathVariable Long id,
            @RequestParam Double rating) {
        supplierService.rateSupplier(id, rating);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Supplier>> searchSuppliers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(supplierService.searchSuppliers(query, page, size));
    }

    // Интеграция с системой пополнения
    @GetMapping("/available-for-product")
    public ResponseEntity<List<Supplier>> getAvailableSuppliersForProduct(
            @RequestParam String productId) {
        return ResponseEntity.ok(supplierService.getAvailableSuppliersForProduct(productId));
    }

    @GetMapping("/best-contract")
    public ResponseEntity<Contract> getBestContractForProduct(
            @RequestParam String productId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(supplierService.getBestContractForProduct(productId, quantity));
    }

    @PostMapping("/auto-order")
    public ResponseEntity<Void> createAutomaticOrder(
            @RequestParam String productId,
            @RequestParam int quantity) {
        supplierService.createAutomaticOrder(productId, quantity);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{supplierId}/expiring-contracts")
    public ResponseEntity<List<Contract>> getExpiringContracts(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "30") int daysBeforeExpiration) {
        return ResponseEntity.ok(supplierService.getExpiringContracts(supplierId, daysBeforeExpiration));
    }
}