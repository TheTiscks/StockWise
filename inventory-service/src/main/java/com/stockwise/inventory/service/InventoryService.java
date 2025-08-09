package com.stockwise.inventory.service;

import com.stockwise.inventory.model.InventoryItem;
import com.stockwise.inventory.model.Product;
import com.stockwise.inventory.model.InventoryHistory;
import com.stockwise.inventory.repository.InventoryRepository;
import com.stockwise.inventory.repository.ProductRepository;
import com.stockwise.inventory.repository.HistoryRepository;
import com.stockwise.inventory.event.InventoryEvent;
import com.stockwise.inventory.event.ReplenishmentEvent;
import com.stockwise.inventory.exception.InsufficientStockException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class InventoryService {
    private final InventoryRepository repository;
    private final ProductRepository productRepository;
    private final HistoryRepository historyRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RestTemplate restTemplate;

    public InventoryService(
            InventoryRepository repository,
            ProductRepository productRepository,
            HistoryRepository historyRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            RestTemplate restTemplate
    ) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.historyRepository = historyRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "inventory", key = "#productId")
    public InventoryItem getInventoryItem(UUID productId) {
        return repository.findByProductProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));
    }

    @Cacheable(value = "products", key = "#productId")
    public Product getProduct(UUID productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
    }

    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    @CachePut(value = "inventory", key = "#productId")
    public InventoryItem adjustStock(UUID productId, int delta, String reason) {
        InventoryItem item = getInventoryItem(productId);
        int oldQuantity = item.getQuantity();
        int newQuantity = oldQuantity + delta;

        if (newQuantity < 0) {
            throw new InsufficientStockException(
                    "Cannot reduce stock below zero. Product: " + item.getProduct().getName()
            );
        }

        item.setQuantity(newQuantity);
        InventoryItem updatedItem = repository.save(item);

        // Запись в историю
        recordHistory(item, "ADJUSTMENT", delta, reason);

        // Отправка события в Kafka
        sendInventoryEvent(item, delta, reason);

        return updatedItem;
    }

    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public void createReplenishmentOrder(UUID productId, int quantity) {
        InventoryItem item = getInventoryItem(productId);

        // Создаем заказ на пополнение
        ReplenishmentOrder order = new ReplenishmentOrder();
        order.setProduct(item.getProduct());
        order.setQuantity(quantity);
        order.setStatus("PENDING");
        order.setCreatedAt(Instant.now());

        // Сохраняем и отправляем событие
        replenishmentOrderRepository.save(order);
        sendReplenishmentEvent(order);

        // Автоматическое пополнение
        adjustStock(productId, quantity, "AUTO_REPLENISHMENT");
    }

    @Cacheable(value = "predictions", key = "#productId")
    public double getDemandPrediction(UUID productId) {
        try {
            // Получение прогноза из ML-сервиса
            String url = "http://ml-service:5000/api/ml/predict";
            PredictionRequest request = new PredictionRequest(productId.toString());
            
            PredictionResponse response = restTemplate.postForObject(url, request, PredictionResponse.class);
            return response != null ? response.getPrediction() : 0.0;
            
        } catch (Exception e) {
            // Fallback: простая эвристика
            return 10.0;
        }
    }

    @Cacheable(value = "inventory", key = "'low_stock'")
    public List<InventoryItem> getLowStockItems() {
        return repository.findByQuantityLessThan(minThreshold);
    }

    @Scheduled(fixedRate = 3600000) // Каждый час
    public void autoReplenish() {
        List<InventoryItem> lowStockItems = getLowStockItems();

        for (InventoryItem item : lowStockItems) {
            double prediction = getDemandPrediction(item.getProduct().getProductId());
            int requiredQuantity = calculateRequiredQuantity(item, prediction);
            
            if (requiredQuantity > 0) {
                createReplenishmentOrder(
                        item.getProduct().getProductId(),
                        requiredQuantity
                );
            }
        }
    }

    private int calculateRequiredQuantity(InventoryItem item, double prediction) {
        int safetyStock = (int) Math.ceil(prediction * 1.2); // +20% буфер
        return Math.max(0, safetyStock - item.getQuantity());
    }

    private void recordHistory(InventoryItem item, String action, int delta, String reason) {
        InventoryHistory history = new InventoryHistory();
        history.setInventoryItem(item);
        history.setAction(action);
        history.setDelta(delta);
        history.setReason(reason);
        history.setTimestamp(Instant.now());
        historyRepository.save(history);
    }

    private void sendInventoryEvent(InventoryItem item, int delta, String reason) {
        InventoryEvent event = new InventoryEvent(
                item.getProduct().getProductId(),
                delta,
                reason
        );
        kafkaTemplate.send("inventory-updates", event.toJson());
    }

    private void sendReplenishmentEvent(ReplenishmentOrder order) {
        ReplenishmentEvent event = new ReplenishmentEvent(
                order.getOrderId(),
                order.getProduct().getProductId(),
                order.getQuantity(),
                order.getStatus()
        );
        kafkaTemplate.send("replenishment-orders", event.toJson());
    }

    // Вспомогательные классы для ML-интеграции
    public static class PredictionRequest {
        private String product_id;
        
        public PredictionRequest(String product_id) {
            this.product_id = product_id;
        }
        
        public String getProduct_id() {
            return product_id;
        }
    }

    public static class PredictionResponse {
        private String product_id;
        private double prediction;
        
        public String getProduct_id() {
            return product_id;
        }
        
        public double getPrediction() {
            return prediction;
        }
    }
}