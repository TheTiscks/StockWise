package com.stockwise.inventory.service;

@Service
@Transactional
public class InventoryService {
    private final InventoryRepository repository;
    private final ProductRepository productRepository;

    public InventoryItem addInventoryItem(InventoryItem item) {
        // Проверяем существование продукта
        UUID productId = item.getProduct().getProductId();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        item.setProduct(product);

        return repository.save(item);
    }

    public InventoryItem adjustStock(Long itemId, int delta) {
        InventoryItem item = repository.findById(itemId)
                .orElseThrow(() -> new InventoryNotFoundException(itemId));

        int newQuantity = item.getQuantity() + delta;
        if (newQuantity < 0) {
            throw new InsufficientStockException("Cannot reduce stock below zero");
        }

        item.setQuantity(newQuantity);
        return repository.save(item);
    }

    @Transactional
    public void transferStock(Long fromId, Long toId, int quantity) {
        // Уменьшаем количество на складе-источнике
        adjustStock(fromId, -quantity);

        // Увеличиваем количество на целевом складе
        adjustStock(toId, quantity);
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