package com.shopsphere.backend.dto;

import com.shopsphere.backend.entity.Order;
import com.shopsphere.backend.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private AddressResponse shippingAddress;
    private List<OrderItemResponse> items;
    private Instant createdAt;

    public static OrderResponse fromEntity(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(OrderItemResponse::fromEntity)
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                AddressResponse.fromEntity(order.getShippingAddress()),
                itemResponses,
                order.getCreatedAt()
        );
    }
}