package com.stockwise.supplier.model;

@Entity
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Supplier supplier;

    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal terms;
    private int deliveryDays;

    // Конструкторы, геттеры, сеттеры
}