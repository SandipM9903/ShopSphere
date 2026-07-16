package com.shopsphere.backend.exception;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException() {
        super("Cannot checkout with an empty cart");
    }
}