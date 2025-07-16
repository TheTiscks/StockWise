package com.stockwise.supplier.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    private String contractNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal terms;
    private int deliveryDays;
    private String paymentConditions;
    private boolean isActive = true;

    public Contract() {
    }

    public Contract(String contractNumber, LocalDate startDate, LocalDate endDate,
                    BigDecimal terms, int deliveryDays, String paymentConditions) {
        this.contractNumber = contractNumber;
        this.startDate = startDate;
        this.endDate = endDate;
        this.terms = terms;
        this.deliveryDays = deliveryDays;
        this.paymentConditions = paymentConditions;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getTerms() {
        return terms;
    }

    public void setTerms(BigDecimal terms) {
        this.terms = terms;
    }

    public int getDeliveryDays() {
        return deliveryDays;
    }

    public void setDeliveryDays(int deliveryDays) {
        this.deliveryDays = deliveryDays;
    }

    public String getPaymentConditions() {
        return paymentConditions;
    }

    public void setPaymentConditions(String paymentConditions) {
        this.paymentConditions = paymentConditions;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isCurrentlyActive() {
        LocalDate today = LocalDate.now();
        return isActive &&
                !today.isBefore(startDate) &&
                !today.isAfter(endDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contract contract = (Contract) o;
        return Objects.equals(id, contract.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Contract{" +
                "id=" + id +
                ", contractNumber='" + contractNumber + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}