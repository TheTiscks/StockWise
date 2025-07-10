package com.stockwise.inventory.controller;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;

    @PostMapping
    public ResponseEntity<InventoryItem> addItem(@RequestBody InventoryItem item) {
        return ResponseEntity.ok(service.addInventoryItem(item));
    }

    @PutMapping("/{id}/adjust")
    public ResponseEntity<InventoryItem> adjustStock(
            @PathVariable Long id,
            @RequestParam int delta) {
        return ResponseEntity.ok(service.adjustStock(id, delta));
    }
}