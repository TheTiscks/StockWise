package com.stockwise.inventory.service;

@Service
@Transactional
public class InventoryService {
    private final InventoryRepository repository;

    public InventoryItem adjustStock(Long itemId, int delta) {
        InventoryItem item = repository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found"));

        int newQuantity = item.getQuantity() + delta;
        if (newQuantity < 0) {
            throw new InsufficientStockException("Cannot reduce stock below zero");
        }

        item.setQuantity(newQuantity);
        return repository.save(item);
    }
}