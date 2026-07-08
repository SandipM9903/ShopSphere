package com.shopsphere.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    // BigDecimal, never double/float, for money - avoids floating point rounding errors
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Version field for optimistic locking - important once multi-threading
    // touches stock_qty in Phase 2 (prevents lost-update race conditions
    // when two orders try to deduct stock at the same time).
    @Version
    private Long version;

    @Column(nullable = false)
    @Builder.Default
    private Integer stockQty = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
