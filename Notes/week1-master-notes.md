# ShopSphere — Week 1 Master Notes
### Every Topic Covered This Week, With Interview Q&A

This is the complete Week 1 reference — one file, everything in it. Each topic follows: **What & Why → How It's Implemented → Interview Q&A**. From Week 2 onward, every week gets a file built the same way.

---

## Topic 1: Project Setup & Dependency Choices (`pom.xml`)

**What & why:** Every dependency was added with the *whole 24-week roadmap* in mind, not just Week 1 — Redis, Kafka, and WebSocket are already declared even though we won't touch them until Phase 2. This avoids "add a dependency, restart, re-test" churn later and means the pom.xml itself documents the project's full technical scope.

**Key choices:**
- `spring-boot-starter-parent` manages all transitive dependency versions — you don't manually pin Spring Security/JPA versions, avoiding version-conflict hell.
- `jjwt` (io.jsonwebtoken) chosen for JWT — a widely-used, actively maintained library over hand-rolling JWT encoding/signing yourself.
- Lombok marked `optional` and excluded from the final JAR via the `spring-boot-maven-plugin` config — it's a compile-time tool, not something that should ship in the runtime artifact.

**Interview Q&A:**

**Q: How does Spring Boot manage dependency versions across so many libraries without conflicts?**
A: Through `spring-boot-starter-parent` (or `spring-boot-dependencies` as a BOM if you're not using it as a parent), which pins compatible versions for the entire Spring ecosystem. You only specify a version for libraries outside that BOM (like `jjwt` here); everything else inherits a tested, compatible version automatically.

**Q: What's a "starter" in Spring Boot?**
A: A curated dependency bundle — e.g. `spring-boot-starter-web` pulls in Spring MVC, an embedded Tomcat server, and Jackson for JSON, all pre-wired to work together, instead of you assembling each piece and its compatible version manually.

---

## Topic 2: ERD Design Reasoning

**What & why:** The schema was designed by walking the user journey (register → browse → cart → checkout → address) rather than starting from "what tables do I need." This produces a schema that maps cleanly to actual API endpoints later, instead of an abstract data model disconnected from how it'll be used.

**Interview Q&A:**

**Q: Walk me through your schema design process.**
A: Started from user actions, not tables — each step in the journey (register, browse, add to cart, checkout) revealed which entities and relationships were needed. This keeps the schema aligned with real use cases instead of over-designing for hypothetical needs.

**Q: What would you do differently if this needed to support multiple currencies or countries?**
A: Add a `currency` field alongside every `BigDecimal` money field (never assume a single implicit currency), and consider whether `Address` needs country-specific validation rules rather than one generic shape.

---

## Topic 3: Cart vs. Order as Separate Entities

**What & why:** A cart is live and mutable — it should always reflect current prices. An order is a frozen financial record — it must never change even if the underlying product changes later.

**How implemented:** `CartItem` holds a live `@ManyToOne` reference to `Product` (reads current price at render time). `OrderItem` stores its own `productNameSnapshot` and `priceSnapshot` fields, frozen at the moment of purchase.

**Interview Q&A:**

**Q: Why not just reuse one "line item" table for both cart and order?**
A: Because they have opposite consistency requirements — a cart must always reflect *current* truth, an order must be an immutable record of what happened at a specific moment. Merging them either breaks pricing history (if order prices could silently update) or forces awkward status flags onto one overloaded table.

**Q: What happens if a product is deleted after being ordered?**
A: The `OrderItem` still has its own snapshot data (name, price), so the order record and any invoice/receipt generation still work correctly even if the `Product` row is gone or changed. (Note: in the current schema, `Product` deletion would need to be a soft-delete via the `active` flag rather than a hard delete, specifically to keep the `product_id` foreign key in `OrderItem` valid — worth calling out as a refinement.)

---

## Topic 4: Optimistic Locking (`@Version`)

**What & why:** Prevents lost updates when two concurrent requests try to modify the same row — specifically, two orders trying to deduct stock from the same product at the same time.

**How implemented:** `@Version private Long version;` on `Product`. Hibernate appends `WHERE version = ?` to every UPDATE and throws `OptimisticLockException` if another transaction already changed the row first.

**Interview Q&A:**

**Q: Explain optimistic locking with a concrete example.**
A: Two customers both try to buy the last unit of a product. Both read `stockQty = 1, version = 5`. Customer A's update succeeds, stock becomes 0, version becomes 6. Customer B's update tries to run `UPDATE ... WHERE id = ? AND version = 5` — but the version is now 6, so 0 rows match, and Hibernate throws `OptimisticLockException`. Customer B's request fails cleanly instead of both succeeding and overselling.

**Q: Optimistic vs. pessimistic locking — when would you choose each?**
A: Optimistic assumes conflicts are rare, lets both transactions proceed and checks only at commit — cheap, scales well under low contention. Pessimistic (`SELECT ... FOR UPDATE`) locks the row at read time, blocking others until released — safer under heavy contention (e.g. a flash sale on one item) but hurts throughput since requests queue up.

**Q: If `OptimisticLockException` is thrown, does Hibernate retry automatically?**
A: No — your service code has to explicitly catch it and decide: retry the operation, or surface a "someone beat you to it" error to the user. Locking only detects the conflict; it doesn't resolve it for you.

---

## Topic 5: RBAC Design — Enum vs. Table-Based Roles

**What & why:** Roles are fixed and known at compile time (`CUSTOMER`, `SELLER`, `ADMIN`), so an enum gives compile-time safety with no join-table overhead.

**How implemented:** `Set<RoleName>` via `@ElementCollection`, stored in a simple `user_roles` table, `EnumType.STRING` (not ordinal) so reordering the enum later can't silently corrupt stored data.

**Interview Q&A:**

**Q: Why enum instead of a full `Role` entity/table?**
A: Table-based roles earn their complexity when roles need to be created or edited by an admin at runtime, or when permissions need dynamic composition. For a small, fixed role set known at compile time, an enum gives type safety (typos become compile errors) without unnecessary join complexity.

**Q: How would you evolve this if you later needed fine-grained permissions (not just roles)?**
A: Introduce a `Permission` entity and a `Role`-to-`Permission` join table, so roles become named bundles of individual permissions (e.g. `ADMIN` = `MANAGE_PRODUCTS` + `VIEW_ALL_ORDERS` + ...), checked via permission strings in `@PreAuthorize` instead of role names directly.

---

## Topic 6: PostgreSQL Reserved Keywords (`user`, `order`)

**What & why:** `user` and `order` are reserved SQL keywords. Using them as table names forces every query to quote them or risk syntax errors.

**How implemented:** `@Table(name = "users")`, `@Table(name = "orders")` — pluralized names sidestep the conflict entirely (and are a common convention regardless).

**Interview Q&A:**

**Q: What would actually break if you named a table `order`?**
A: Every raw SQL query touching it — including ones Hibernate generates — would need to quote the identifier (`"order"`) or the database parser interprets it as the reserved keyword and throws a syntax error. It's an easy, avoidable footgun.

---

## Topic 7: `open-in-view: false`

**What & why:** By default, Spring Boot keeps the Hibernate session open for the entire HTTP request, letting lazy fields be fetched anywhere — even outside the service layer that's supposed to own that responsibility. This masks N+1 problems and blurs layer boundaries.

**Interview Q&A:**

**Q: What is Open Session in View, and why is it considered an anti-pattern?**
A: It keeps the DB session open across the full request lifecycle so lazy associations can be fetched from anywhere, including the controller or view layer. It's convenient short-term, but it hides N+1 query bugs (they still happen, just silently), obscures which layer owns data-fetching responsibility, and can hold DB connections open longer than necessary under load.

**Q: What happens in your app if you access a lazy field outside a transaction now that this is off?**
A: A `LazyInitializationException` — which is the framework surfacing the problem immediately during development, forcing you to either fetch what's needed inside the `@Transactional` service method or use `JOIN FETCH` for that specific case.

---

## Topic 8: `BigDecimal` for Money

**What & why:** `double`/`float` use binary floating point, which can't exactly represent most decimal fractions — small rounding errors compound into real discrepancies for financial data.

**Interview Q&A:**

**Q: Why `BigDecimal` instead of `double` for prices?**
A: `BigDecimal` represents values exactly via an unscaled integer and a scale, avoiding floating-point rounding errors. For money, exactness matters — tiny errors compound across many transactions into real accounting discrepancies. Tradeoff: slower and more verbose (no native operators — `.add()`, `.subtract()` instead of `+`/`-`), an acceptable cost for correctness.

---

## Topic 9: Entity Layer Annotations (Full Reference)

| Annotation | Purpose | What breaks without it |
|---|---|---|
| `@Entity` | Marks class as a persisted table | Class is invisible to JPA entirely |
| `@Table(name=...)` | Overrides default table name | Risk of reserved-keyword collisions |
| `@Id` | Marks primary key field | App fails to start (no identifier) |
| `@GeneratedValue(strategy = UUID)` | Auto-generates PK values | You'd have to supply IDs manually everywhere |
| `@Column(nullable, unique)` | DB-level constraints | Only app-level validation exists, race conditions can slip through |
| `@ManyToOne(fetch = LAZY)` | Maps relationship, defers loading | EAGER by default = unnecessary queries everywhere |
| `@OneToMany(mappedBy, cascade, orphanRemoval)` | Maps parent-to-children, propagates lifecycle | Deleting a parent leaves orphaned child rows |
| `@ElementCollection` + `@CollectionTable` | Maps simple-value collections without a full entity | Would need a full `Role` entity just to store enum values |
| `@Enumerated(STRING)` | Stores enum as name, not ordinal | Reordering the enum later corrupts existing data (if ORDINAL) |
| `@Version` | Enables optimistic locking | Concurrent updates can silently overwrite each other |
| `@PrePersist` | Lifecycle callback before first save | `createdAt` has to be set manually everywhere, easy to forget |
| Lombok `@NoArgsConstructor` | No-arg constructor for reflection | Hibernate can't instantiate the entity — startup/runtime failure |

**Interview Q&A:**

**Q: What's the difference between `CascadeType.ALL` and `orphanRemoval = true`?**
A: `CascadeType.ALL` propagates operations (persist, merge, remove, etc.) called on the parent down to its children — e.g., deleting a `Cart` deletes its `CartItem`s. `orphanRemoval` handles a different case: if a child is simply *removed from the collection* (not the parent deleted), it still gets deleted from the DB. You typically want both together for true parent-owned children.

**Q: Why did you use `@Getter`/`@Setter` individually instead of Lombok's `@Data`?**
A: `@Data` also generates `equals()`/`hashCode()`/`toString()`, which on JPA entities with lazy associations can trigger unwanted DB fetches, or cause issues with bidirectional relationships (infinite loops in `toString`/`equals`). Using individual annotations avoids generating methods you don't want on entities.

---

## Topic 10: Repository Layer — Spring Data JPA

**What & why:** `JpaRepository<Entity, IdType>` gives full CRUD + pagination with zero implementation code. Derived query methods let Spring generate simple queries from method names.

**Interview Q&A:**

**Q: How does Spring Data JPA generate a working query just from a method name like `findByCategoryIdAndActiveTrue`?**
A: At startup, Spring parses the method name against a defined grammar (`findBy`, `And`, `True`, `ContainingIgnoreCase`, etc.), maps each clause to an entity field, and builds the JPQL query dynamically via a runtime proxy implementing the repository interface.

**Q: Why return `Page<Product>` instead of `List<Product>` for a product listing endpoint?**
A: An unbounded list doesn't scale — as the catalog grows, that endpoint gets slower and eventually unusable. `Page<T>` carries pagination metadata (total elements, total pages, current page) alongside a bounded result set, which is the expected shape for any real listing API.

**Q: When would you abandon a derived query method in favor of `@Query`?**
A: Once the method name needs 4+ chained conditions, or involves aggregation/grouping/joins that don't map cleanly to the naming grammar — at that point explicit JPQL is more readable and maintainable.

---

## Topic 11: Application Configuration Decisions (`application.yml`)

**What & why:**
- `ddl-auto: update` — acceptable *only* during early active schema iteration; the note in the file flags switching to `validate` + a migration tool (Flyway/Liquibase) once the schema stabilizes.
- `show-sql: true` + `format_sql: true` — visibility into generated SQL during development, to catch N+1 issues and unexpected queries early.

**Interview Q&A:**

**Q: Why is `ddl-auto: update` risky, and what would you use in production?**
A: `update` lets Hibernate auto-modify your schema based on entity changes — convenient in development, but unpredictable and risky in production (it can silently drop columns or fail on incompatible changes). Production systems use `validate` (Hibernate checks the schema matches but never changes it) alongside an explicit migration tool like Flyway or Liquibase, where every schema change is a reviewed, version-controlled script.

---

## Topic 12: Architecture — Monolith vs. Microservices

**What & why:** Microservices solve a *team-scaling* problem (independent deployment across separate teams), not a purely technical one. A solo developer building a portfolio project doesn't have that organizational pressure, so adopting microservices means paying all the operational costs (distributed transactions, service discovery, N deployment pipelines, N databases) without the actual benefit they exist to provide.

**Decision made:** Modular monolith for the core system, with **one service (Notification) extracted** later as a bonus phase — giving real hands-on proof of inter-service messaging and independent deployment without redesigning the whole system around it.

**Interview Q&A:**

**Q: Why didn't you use microservices for this project?**
A: Microservices exist primarily to let independent teams deploy and scale their own services without coordinating with each other. As a solo developer, I don't have that organizational problem, so full microservices would mean absorbing all the complexity — network calls instead of method calls, distributed transactions via Saga patterns, service discovery, multiple deployment pipelines — without the benefit they're designed to solve. Instead, I built a modular monolith with clean domain boundaries and extracted one service (Notification) to demonstrate I understand how and when to make that split in practice.

**Q: What actually gets harder, concretely, if you go full microservices?**
A: Every DevOps layer multiplies — Docker (1 image → 5+), CI/CD (1 pipeline → 5+ independent ones), Kubernetes YAML (roughly 4 files → 20+), and databases (1 → potentially 3-4, since each service should own its data). The hardest new problem is distributed transactions: a single "place order" action that used to be one ACID transaction now spans multiple services/databases with no shared transaction, requiring a Saga pattern with compensating actions to maintain consistency.

**Q: When would microservices actually be the right call?**
A: When independent teams need to deploy on separate schedules, when different components have very different scaling needs (e.g. a search service needing to scale independently of an admin panel), or when you need fault isolation so one component failing doesn't take down the whole system.

---

## Topic 13: `.gitignore` and Repo Hygiene

**What & why:** Excludes `target/` (build artifacts), IDE-specific folders (`.idea`, `.vscode`), and any local-only config (`application-local.yml`) from version control — keeps the repo clean and avoids committing machine-specific or generated files.

**Interview Q&A:**

**Q: Why keep `application-local.yml` out of version control but still support it?**
A: It's a common pattern for developer-specific overrides (e.g., a different local DB password) that shouldn't be shared or committed, while `application.yml` holds the shared baseline config safe to commit.

---

## Master Self-Test (All Week 1 Topics)

1. Why does `OrderItem` store its own copy of product name and price instead of referencing `Product` directly for display?
2. Walk through exactly what happens, step by step, when two threads try to buy the last unit of a product with `@Version` in place.
3. When would a table-based `Role` entity be the better choice over an enum?
4. What breaks, specifically, if a table were named `order` in PostgreSQL?
5. What's the practical risk of leaving `open-in-view` at its default (`true`)?
6. Why `BigDecimal` and not `double` for prices — what specifically goes wrong with `double`?
7. What's the difference between `CascadeType.ALL` and `orphanRemoval = true`?
8. Explain, in your own words, why microservices weren't used here — and name one scenario where they would be the right call.
9. Why is `ddl-auto: update` acceptable now but wrong for production?
10. What's the actual mechanism behind Spring Data JPA turning a method name into a working query?

If more than 2-3 of these feel shaky, spend 15 minutes re-reading those sections before starting Week 2 — this is the foundation everything else builds on.
