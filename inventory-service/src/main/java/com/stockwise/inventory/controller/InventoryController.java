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

    @PostMapping("/adjust")
    public ResponseEntity<InventoryAdjustmentResponse> adjustInventory(
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        try {
            // 1. Валидация запроса
            if (request.getDelta() == 0) {
                return ResponseEntity.badRequest().body(
                        new InventoryAdjustmentResponse("Delta cannot be zero", null)
                );
            }

            // 2. Обновление запасов
            int updatedStock = inventoryService.adjustStock(
                    request.getProductId(),
                    request.getDelta(),
                    request.getReason()
            );

            // 3. Отправка события в Kafka
            InventoryEvent event = new InventoryEvent(
                    request.getProductId(),
                    request.getDelta(),
                    request.getReason()
            );
            kafkaTemplate.send("inventory-updates", event.toJson());

            // 4. Аудит операции
            auditService.logAdjustment(
                    request.getProductId(),
                    request.getDelta(),
                    request.getReason(),
                    "MANUAL"
            );

            return ResponseEntity.ok(new InventoryAdjustmentResponse(
                    "Inventory adjusted successfully",
                    new InventoryItem(request.getProductId(), updatedStock)
            ));
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new InventoryAdjustmentResponse("Product not found", null)
            );
        } catch (InsufficientStockException ex) {
            return ResponseEntity.badRequest().body(
                    new InventoryAdjustmentResponse("Insufficient stock", null)
            );
        }
    }

    @PostMapping("/replenish")
    public ResponseEntity<ReplenishmentResponse> replenishInventory(
            @Valid @RequestBody ReplenishmentRequest request
    ) {
        try {
            ReplenishmentOrder order = replenishmentService.createOrder(
                    request.getProductId(),
                    request.getQuantity(),
                    "MANUAL"
            );

            return ResponseEntity.ok(new ReplenishmentResponse(
                    "Replenishment order created",
                    order.getOrderId(),
                    order.getQuantity(),
                    order.getStatus()
            ));
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ReplenishmentResponse("Product not found", null, 0, "FAILED")
            );
        }
    }

    @GetMapping("/reports/demand")
    public ResponseEntity<Resource> generateDemandReport(
            @RequestParam UUID productId,
            @RequestParam(defaultValue = "30") int days
    ) {
        try {
            // 1. Генерация PDF
            byte[] pdfContent = reportService.generateDemandReport(productId, days);

            // 2. Создание ресурса для ответа
            ByteArrayResource resource = new ByteArrayResource(pdfContent);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=demand_report_" + productId + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfContent.length)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // DTO классы
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAdjustmentRequest {
        @NotNull
        private UUID productId;

        @Min(-1000)
        @Max(1000)
        private int delta;

        @NotBlank
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAdjustmentResponse {
        private String message;
        private InventoryItem item;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplenishmentRequest {
        @NotNull
        private UUID productId;

        @Min(1)
        @Max(1000)
        private int quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplenishmentResponse {
        private String message;
        private UUID orderId;
        private int quantity;
        private String status;
    }
}

    // DTO классы
    public record InventoryAdjustmentRequest(UUID productId, int delta) {}
    public record InventoryItem(UUID productId, int stock) {}
}