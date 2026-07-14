package com.shopsphere.backend.dto;

import com.shopsphere.backend.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal price;
    private Integer stockQty;
    private boolean active;

    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getPrice(),
                product.getStockQty(),
                product.isActive()
        );
    }
}