package com.shopsphere.backend.entity;

/**
 * RBAC roles for ShopSphere.
 * - CUSTOMER: browses/buys products
 * - SELLER: manages own product listings
 * - ADMIN: full access, approves sellers, manages catalog
 *
 * Kept as an enum (not a DB table) since roles are fixed and known
 * at compile time. If you later need admin-configurable roles/permissions
 * (e.g. a "Manager" role with custom scopes), migrate to a Role entity
 * with a permissions join table instead.
 */
public enum RoleName {
    CUSTOMER,
    SELLER,
    ADMIN
}
