package com.shopsphere.backend.entity;

public enum OrderStatus {
    PENDING,      // created, payment not yet confirmed
    CONFIRMED,    // payment succeeded, stock deducted
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED        // payment failed or stock deduction failed
}
