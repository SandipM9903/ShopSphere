package com.shopsphere.backend.dto;

import com.shopsphere.backend.entity.Cart;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class CartResponse {

    private UUID id;
    private List<CartItemResponse> items;
    private BigDecimal grandTotal;

    public static CartResponse fromEntity(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(CartItemResponse::fromEntity)
                .collect(Collectors.toList());

        BigDecimal grandTotal = itemResponses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), itemResponses, grandTotal);
    }
}