# ShopSphere — Week 1 Notes
### Foundation: ERD Design, Entity Modeling, Repository Layer

---

## 1. Why Cart and Order Are Separate Tables (Not Reused)

**The problem it solves:** A cart is *mutable and live* — prices should always reflect the current catalog. An order is a *legal/financial record* — once placed, it must never silently change even if the product's price changes tomorrow.

**How it's implemented:**
```java
// CartItem — always points live to Product
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_id", nullable = false)
private Product product;   // read price from product.getPrice() at render time

// OrderItem — freezes the values at purchase time
@Column(nullable = false)
private String productNameSnapshot;

@Column(nullable = false, precision = 10, scale = 2)
private BigDecimal priceSnapshot;
```

**Common interview question:** *"Why not just add a `quantity` field to a cart/order-shared table?"*
**Answer:** Because a cart and an order have fundamentally different consistency requirements. A cart's job is to always reflect *current* truth (price, availability). An order's job is to be an immutable receipt of what happened at a specific moment — required for refunds, disputes, invoicing, and audits. Conflating them either breaks pricing history or forces awkward "is this a cart or an order" flags on one table.

**Gotcha to remember:** Even the reference to `Product` in `OrderItem` should only be used for traceability ("show all orders containing this product") — never for displaying price/name. That's a bug waiting to happen if a future teammate (or future you) forgets and reads `orderItem.getProduct().getPrice()` instead of `orderItem.getPriceSnapshot()`.

---

## 2. Optimistic Locking with `@Version`

**The problem it solves:** Two customers hit "buy" on the last unit of a product at the same instant. Without protection, both requests can read `stockQty = 1`, both decrement it, and you oversell.

**How it's implemented:**
```java
@Entity
public class Product {
    @Version
    private Long version;

    @Column(nullable = false)
    private Integer stockQty;
}
```
Hibernate automatically adds a `WHERE version = ?` clause to every `UPDATE`. If another transaction already bumped the version, your update affects 0 rows and Hibernate throws `OptimisticLockException` — the second request fails cleanly instead of corrupting data.

**Common interview question:** *"What's the difference between optimistic and pessimistic locking, and when would you use each?"*
**Answer:** Optimistic locking assumes conflicts are rare — it lets both transactions proceed and only checks for conflict at commit time (via the version column). It's cheap and scales well under low contention. Pessimistic locking (`SELECT ... FOR UPDATE`) actually locks the row at read time, blocking other transactions until the first one finishes — safer under high contention but hurts throughput since transactions queue up. For a "flash sale" scenario with extreme contention on one product, pessimistic locking or a Redis-based counter is often better than optimistic locking, which would cause a storm of failed retries.

**Gotcha to remember:** Optimistic locking only *detects* the conflict — your service code still needs to catch `OptimisticLockException` and decide what to do (retry, or tell the user "someone beat you to it, please try again"). It doesn't resolve the conflict for you.

---

## 3. RBAC: Enum vs. Table-Based Roles

**The problem it solves:** Different users need different permissions (CUSTOMER can buy, SELLER can list products, ADMIN can manage everything). RBAC needs *some* structured way to represent "what can this user do."

**How it's implemented (the choice made this week):**
```java
public enum RoleName { CUSTOMER, SELLER, ADMIN }

@Entity
public class User {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    private Set<RoleName> roles = new HashSet<>();
}
```

**Common interview question:** *"Why an enum instead of a `Role` entity/table?"*
**Answer:** Enum-based roles are the right call when the set of roles is small, fixed, and known at compile time — you get compile-time safety (typos become compile errors, not runtime bugs) and no extra join complexity. A table-based `Role` entity (with a `Permission` join table) is the right call when roles need to be created/edited by an admin at runtime, or when permissions need to be composed dynamically (e.g. a custom "Regional Manager" role with a hand-picked set of permissions). Most real products start with enum-based RBAC and only migrate to table-based roles when a genuine business need for dynamic roles shows up.

**Gotcha to remember:** `fetch = FetchType.EAGER` on the roles collection is deliberate here, not an oversight — roles are checked on nearly every authenticated request (via `@PreAuthorize`), so lazy-loading them would just cause an extra query almost every time anyway, plus risk of `LazyInitializationException` outside a transaction.

---

## 4. PostgreSQL Reserved Keywords: `user` and `order`

**The problem it solves:** Nothing conceptual — this is a "gotcha you learn by hitting it once" category, and now you don't have to hit it.

**How it's implemented:**
```java
@Table(name = "users")   // NOT "user"
@Table(name = "orders")  // NOT "order"
```

**Why it matters:** Both `user` and `order` are reserved words in standard SQL / PostgreSQL. If you name a table `user`, every query touching it needs to be quoted (`"user"`) or it fails with a syntax error, because the database parser interprets `user` as the keyword, not your table name. Pluralizing table names (`users`, `orders`, `products`) sidesteps this entirely and is also just a common convention.

**Gotcha to remember:** This applies to column names too, not just table names (`order`, `group`, `check`, `table` are all reserved words). When naming anything in SQL, it's worth a 5-second mental check against common reserved word lists.

---

## 5. `open-in-view: false`

**The problem it solves:** By default, Spring Boot keeps the Hibernate session open for the *entire HTTP request* (including view rendering), which lets lazy-loaded fields be fetched anywhere in the request — even in a controller or view template, far from the `@Transactional` service method that should own that responsibility.

**How it's implemented:**
```yaml
spring:
  jpa:
    open-in-view: false
```

**Common interview question:** *"What is the Open Session in View pattern and why is it considered an anti-pattern?"*
**Answer:** OSIV keeps the database session open across the whole request lifecycle so that lazy associations can be lazily fetched anywhere, even in the presentation layer. It's convenient short-term but causes real problems: it hides N+1 query bugs (they still happen, just silently, since the session is always available to satisfy them), it makes it unclear which layer owns data-fetching responsibility, and it can hold DB connections open longer than necessary, hurting throughput under load. Turning it off forces lazy-loading issues to surface immediately during development — in the service layer, where they're supposed to be handled — rather than being masked until they intermittently break in production.

**Gotcha to remember:** With this off, if you try to access a lazy field (e.g. `order.getItems()`) outside of a transactional method, you'll get a `LazyInitializationException`. That's the framework doing its job — it means you need to either fetch what you need inside the `@Transactional` service method, or use a `JOIN FETCH` query to get it eagerly for that specific use case.

---

## 6. Spring Data JPA — Derived Query Methods

**The problem it solves:** Writing boilerplate SQL/JPQL for simple lookups (find by field X, find by field X and Y) is repetitive.

**How it's implemented:**
```java
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findByCategoryIdAndActiveTrue(UUID categoryId, Pageable pageable);
    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String keyword, Pageable pageable);
}
```
Spring Data JPA parses the method name at startup and generates the query automatically — no implementation needed.

**Common interview question:** *"How does Spring Data JPA know what query to run just from a method name?"*
**Answer:** At application startup, Spring Data JPA parses the method name using a defined grammar (`findBy`, `And`, `Or`, `ContainingIgnoreCase`, `OrderBy`, etc.), maps each clause to the corresponding entity field, and generates the JPQL/SQL query dynamically via a proxy implementation of the repository interface. For queries too complex for the naming convention, you drop down to `@Query` with JPQL or native SQL.

**Gotcha to remember:** Derived query methods are great for simple lookups, but if the method name starts getting long and hard to read (4+ conditions), that's usually a signal to switch to an explicit `@Query` annotation for readability — the naming convention isn't meant to replace JPQL entirely.

---

## 7. Money as `BigDecimal`, Never `double`/`float`

**The problem it solves:** `double` and `float` use binary floating-point representation, which cannot exactly represent most decimal fractions (e.g. `0.1 + 0.2 != 0.3` in floating point). For money, even tiny rounding errors compound and cause real accounting discrepancies.

**How it's implemented:**
```java
@Column(nullable = false, precision = 10, scale = 2)
private BigDecimal price;
```

**Common interview question:** *"Why use `BigDecimal` instead of `double` for currency values?"*
**Answer:** `BigDecimal` represents numbers exactly using an unscaled integer value and a scale, avoiding the binary floating-point rounding errors inherent to `double`/`float`. For financial data, exactness matters — even fractions-of-a-cent errors, multiplied across thousands of transactions, become real discrepancies in reporting and reconciliation. The tradeoff is `BigDecimal` is slower and more verbose to work with (no native `+`/`-` operators — you use `.add()`, `.subtract()`), which is an acceptable cost for correctness in financial code.

---

## Quick-Reference Summary Table

| Topic | One-line takeaway |
|---|---|
| Cart vs Order tables | Cart = live/mutable, Order = frozen historical record |
| `@Version` optimistic locking | Detects concurrent update conflicts; you still handle the retry logic |
| Enum vs table RBAC | Enum for fixed/small role sets; table for dynamic, admin-configurable roles |
| Reserved SQL keywords | Pluralize table names (`users`, `orders`) to dodge `user`/`order` keyword conflicts |
| `open-in-view: false` | Surfaces lazy-loading issues early instead of masking them until production |
| Derived query methods | Spring parses method names into queries automatically; switch to `@Query` when names get unwieldy |
| `BigDecimal` for money | Exact decimal representation; avoids floating-point rounding errors |

---

## Self-Test Before Moving to Week 2
Try answering these out loud, without looking back at the notes:
1. Why does `OrderItem` store its own copy of the product name and price?
2. Walk through what happens if two threads try to buy the last item in stock at the same time, with `@Version` in place.
3. When would you choose a `Role` table over an enum for RBAC?
4. What actually breaks if you name a table `order` in PostgreSQL?
5. What's the practical downside of leaving `open-in-view` at its default (`true`)?

If any of these feel shaky, that's the one to re-read before Week 2 — don't move forward on a wobbly foundation.
