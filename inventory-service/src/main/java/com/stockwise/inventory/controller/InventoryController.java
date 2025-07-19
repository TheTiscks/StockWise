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


@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;

    public InventoryController(KafkaTemplate<String, String> kafkaTemplate, JdbcTemplate jdbcTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/adjust")
    public ResponseEntity<?> adjustInventory(
            @RequestBody InventoryAdjustmentRequest request
    ) {
        // 1. Обновляем БД
        jdbcTemplate.update(
                "UPDATE inventory SET stock = stock + ? WHERE product_id = ?",
                request.getDelta(), request.getProductId()
        );

        // 2. Отправляем событие в Kafka
        InventoryEvent event = new InventoryEvent(
                request.getProductId(),
                request.getDelta(),
                "MANUAL_ADJUSTMENT"
        );
        kafkaTemplate.send("inventory-updates", event.toJson());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryItem> getInventory(@PathVariable UUID productId) {
        InventoryItem item = jdbcTemplate.queryForObject(
                "SELECT * FROM inventory WHERE product_id = ?",
                new Object[]{productId},
                (rs, rowNum) -> new InventoryItem(
                        rs.getObject("product_id", UUID.class),
                        rs.getInt("stock")
                )
        );
        return ResponseEntity.ok(item);
    }

    // DTO классы
    public record InventoryAdjustmentRequest(UUID productId, int delta) {}
    public record InventoryItem(UUID productId, int stock) {}
}