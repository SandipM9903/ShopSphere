package com.shopsphere.backend.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int available) {
        super("Only " + available + " unit(s) of " + productName + " available");
    }
}