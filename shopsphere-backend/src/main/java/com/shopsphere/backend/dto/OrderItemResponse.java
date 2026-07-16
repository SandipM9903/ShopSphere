package com.shopsphere.backend.dto;

import com.shopsphere.backend.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrderItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;

    public static OrderItemResponse fromEntity(OrderItem orderItem) {
        BigDecimal subtotal = orderItem.getPriceSnapshot()
                .multiply(BigDecimal.valueOf(orderItem.getQuantity()));

        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProduct().getId(),
                orderItem.getProductNameSnapshot(),   // ← snapshot, not live
                orderItem.getPriceSnapshot(),          // ← snapshot, not live
                orderItem.getQuantity(),
                subtotal
        );
    }
}