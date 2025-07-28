package com.stockwise.inventory.service;

@Service
@Transactional
public class InventoryService {
    private final InventoryRepository repository;
    private final ProductRepository productRepository;
    private final HistoryRepository historyRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public InventoryService(
            InventoryRepository repository,
            ProductRepository productRepository,
            HistoryRepository historyRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.historyRepository = historyRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public InventoryItem getInventoryItem(UUID productId) {
        return repository.findByProductProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));
    }

    @Transactional
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

        // Автоматическое пополнение (можно вынести в отдельный метод)
        adjustStock(productId, quantity, "AUTO_REPLENISHMENT");
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
}

@Transactional
public List<InventoryItem> bulkUpdate(List<InventoryItem> updates) {
    return updates.stream()
            .map(update -> {
                InventoryItem item = repository.findById(update.getId())
                        .orElseThrow(() -> new InventoryNotFoundException(update.getId()));
                item.setQuantity(update.getQuantity());
                return repository.save(item);
            })
            .collect(Collectors.toList());
}

// Реализуем историю изменений
public List<InventoryHistory> getInventoryHistory(UUID productId) {
    return historyRepository.findByProductProductIdOrderByTimestampDesc(productId);
}

private void recordHistory(InventoryItem item, String action) {
    InventoryHistory history = new InventoryHistory();
    history.setInventoryItem(item);
    history.setAction(action);
    history.setQuantityChange(item.getQuantity());
    history.setTimestamp(Instant.now());
    historyRepository.save(history);
}

// Обновляем метод adjustStock
public InventoryItem adjustStock(Long itemId, int delta) {
    InventoryItem item = repository.findById(itemId)
            .orElseThrow(() -> new InventoryNotFoundException(itemId));

    int oldQuantity = item.getQuantity();
    int newQuantity = oldQuantity + delta;

    if (newQuantity < 0) {
        throw new InsufficientStockException("Cannot reduce stock below zero");
    }

    item.setQuantity(newQuantity);
    InventoryItem updated = repository.save(item);

    // Записываем историю
    recordHistory(item, "ADJUSTMENT");

    return updated;
}

public List<InventoryHistory> getInventoryHistory(UUID productId, int days) {
    Instant fromDate = Instant.now().minus(days, ChronoUnit.DAYS);

    return historyRepository.findByInventoryItemProductProductIdAndTimestampAfterOrderByTimestampDesc(
            productId,
            fromDate
    );
}

public List<InventoryItem> getLowStockItems() {
    return repository.findByQuantityLessThan(minThreshold);
}


//ML INTEGRATION
private final RestTemplate restTemplate;

public int calculateRequiredQuantity(InventoryItem item) {
    try {
        // Получение прогноза спроса
        PredictionResponse prediction = restTemplate.postForObject(
                "http://ml-service:5000/predict",
                new PredictionRequest(item.getProduct().getProductId()),
                PredictionResponse.class
        );

        // Расчет необходимого количества
        int safetyStock = (int) Math.ceil(prediction.getPrediction() * 1.2);
        return Math.max(0, safetyStock - item.getQuantity());

    } catch (Exception e) {
        // Fallback: простая эвристика
        return Math.max(0, item.getMinThreshold() - item.getQuantity());
    }
}

@Scheduled(fixedRate = 3600000) // Каждый час
public void autoReplenish() {
    List<InventoryItem> lowStockItems = getLowStockItems();

    for (InventoryItem item : lowStockItems) {
        int requiredQuantity = calculateRequiredQuantity(item);
        if (requiredQuantity > 0) {
            createReplenishmentOrder(
                    item.getProduct().getProductId(),
                    requiredQuantity
            );
        }
    }
}