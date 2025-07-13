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