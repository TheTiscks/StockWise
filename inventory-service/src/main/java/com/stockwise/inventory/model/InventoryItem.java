package com.stockwise.inventory.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inventoryId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;
    private Instant lastUpdated = Instant.now();

    // Конструкторы, геттеры, сеттеры
}