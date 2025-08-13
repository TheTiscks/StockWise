package com.stockwise.order.model;

public class OrderStats {
    private final long totalOrders;
    private final long pendingOrders;
    private final long confirmedOrders;
    private final long deliveredOrders;
    private final long cancelledOrders;

    public OrderStats(long totalOrders, long pendingOrders, long confirmedOrders, 
                     long deliveredOrders, long cancelledOrders) {
        this.totalOrders = totalOrders;
        this.pendingOrders = pendingOrders;
        this.confirmedOrders = confirmedOrders;
        this.deliveredOrders = deliveredOrders;
        this.cancelledOrders = cancelledOrders;
    }

    // Getters
    public long getTotalOrders() {
        return totalOrders;
    }

    public long getPendingOrders() {
        return pendingOrders;
    }

    public long getConfirmedOrders() {
        return confirmedOrders;
    }

    public long getDeliveredOrders() {
        return deliveredOrders;
    }

    public long getCancelledOrders() {
        return cancelledOrders;
    }

    // Вычисляемые поля
    public double getDeliveryRate() {
        return totalOrders > 0 ? (double) deliveredOrders / totalOrders * 100 : 0.0;
    }

    public double getCancellationRate() {
        return totalOrders > 0 ? (double) cancelledOrders / totalOrders * 100 : 0.0;
    }

    public long getActiveOrders() {
        return pendingOrders + confirmedOrders;
    }
}
