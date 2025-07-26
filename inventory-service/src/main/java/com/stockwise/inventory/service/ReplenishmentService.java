@Service
public class ReplenishmentService {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ReplenishmentService(
            RestTemplateBuilder restTemplateBuilder,
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.restTemplate = restTemplateBuilder.build();
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRate = 3600000) // Каждый час
    public void checkAndReplenish() {
        List<InventoryItem> lowStockItems = getLowStockItems();

        for (InventoryItem item : lowStockItems) {
            double prediction = getDemandPrediction(item.getProductId());
            int required = calculateRequiredQuantity(item, prediction);

            if (required > 0) {
                createReplenishmentOrder(item, required);
            }
        }
    }

    private List<InventoryItem> getLowStockItems() {
        return jdbcTemplate.query(
                "SELECT * FROM inventory WHERE stock < min_threshold",
                (rs, rowNum) -> new InventoryItem(
                        rs.getObject("product_id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("stock"),
                        rs.getInt("min_threshold")
                )
        );
    }

    private double getDemandPrediction(UUID productId) {
        try {
            String url = "http://localhost:5000/predict";
            PredictionRequest request = new PredictionRequest(productId.toString(), getFeatures(productId));

            ResponseEntity<PredictionResponse> response = restTemplate.postForEntity(
                    url, request, PredictionResponse.class);

            return response.getBody().getPrediction();
        } catch (Exception e) {
            // Fallback: минимальный прогноз
            return 10;
        }
    }

    private int calculateRequiredQuantity(InventoryItem item, double prediction) {
        int safetyStock = (int) Math.ceil(prediction * 1.2); // +20% буфер
        return Math.max(0, safetyStock - item.getStock());
    }

    private void createReplenishmentOrder(InventoryItem item, int quantity) {
        ReplenishmentEvent event = new ReplenishmentEvent(
                UUID.randomUUID(),
                item.getProductId(),
                item.getName(),
                quantity,
                "AUTO"
        );

        kafkaTemplate.send("replenishment-orders", event.toJson());

        // Обновление запасов (предварительное)
        jdbcTemplate.update(
                "UPDATE inventory SET stock = stock + ? WHERE product_id = ?",
                quantity, item.getProductId()
        );
    }

    // Вспомогательные классы
    @Value
    private static class PredictionRequest {
        String product_id;
        Map<String, Object> features;
    }

    @Value
    private static class PredictionResponse {
        String product_id;
        double prediction;
    }
}