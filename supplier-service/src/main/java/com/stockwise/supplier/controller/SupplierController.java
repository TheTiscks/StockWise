package com.stockwise.supplier.controller;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService supplierService;

    @PostMapping("/{supplierId}/contracts")
    public ResponseEntity<Contract> addContract(
            @PathVariable Long supplierId,
            @RequestBody Contract contract) {
        return ResponseEntity.ok(supplierService.addContract(supplierId, contract));
    }

    @PutMapping("/contracts/{contractId}")
    public ResponseEntity<Contract> updateContract(
            @PathVariable Long contractId,
            @RequestBody Contract contract) {
        return ResponseEntity.ok(supplierService.updateContract(contractId, contract));
    }

    @GetMapping("/{supplierId}/contracts")
    public ResponseEntity<List<Contract>> getActiveContracts(
            @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierService.getActiveContracts(supplierId));
    }
}