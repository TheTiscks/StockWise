package com.stockwise.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class Product {
    @Id
    private UUID productId;
    private String name;
    private String category;

    // Конструкторы, геттеры, сеттеры
}