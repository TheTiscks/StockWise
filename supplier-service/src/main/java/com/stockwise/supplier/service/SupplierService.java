package com.stockwise.supplier.service;

import com.stockwise.supplier.model.Supplier;
import com.stockwise.supplier.model.Contract;
import com.stockwise.supplier.repository.SupplierRepository;
import com.stockwise.supplier.repository.ContractRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final ContractRepository contractRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics.supplier-events}")
    private String supplierTopic;

    public SupplierService(SupplierRepository supplierRepository,
                          ContractRepository contractRepository,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.supplierRepository = supplierRepository;
        this.contractRepository = contractRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // CRUD операции для поставщиков
    public Supplier createSupplier(Supplier supplier) {
        Supplier savedSupplier = supplierRepository.save(supplier);
        sendSupplierEvent(savedSupplier, "SUPPLIER_CREATED");
        return savedSupplier;
    }

    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
    }

    public List<Supplier> getAllSuppliers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Supplier> supplierPage = supplierRepository.findAll(pageable);
        return supplierPage.getContent();
    }

    public Supplier updateSupplier(Long id, Supplier supplierDetails) {
        Supplier supplier = getSupplierById(id);
        supplier.setName(supplierDetails.getName());
        supplier.setContactEmail(supplierDetails.getContactEmail());
        supplier.setPhoneNumber(supplierDetails.getPhoneNumber());
        supplier.setAddress(supplierDetails.getAddress());
        supplier.setStatus(supplierDetails.getStatus());
        
        Supplier updatedSupplier = supplierRepository.save(supplier);
        sendSupplierEvent(updatedSupplier, "SUPPLIER_UPDATED");
        return updatedSupplier;
    }

    public void deleteSupplier(Long id) {
        Supplier supplier = getSupplierById(id);
        supplierRepository.delete(supplier);
        sendSupplierEvent(supplier, "SUPPLIER_DELETED");
    }

    // Операции с контрактами
    public Contract addContract(Long supplierId, Contract contract) {
        Supplier supplier = getSupplierById(supplierId);
        contract.setSupplier(supplier);
        Contract savedContract = contractRepository.save(contract);
        sendContractEvent(savedContract, "CONTRACT_CREATED");
        return savedContract;
    }

    public List<Contract> getActiveContracts(Long supplierId) {
        return contractRepository.findBySupplierIdAndIsActiveTrue(supplierId);
    }

    public Contract updateContract(Long contractId, Contract contractDetails) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found: " + contractId));
        
        contract.setContractNumber(contractDetails.getContractNumber());
        contract.setStartDate(contractDetails.getStartDate());
        contract.setEndDate(contractDetails.getEndDate());
        contract.setTerms(contractDetails.getTerms());
        contract.setDeliveryDays(contractDetails.getDeliveryDays());
        contract.setPaymentConditions(contractDetails.getPaymentConditions());
        contract.setStatus(contractDetails.getStatus());
        
        Contract updatedContract = contractRepository.save(contract);
        sendContractEvent(updatedContract, "CONTRACT_UPDATED");
        return updatedContract;
    }

    public void deleteContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found: " + contractId));
        contractRepository.delete(contract);
        sendContractEvent(contract, "CONTRACT_DELETED");
    }

    // Дополнительные операции
    public Double getSupplierRating(Long id) {
        Supplier supplier = getSupplierById(id);
        return supplier.getRating();
    }

    public void rateSupplier(Long id, Double rating) {
        Supplier supplier = getSupplierById(id);
        supplier.setRating(rating);
        supplierRepository.save(supplier);
        sendSupplierEvent(supplier, "SUPPLIER_RATED");
    }

    public List<Supplier> searchSuppliers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return supplierRepository.findByNameContainingIgnoreCaseOrContactEmailContainingIgnoreCase(
                query, query, pageable);
    }

    // Интеграция с системой пополнения
    public List<Supplier> getAvailableSuppliersForProduct(String productId) {
        // Логика выбора поставщиков для конкретного продукта
        return supplierRepository.findByStatusAndContractsIsActiveTrue(Supplier.SupplierStatus.ACTIVE);
    }

    public Contract getBestContractForProduct(String productId, int quantity) {
        // Логика выбора лучшего контракта для заказа
        List<Contract> availableContracts = contractRepository.findByStatusAndIsActiveTrue(Contract.ContractStatus.ACTIVE);
        return availableContracts.stream()
                .filter(contract -> contract.getMinOrderQuantity() <= quantity && 
                                  (contract.getMaxOrderQuantity() == null || contract.getMaxOrderQuantity() >= quantity))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No suitable contract found"));
    }

    // Отправка событий в Kafka
    private void sendSupplierEvent(Supplier supplier, String eventType) {
        // Реализация отправки события
    }

    private void sendContractEvent(Contract contract, String eventType) {
        // Реализация отправки события
    }
}