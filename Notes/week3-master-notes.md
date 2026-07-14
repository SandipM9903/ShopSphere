# ShopSphere — Week 3 Master Notes (Complete)
### Category & Product CRUD — Every Topic Covered, With Interview Q&A

**Note:** This supersedes the earlier "Part 1" file — that one covered only Category (Steps 1–4) while Product was still in progress. This is the complete Week 3 file, Category + Product together, Steps 1–8.

---

## Topic 1: Generic vs. Specific Exceptions

**What & why:** `ResourceNotFoundException` is generic (`resourceName`, `id` as parameters) rather than separate `CategoryNotFoundException`/`ProductNotFoundException` classes. "Not found by ID" is a repeating structural pattern, not a distinct business rule — the same class now serves both entities unchanged, exactly as designed.

**Interview Q&A:**

**Q: How do you decide whether an exception should be generic and reusable versus specific and named?**
A: Ask whether the failure represents a repeating structural pattern (any entity can fail to be found by ID) versus a distinct business rule tied to one specific concept (a duplicate email is a rule specific to registration, not a pattern other entities share). Generic exceptions reduce class-count bloat for patterns; specific exceptions make business rules self-documenting.

**Q: This exception ended up used by both Category and Product without any changes. Was that planned or a lucky coincidence?**
A: Planned — that reusability was the entire justification for making it generic in Week 3 Step 1, before Product even existed. Recognizing "not found by ID" as a pattern that would repeat across future entities is exactly the kind of forward-looking design decision worth explaining when asked about it.

---

## Topic 2: The DTO Static Factory Method Pattern (`fromEntity`)

**What & why:** Both `CategoryResponse.fromEntity()` and `ProductResponse.fromEntity()` centralize entity-to-DTO mapping in one static method per DTO class, called from every service method that returns that shape.

**Interview Q&A:**

**Q: `ProductResponse.fromEntity()` does more work than `CategoryResponse.fromEntity()` — why?**
A: `ProductResponse` denormalizes data from a *related* entity (`Category`) into the flat response shape (`categoryId`, `categoryName`), not just its own fields. That means `fromEntity` for `Product` has to reach across the `@ManyToOne` relationship (`product.getCategory().getName()`), which `Category`'s version never needed to do, since `Category` has no such relationship to flatten.

---

## Topic 3: Where RBAC Belongs — Service Layer, Not Controller

**What & why:** `@PreAuthorize` sits on service methods in both `CategoryService` and `ProductService`, never on the controllers. Business rules travel with business logic regardless of which entry point calls it.

**Interview Q&A:**

**Q: Why put `@PreAuthorize` on the service layer instead of the controller?**
A: If a second controller, an internal tool, or a batch job ever called these service methods directly, the role rule still applies automatically — it can't be bypassed by a caller that forgot to re-check. The controller in this design has zero explicit security code; both request-level and role-level enforcement happen elsewhere, deliberately.

**Q: Category is ADMIN-only for every write operation, but Product allows SELLER *and* ADMIN for create/update, ADMIN-only for delete. Why the difference?**
A: These represent genuinely different business rules, not an inconsistency. Managing the category taxonomy of an entire store is treated as an admin-level structural decision. Listing and editing individual products is a normal seller workflow in any real marketplace — sellers need to manage their own inventory. Removing a product entirely (even as a soft delete) is treated as more sensitive here, restricted to ADMIN. Worth being honest that a complete implementation would also need an ownership check — confirming a SELLER only edits *their own* products — which isn't built yet, since `Product` has no `sellerId`/`ownerId` field.

---

## Topic 4: Hibernate Dirty Checking

**What & why:** Both `updateCategory()` and `updateProduct()` — and `deleteProduct()`'s soft-delete flag flip — modify a loaded entity's fields inside a `@Transactional` method with no explicit `.save()` call, and the changes still persist.

**Interview Q&A:**

**Q: Explain dirty checking using the `deleteProduct` soft-delete example specifically.**
A: `deleteProduct` loads the `Product` (making it a Hibernate-managed entity within the active transaction), then calls `product.setActive(false)`. Hibernate tracks that field change against the entity's original loaded state, and when the transaction commits, automatically issues the corresponding `UPDATE` statement — no explicit `save()` needed. This is the same mechanism as `updateCategory`, just changing one boolean field instead of several string fields.

---

## Topic 5: Thin Controllers & Automatic Type Conversion

**What & why:** Neither `CategoryController` nor `ProductController` contains business logic — every method validates input, delegates to one service call, and wraps the result in the right HTTP status. `@PathVariable UUID id` and `Pageable pageable` both demonstrate Spring automatically converting raw request data into typed objects.

**Interview Q&A:**

**Q: `ProductController` uses `Pageable pageable` as a bare method parameter with no annotation. How does Spring know to populate it from query parameters?**
A: This is Spring Data Web support — when a controller method parameter is of type `Pageable`, Spring automatically parses recognized query parameters (`page`, `size`, `sort`) from the request URL and constructs the object, with sensible defaults (page 0, size 20) if none are provided. No manual parsing or annotation is required; it's convention-based binding tied specifically to that parameter type.

---

## Topic 6: Choosing the Right REST Status Code

**What & why:** `204 No Content` for deletes (both Category and Product), `201 Created` for creation, `200 OK` for reads/updates, `404 Not Found` for missing resources — each chosen deliberately, not defaulted to 200 everywhere.

**Interview Q&A:**

**Q: Why 204 for delete instead of 200 with an empty body?**
A: 204 explicitly communicates "succeeded, and there is intentionally nothing to return" — more precise than 200, which implies a response body a client might expect to parse.

---

## Topic 7: Two-Layer Authorization — `SecurityConfig` (Coarse) + `@PreAuthorize` (Fine)

**What & why:** `SecurityConfig`'s `HttpMethod.GET`-scoped `permitAll()` on `/api/products/**` and `/api/categories/**` covers all read endpoints for both entities in one rule, while write operations fall through to `.anyRequest().authenticated()` and then get their specific role checked by `@PreAuthorize`.

**Interview Q&A:**

**Q: `ProductController` has three different GET endpoints (`/`, `/category/{id}`, `/search`) plus `/{id}`. Did each need its own `SecurityConfig` rule?**
A: No — all of them fall under the `/api/products/**` wildcard pattern already established in `SecurityConfig`, since the rule matches on path prefix and HTTP method (`GET`), not exact path. This is a good example of why choosing wildcard patterns thoughtfully at the config level saves needing a new rule every time a new endpoint gets added under the same resource.

---

## Topic 8: Pagination — Applied Where the Data Shape Actually Needs It

**What & why:** Category skips pagination (`List<CategoryResponse>`); Product requires it everywhere (`Page<ProductResponse>`) — a deliberate difference based on realistic data volume, not a blanket rule applied uniformly.

**Interview Q&A:**

**Q: Why does Product need three separate paginated endpoints (all, by category, search) instead of one?**
A: Each maps to a distinctly-purposed repository query (`findAll`, `findByCategoryIdAndActiveTrue`, `findByNameContainingIgnoreCaseAndActiveTrue`), and separate endpoints keep each controller method single-purpose rather than needing conditional logic to figure out which filter combination a client intended. The alternative — one endpoint with optional query params — is also valid; the point is having a deliberate reason for the choice made, not that one approach is universally correct.

---

## Topic 9: UUID — Why It's Used

**What & why:** Covered as a deep-dive topic this week; formally captured here.

**Interview Q&A:**

**Q: Why UUID instead of auto-incrementing integers?**
A: Four reasons: generable in application code before insert; doesn't leak sequential business information (guards against IDOR-style enumeration); allows coordination-free ID generation across distributed systems/services; supports offline or optimistic-UI scenarios where a client needs a valid ID before the server confirms anything. Tradeoff: larger storage footprint and less efficient B-tree index insertion than sequential integers — negligible at this project's scale, relevant at massive scale (where UUID v7 is sometimes used instead, to stay roughly time-ordered).

---

## Topic 10: Testing Methodology — Same Endpoint, Multiple Outcomes by Role

**What & why:** Category's test plan validates the *same* endpoint under three different auth states expecting three different results — proof the authorization logic itself is correct, not just that the feature exists.

**Interview Q&A:**

**Q: Why test the same endpoint multiple times with different roles rather than confirming it works once?**
A: A single passing "it works" test says nothing about whether authorization is correctly enforced — a bug that let a CUSTOMER create a category would still pass a test that only checks the ADMIN case. Testing every relevant auth state against the identical request is what actually validates the authorization rule, not just the feature's existence.

---

## Topic 11: Class-Level `@Transactional(readOnly = true)` with Method-Level Overrides

**What & why:** `ProductService` is annotated `@Transactional(readOnly = true)` at the class level — the default for every method — with individual write methods (`createProduct`, `updateProduct`, `deleteProduct`) overriding that default with their own plain `@Transactional`.

**Interview Q&A:**

**Q: What's the benefit of `readOnly = true`, and why apply it at the class level instead of on each read method individually?**
A: `readOnly = true` lets Hibernate skip dirty-checking overhead on entities loaded within that transaction, since you're telling it upfront nothing will be modified — a genuine (if modest) performance optimization, and some databases can apply additional read-optimizations underneath. Applying it at the class level makes "read-only by default" the baseline assumption for the whole service, requiring write methods to explicitly opt out — which also makes those write methods easier to spot at a glance, since they're the ones with a *different* annotation than their neighbors.

**Q: Why didn't `CategoryService` use this same pattern in Step 3?**
A: This is worth noticing as a genuine inconsistency between the two, not a deliberate distinction — a good candidate to go back and align once Week 3 is fully wrapped up, applying the same class-level `readOnly = true` pattern to `CategoryService` for consistency.

---

## Topic 12: Validating Foreign-Key References from Client Input

**What & why:** `createProduct` and `updateProduct` both call `categoryRepository.findById(request.getCategoryId())` before doing anything else — the client-supplied `categoryId` is never trusted blindly.

**Interview Q&A:**

**Q: Why look up the `Category` at all instead of just setting the foreign key directly from the incoming ID?**
A: If the client sends a `categoryId` for a category that doesn't exist (typo, stale data, deleted category), blindly setting that as a foreign key would either fail at the database level with a raw constraint violation error (an ugly, unhelpful 500) or, worse, silently succeed if the database doesn't enforce it strictly. Explicitly looking it up and throwing a clean `ResourceNotFoundException("Category", categoryId)` if it's missing turns that failure into a proper, informative `404` instead.

---

## Topic 13: Soft Delete vs. Hard Delete

**What & why:** `deleteProduct` sets `active = false` rather than calling `productRepository.delete(product)` — directly connected to the `OrderItem` snapshot design decision from Week 1.

**Interview Q&A:**

**Q: Why soft delete for `Product` specifically, when `Category`'s delete is a real hard delete?**
A: `OrderItem` holds a foreign key reference to `Product` for traceability, even though it reads price/name from its own snapshot fields, not live from `Product`. A hard delete would either break that foreign key (if the DB doesn't enforce referential integrity) or fail outright with a constraint violation (if it does) the moment any product with existing order history was deleted. Soft delete avoids the problem entirely — the row persists, past orders stay valid, but the product disappears from active browsing and search via the `...AndActiveTrue` repository queries. `Category` doesn't have this problem in the current schema, since nothing holds a permanent historical reference to a category the way `OrderItem` does to `Product`.

**Q: What's a known gap in the current soft-delete implementation?**
A: `getAllProducts()` calls plain `findAll()`, which does **not** filter by `active` — meaning a soft-deleted product would still appear in the general product listing, even though it's correctly excluded from the category-filtered and search-filtered listings (which do use `...AndActiveTrue`). This is a real, acknowledged inconsistency worth fixing, not something to gloss over — a good habit to keep flagging these rather than letting them sit silently.

---

## Topic 14: DTO Denormalization Across a Relationship, and Lazy Loading in Context

**What & why:** `ProductResponse` includes both `categoryId` and `categoryName`, requiring `fromEntity` to call `product.getCategory().getName()` — reaching across the `@ManyToOne` lazy relationship established in Week 1.

**Interview Q&A:**

**Q: Why is it safe to call `product.getCategory().getName()` inside `fromEntity`, given `category` is `LAZY`-fetched?**
A: It's only safe because `fromEntity` is always called from within a `@Transactional` service method, while the Hibernate session is still open — the lazy association can be resolved with an additional query at that point. If `fromEntity` were ever called outside an active transaction (say, directly from a controller after the service method had already returned and the transaction closed), this would throw `LazyInitializationException` — exactly the kind of boundary violation `open-in-view: false` (Week 1) is designed to surface immediately rather than mask.

---

## Topic 15: API Design — Multiple Focused Endpoints vs. One Endpoint with Optional Filters

**What & why:** Product exposes `/`, `/category/{categoryId}`, and `/search` as three separate `GET` endpoints rather than a single endpoint accepting optional `categoryId`/`keyword` query parameters.

**Interview Q&A:**

**Q: What's the tradeoff of separate endpoints versus one endpoint with optional filters?**
A: Separate endpoints keep each controller method single-purpose, map cleanly to one specific repository query each, and avoid conditional branching logic to figure out which combination of filters was actually provided. A single flexible endpoint is more compact and can be more convenient for clients that want to combine filters (e.g., search *within* a category), at the cost of more complexity inside that one method. Neither is objectively correct — the decision here favored simplicity and directness for the MVP scope.

---

## Topic 16: Exception Message Specificity — When Vague Is Safer, When Specific Is Helpful

**What & why:** `GlobalExceptionHandler`'s new `ResourceNotFoundException` handler uses `ex.getMessage()` directly (a specific, informative message), unlike the deliberately generic "Invalid email or password" chosen for `BadCredentialsException` back in Week 2.

**Interview Q&A:**

**Q: Why is it fine for a 404 to say exactly which resource ID wasn't found, when a login failure deliberately hides whether it was the email or password that was wrong?**
A: The two situations carry different risks. Revealing which specific *credential* was wrong enables account enumeration — a real attack vector. Revealing that a specific *resource ID* doesn't exist carries no comparable risk; it's simply useful debugging information for a legitimate client that made a typo or is working with stale data. The right level of message specificity depends on what information could actually be exploited if exposed, not a single blanket rule applied everywhere.

---

## Master Self-Test (Complete Week 3)

1. Why is `ResourceNotFoundException` reused unchanged across both Category and Product — what made that reusability possible by design?
2. Explain dirty checking using the `deleteProduct` soft-delete example specifically — what would happen without `@Transactional` on that method?
3. Category is ADMIN-only for every write; Product allows SELLER for create/update but ADMIN-only for delete. Explain the reasoning, and name the missing piece that would make Product's SELLER permission fully correct in a real system.
4. What does `Pageable pageable` as a bare controller parameter actually do, and where do the default page/size values come from if the client sends none?
5. Why does `createProduct` look up the `Category` from the database instead of trusting the `categoryId` sent by the client?
6. Why is `Product` soft-deleted while `Category` is hard-deleted — what's the specific structural reason for the difference?
7. Name the known gap in `getAllProducts()` around soft-deleted products, and explain why it doesn't affect `getProductsByCategory` or `searchProducts` the same way.
8. Why is it safe for `ProductResponse.fromEntity()` to access a lazy-loaded `Category`, when accessing a lazy field elsewhere in the app might throw an exception?
9. Contrast the message specificity choices in `BadCredentialsException` (Week 2) versus `ResourceNotFoundException` (Week 3) — what's the actual decision criterion?
10. What's the one inconsistency between `CategoryService` and `ProductService` worth fixing before Week 4, regarding `@Transactional` usage?

If more than 2-3 feel shaky, this is the moment to solidify them — Week 4 (Cart module) will lean directly on the RBAC placement pattern, the dirty-checking mechanism, and the foreign-key validation pattern established here, applied to a slightly more complex entity relationship (Cart → CartItem → Product).
