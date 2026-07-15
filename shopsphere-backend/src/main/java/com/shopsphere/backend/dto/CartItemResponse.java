package com.shopsphere.backend.dto;

import com.shopsphere.backend.entity.CartItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CartItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;

    public static CartItemResponse fromEntity(CartItem cartItem) {
        BigDecimal unitPrice = cartItem.getProduct().getPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getProduct().getId(),
                cartItem.getProduct().getName(),
                unitPrice,
                cartItem.getQuantity(),
                subtotal
        );
    }
}