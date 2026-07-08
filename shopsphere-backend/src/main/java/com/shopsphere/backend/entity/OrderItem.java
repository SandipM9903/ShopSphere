package com.shopsphere.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Kept for traceability (e.g. "show me all orders containing this product"),
    // but NEVER read price/name from this at display time - always use the
    // snapshot fields below.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // --- Snapshot fields: frozen at the moment the order was placed ---
    @Column(nullable = false)
    private String productNameSnapshot;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;
    // -------------------------------------------------------------------

    @Column(nullable = false)
    private Integer quantity;
}
