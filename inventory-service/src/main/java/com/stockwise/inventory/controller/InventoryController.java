package com.stockwise.inventory.controller;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;

    @PostMapping
    public ResponseEntity<InventoryItem> addItem(@RequestBody InventoryItem item) {
        return ResponseEntity.ok(service.addInventoryItem(item));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryItem> getItem(@PathVariable Long id) {
        return ResponseEntity.ok(service.getItemById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryItem> updateItem(
            @PathVariable Long id,
            @RequestBody InventoryItem item) {
        return ResponseEntity.ok(service.updateItem(id, item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    // Транзакционные операции
    @PutMapping("/{id}/adjust")
    public ResponseEntity<InventoryItem> adjustStock(
            @PathVariable Long id,
            @RequestParam int delta) {
        return ResponseEntity.ok(service.adjustStock(id, delta));
    }

    @PutMapping("/transfer")
    public ResponseEntity<Void> transferStock(
            @RequestParam Long fromId,
            @RequestParam Long toId,
            @RequestParam int quantity) {
        service.transferStock(fromId, toId, quantity);
        return ResponseEntity.ok().build();
    }
}