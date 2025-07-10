package com.stockwise.supplier.service;

@Service
public class SupplierService {
    private final SupplierRepository supplierRepository;

    public Contract addContractToSupplier(Long supplierId, Contract contract) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));

        contract.setSupplier(supplier);
        supplier.getContracts().add(contract);

        return supplierRepository.save(supplier)
                .getContracts()
                .get(supplier.getContracts().size() - 1);
    }
}