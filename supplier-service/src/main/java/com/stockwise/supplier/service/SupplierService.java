package com.stockwise.supplier.service;

import com.stockwise.supplier.model.Supplier;
import com.stockwise.supplier.model.Contract;
import com.stockwise.supplier.repository.SupplierRepository;
import com.stockwise.supplier.repository.ContractRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final ContractRepository contractRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.supplier-events:supplier-events}")
    private String supplierTopic;

    @Value("${kafka.topics.contract-events:contract-events}")
    private String contractTopic;

    public SupplierService(SupplierRepository supplierRepository,
                          ContractRepository contractRepository,
                          KafkaTemplate<String, String> kafkaTemplate,
                          RestTemplate restTemplate) {
        this.supplierRepository = supplierRepository;
        this.contractRepository = contractRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    // CRUD операции для поставщиков
    @CacheEvict(value = "suppliers", allEntries = true)
    public Supplier createSupplier(Supplier supplier) {
        Supplier savedSupplier = supplierRepository.save(supplier);
        sendSupplierEvent(savedSupplier, "SUPPLIER_CREATED");
        return savedSupplier;
    }

    @Cacheable(value = "suppliers", key = "#id")
    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
    }

    @Cacheable(value = "suppliers", key = "'all_' + #page + '_' + #size")
    public List<Supplier> getAllSuppliers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Supplier> supplierPage = supplierRepository.findAll(pageable);
        return supplierPage.getContent();
    }

    @CacheEvict(value = "suppliers", allEntries = true)
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

    @CacheEvict(value = "suppliers", allEntries = true)
    public void deleteSupplier(Long id) {
        Supplier supplier = getSupplierById(id);
        supplierRepository.delete(supplier);
        sendSupplierEvent(supplier, "SUPPLIER_DELETED");
    }

    // Операции с контрактами
    @CacheEvict(value = "contracts", allEntries = true)
    public Contract addContract(Long supplierId, Contract contract) {
        Supplier supplier = getSupplierById(supplierId);
        contract.setSupplier(supplier);
        Contract savedContract = contractRepository.save(contract);
        sendContractEvent(savedContract, "CONTRACT_CREATED");
        return savedContract;
    }

    @Cacheable(value = "contracts", key = "#supplierId")
    public List<Contract> getActiveContracts(Long supplierId) {
        return contractRepository.findBySupplierIdAndIsActiveTrue(supplierId);
    }

    @CacheEvict(value = "contracts", allEntries = true)
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

    @CacheEvict(value = "contracts", allEntries = true)
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
        List<Contract> availableContracts = contractRepository.findSuitableContracts(
                quantity, Contract.ContractStatus.ACTIVE);
        
        if (availableContracts.isEmpty()) {
            throw new RuntimeException("No suitable contract found for product: " + productId + " with quantity: " + quantity);
        }
        
        // Возвращаем контракт с лучшими условиями (низкая цена)
        return availableContracts.get(0);
    }

    // Автоматические заказы
    @Scheduled(cron = "0 0 8 * * *") // Ежедневно в 8 утра
    public void generateSupplierOrders() {
        List<Supplier> activeSuppliers = supplierRepository.findByStatus(Supplier.SupplierStatus.ACTIVE);
        
        for (Supplier supplier : activeSuppliers) {
            List<Contract> expiringContracts = getExpiringContracts(supplier.getId(), 30);
            
            for (Contract contract : expiringContracts) {
                sendContractExpirationNotification(contract);
            }
        }
    }

    public List<Contract> getExpiringContracts(Long supplierId, int daysBeforeExpiration) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysBeforeExpiration);
        return contractRepository.findExpiringContracts(startDate, endDate);
    }

    // Автоматическое создание заказов на пополнение
    public void createAutomaticOrder(String productId, int quantity) {
        try {
            // Получаем доступных поставщиков
            List<Supplier> availableSuppliers = getAvailableSuppliersForProduct(productId);
            
            if (availableSuppliers.isEmpty()) {
                throw new RuntimeException("No available suppliers for product: " + productId);
            }
            
            // Выбираем лучшего поставщика (по рейтингу)
            Supplier bestSupplier = availableSuppliers.stream()
                    .filter(s -> s.getRating() != null && s.getRating() >= 4.0)
                    .max((s1, s2) -> Double.compare(s1.getRating(), s2.getRating()))
                    .orElse(availableSuppliers.get(0));
            
            // Получаем лучший контракт
            Contract bestContract = getBestContractForProduct(productId, quantity);
            
            // Создаем заказ
            createOrder(bestSupplier, bestContract, productId, quantity);
            
        } catch (Exception e) {
            // Логирование ошибки
            System.err.println("Error creating automatic order: " + e.getMessage());
        }
    }

    private void createOrder(Supplier supplier, Contract contract, String productId, int quantity) {
        // Создаем заказ и отправляем событие
        OrderRequest orderRequest = new OrderRequest(
                supplier.getId(),
                contract.getId(),
                productId,
                quantity,
                "AUTO_GENERATED"
        );
        
        sendOrderEvent(orderRequest, "ORDER_CREATED");
    }

    private void sendContractExpirationNotification(Contract contract) {
        ContractExpirationEvent event = new ContractExpirationEvent(
                contract.getId(),
                contract.getSupplier().getId(),
                contract.getEndDate()
        );
        
        sendContractEvent(contract, "CONTRACT_EXPIRING");
    }

    // Отправка событий в Kafka
    private void sendSupplierEvent(Supplier supplier, String eventType) {
        try {
            SupplierEvent event = new SupplierEvent(eventType, supplier);
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(supplierTopic, eventJson);
        } catch (Exception e) {
            System.err.println("Error sending supplier event: " + e.getMessage());
        }
    }

    private void sendContractEvent(Contract contract, String eventType) {
        try {
            ContractEvent event = new ContractEvent(eventType, contract);
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(contractTopic, eventJson);
        } catch (Exception e) {
            System.err.println("Error sending contract event: " + e.getMessage());
        }
    }

    private void sendOrderEvent(OrderRequest orderRequest, String eventType) {
        try {
            OrderEvent event = new OrderEvent(eventType, orderRequest);
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("orders", eventJson);
        } catch (Exception e) {
            System.err.println("Error sending order event: " + e.getMessage());
        }
    }

    // Вспомогательные классы для событий
    public static class SupplierEvent {
        private String eventType;
        private Supplier supplier;
        
        public SupplierEvent(String eventType, Supplier supplier) {
            this.eventType = eventType;
            this.supplier = supplier;
        }
        
        // Getters and setters
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public Supplier getSupplier() { return supplier; }
        public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    }

    public static class ContractEvent {
        private String eventType;
        private Contract contract;
        
        public ContractEvent(String eventType, Contract contract) {
            this.eventType = eventType;
            this.contract = contract;
        }
        
        // Getters and setters
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public Contract getContract() { return contract; }
        public void setContract(Contract contract) { this.contract = contract; }
    }

    public static class OrderRequest {
        private Long supplierId;
        private Long contractId;
        private String productId;
        private int quantity;
        private String reason;
        
        public OrderRequest(Long supplierId, Long contractId, String productId, int quantity, String reason) {
            this.supplierId = supplierId;
            this.contractId = contractId;
            this.productId = productId;
            this.quantity = quantity;
            this.reason = reason;
        }
        
        // Getters and setters
        public Long getSupplierId() { return supplierId; }
        public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
        public Long getContractId() { return contractId; }
        public void setContractId(Long contractId) { this.contractId = contractId; }
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class OrderEvent {
        private String eventType;
        private OrderRequest orderRequest;
        
        public OrderEvent(String eventType, OrderRequest orderRequest) {
            this.eventType = eventType;
            this.orderRequest = orderRequest;
        }
        
        // Getters and setters
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public OrderRequest getOrderRequest() { return orderRequest; }
        public void setOrderRequest(OrderRequest orderRequest) { this.orderRequest = orderRequest; }
    }

    public static class ContractExpirationEvent {
        private Long contractId;
        private Long supplierId;
        private LocalDate expirationDate;
        
        public ContractExpirationEvent(Long contractId, Long supplierId, LocalDate expirationDate) {
            this.contractId = contractId;
            this.supplierId = supplierId;
            this.expirationDate = expirationDate;
        }
        
        // Getters and setters
        public Long getContractId() { return contractId; }
        public void setContractId(Long contractId) { this.contractId = contractId; }
        public Long getSupplierId() { return supplierId; }
        public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
        public LocalDate getExpirationDate() { return expirationDate; }
        public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
    }
}