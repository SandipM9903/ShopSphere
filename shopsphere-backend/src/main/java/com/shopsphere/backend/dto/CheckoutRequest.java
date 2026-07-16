package com.shopsphere.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CheckoutRequest {

    @NotNull(message = "Shipping address is required")
    private UUID shippingAddressId;
}