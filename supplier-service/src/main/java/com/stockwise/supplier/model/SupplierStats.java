package com.stockwise.supplier.model;

public class SupplierStats {
    private final long totalSuppliers;
    private final long activeSuppliers;
    private final long inactiveSuppliers;
    private final long highRatedSuppliers;

    public SupplierStats(long totalSuppliers, long activeSuppliers, 
                        long inactiveSuppliers, long highRatedSuppliers) {
        this.totalSuppliers = totalSuppliers;
        this.activeSuppliers = activeSuppliers;
        this.inactiveSuppliers = inactiveSuppliers;
        this.highRatedSuppliers = highRatedSuppliers;
    }

    // Getters
    public long getTotalSuppliers() {
        return totalSuppliers;
    }

    public long getActiveSuppliers() {
        return activeSuppliers;
    }

    public long getInactiveSuppliers() {
        return inactiveSuppliers;
    }

    public long getHighRatedSuppliers() {
        return highRatedSuppliers;
    }

    // Вычисляемые поля
    public double getActiveRate() {
        return totalSuppliers > 0 ? (double) activeSuppliers / totalSuppliers * 100 : 0.0;
    }

    public double getHighRatedRate() {
        return totalSuppliers > 0 ? (double) highRatedSuppliers / totalSuppliers * 100 : 0.0;
    }
}
