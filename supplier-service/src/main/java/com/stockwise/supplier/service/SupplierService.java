package com.stockwise.supplier.service;

@Service
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final ContractRepository contractRepository;

    public Contract addContract(Long supplierId, Contract contract) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));

        // Валидация дат контракта
        if (contract.getStartDate().isAfter(contract.getEndDate())) {
            throw new InvalidContractException("End date must be after start date");
        }

        contract.setSupplier(supplier);
        return contractRepository.save(contract);
    }

    public Contract updateContract(Long contractId, Contract updatedContract) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        // Обновляем только допустимые поля
        contract.setTerms(updatedContract.getTerms());
        contract.setDeliveryDays(updatedContract.getDeliveryDays());
        contract.setEndDate(updatedContract.getEndDate());

        return contractRepository.save(contract);
    }

    public List<Contract> getActiveContracts(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));

        Instant now = Instant.now();
        return supplier.getContracts().stream()
                .filter(c -> c.getStartDate().isBefore(now) && c.getEndDate().isAfter(now))
                .collect(Collectors.toList());
    }
}