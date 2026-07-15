# ShopSphere — Week 4 Master Notes (Complete)
### Cart Module — Every Topic Covered, With Interview Q&A

---

## Topic 1: Computed Fields vs. Stored Fields in DTOs

**What & why:** `CartItemResponse.subtotal` and `CartResponse.grandTotal` don't exist as database columns anywhere — they're calculated fresh every time a response is built, from data that *is* stored (`unitPrice`, `quantity`).

**How implemented:**
```java
BigDecimal unitPrice = cartItem.getProduct().getPrice();
BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
```
```java
BigDecimal grandTotal = itemResponses.stream()
    .map(CartItemResponse::getSubtotal)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

**Interview Q&A:**

**Q: Why not just store `subtotal` as a column on `cart_items`?**
A: A stored derived value can go stale relative to the data it was calculated from — if you stored subtotal at add-to-cart time and the product's price changed later, the stored value would silently become wrong until something explicitly recalculated it. Computing it fresh every time the response is built guarantees it's always correct, at the cost of a small amount of repeated calculation — a good tradeoff, since the calculation itself is trivial (one multiplication).

**Q: What's actually in the `cart_items` table, versus what's in the JSON response?**
A: The table stores only `id`, `cart_id`, `product_id`, `quantity` — nothing about price or name. Everything else in the response (`productName`, `unitPrice`, `subtotal`, `grandTotal`) is assembled at request time: the first two via a live join through the `product_id` foreign key, the last two via in-memory calculation in Java. The response is genuinely richer than the row data — a distinction worth being able to draw clearly when explaining the schema.

**Q: Why does `grandTotal` sum the already-computed `subtotal` values instead of recalculating `price × quantity` for every item again from scratch?**
A: Reuses `CartItemResponse.getSubtotal()` rather than duplicating the multiplication logic in two places. If the subtotal calculation ever needs to change (e.g., adding a per-item discount later), there's exactly one method to update, and the grand total automatically stays correct.

---

## Topic 2: `CartItem` Reads Live From `Product` — The Payoff of a Week 1 Decision

**What & why:** `CartItemResponse.fromEntity()` reads `unitPrice` directly from `cartItem.getProduct().getPrice()` at response-build time — never a stored/cached value on `CartItem` itself.

**Interview Q&A:**

**Q: Why does this matter, connecting back to Week 1?**
A: This is the actual payoff of the Cart-vs-Order design decision made in Week 1 — `CartItem` was deliberately built with a *live* reference to `Product` (unlike `OrderItem`, which snapshots). This is where that choice becomes visible in practice: if a product's price changes while sitting in someone's cart, the cart response always reflects the *current* price automatically, with zero extra code needed — that behavior falls directly out of the entity design made three weeks earlier.

---

## Topic 3: Business-Meaning-Driven Validation — `@Min(1)` vs. `@Min(0)`

**What & why:** `AddToCartRequest.quantity` and `UpdateCartItemRequest.quantity` both use `@Min(value = 1)`, while `Product.stockQty` (Week 3) uses `@Min(value = 0)` — a deliberate difference, not an inconsistency.

**Interview Q&A:**

**Q: Why can stock be `0` but cart quantity can't?**
A: These represent different real-world states. `0` stock is a legitimate, meaningful state — "out of stock" is something a product can genuinely be. A cart item with quantity `0` isn't a meaningful state at all — it's a contradiction; an item either is or isn't in the cart. If a quantity should go to zero, the correct action is removing the item entirely (a separate `DELETE` operation), not representing zero as a valid state to store.

---

## Topic 4: A New Kind of Exception — Business Rule, Not Structural Pattern

**What & why:** `InsufficientStockException` is specific and named, following the same criterion established in Week 3 Topic 1 — "not enough stock" is a genuine business rule (the resource exists, it just can't satisfy the request), distinct from `ResourceNotFoundException`'s "doesn't exist at all" pattern.

**Interview Q&A:**

**Q: Why not just reuse `ResourceNotFoundException` or throw a generic error here?**
A: The product genuinely exists — this isn't a "not found" situation at all, so `ResourceNotFoundException` would be semantically wrong. It's a distinct business rule ("requested quantity exceeds availability") that deserves its own type, both for a clean `400` mapping in `GlobalExceptionHandler` and for clarity anywhere the exception is caught or logged.

**Q: What status code did this map to, and why that one specifically, given two other codes were already in use for other "invalid" situations?**
A: `400 Bad Request` — meaning "the request itself cannot be fulfilled as submitted." This project also uses `404` (Week 3, resource doesn't exist) and `409` (Week 2, conflicts with existing data, like a duplicate email). Insufficient stock fits none of those precisely — the product exists (not 404) and there's no conflicting duplicate (not 409); it's simply an invalid request given current state, which is exactly what 400 means.

---

## Topic 5: Role-Based Access Control vs. Ownership-Based Access Control

**What & why:** `CartService` has **no `@PreAuthorize` anywhere** — a deliberate, total departure from `CategoryService` and `ProductService`. Every authenticated user, regardless of role, can manage a cart — but strictly their own.

**Interview Q&A:**

**Q: Why does Cart have zero `@PreAuthorize` annotations when every other service this project has built so far uses them?**
A: `@PreAuthorize("hasRole(...)")` answers "does this user's *role* permit this action" — a question with the same answer for every user of that role. Cart's actual rule is completely different: it's not about role at all, every role can manage a cart, but only their *own*. That's **object-level (resource-based) access control**, not role-based — a fundamentally different authorization pattern that `hasRole()` has no way to express, since it has no concept of "does this specific resource belong to this specific user."

**Q: If `@PreAuthorize` can't express ownership, how is ownership actually enforced here?**
A: Not through an authorization annotation at all — through the query itself. Every cart lookup is scoped by `userId`, and that `userId` comes only from the authenticated token (`principal.getUser().getId()` in the controller), never from anything the client can supply in a URL or request body. There's no code path where a client can request "cart X" — only "my cart," where "my" is fixed by the verified JWT. This makes it structurally impossible to load someone else's cart, rather than possible-but-checked-and-rejected.

---

## Topic 6: The Actual Security Mechanism — `WHERE user_id = ?`

**What & why:** The literal enforcement point is one line: `cartRepository.findByUserId(userId)`, where `userId` is always the authenticated caller's own ID. Confirmed directly in the Hibernate SQL log: `select ... from carts c1_0 where c1_0.user_id=?`.

**Interview Q&A:**

**Q: Walk through, precisely, how a request with User B's token is prevented from touching User A's cart item.**
A: `JwtAuthFilter` validates User B's token and stores User B's identity in the security context. `CartController` pulls that identity out via `@AuthenticationPrincipal` and passes User B's real database ID into `CartService`. `CartService.getOrCreateCart(userId)` runs `cartRepository.findByUserId(userId)` — a SQL query with `WHERE user_id = <User B's ID>`. This query can only ever return User B's own cart row; User A's cart row has a different `user_id` value and simply doesn't match the `WHERE` clause, so it's never part of the result set. The subsequent search for the specific cart item then happens *only* within that already-correctly-scoped cart's items — so a cart item ID belonging to a different user's cart is never found, and `ResourceNotFoundException` fires, producing a `404`.

**Q: Why does this scenario return 404 instead of 403?**
A: Returning 403 would implicitly confirm "this cart item exists, you're just not allowed to access it" — leaking information about another user's data existing at all. Returning 404 ("not found," which is honestly true from the requester's perspective — it's not found *in their own cart*) avoids that leak entirely. Same information-minimization principle as Week 2's deliberately vague "Invalid email or password" message, applied to a completely different scenario.

**Q: Is this "checked" at some point, or is there no explicit check at all?**
A: There's no explicit `if (owner != requester) reject` check anywhere in the code, and that's the deliberate design, not a shortcut. The security comes from the query's structure rather than a checkpoint — the database is asked "give me the cart belonging to this specific user," and a query built that way is physically incapable of returning a different user's row, the same way any `WHERE` clause filters out non-matching rows by construction, not by an afterthought check on each row.

---

## Topic 7: Lazy Cart Creation

**What & why:** `getOrCreateCart(userId)` creates a `Cart` row only on first access (view or add-to-cart), not at registration time — `AuthService.register()` from Week 2 has no awareness that carts even exist.

**Interview Q&A:**

**Q: Why not create a cart automatically when a user registers?**
A: Avoids creating an empty, likely-unused `Cart` row for every registration, many of which may never reach the shopping flow at all. It also keeps `AuthService` and `CartService` cleanly decoupled — registration doesn't need to know carts exist, and cart creation doesn't need to hook into the registration flow. Creating the cart lazily, exactly when it's first actually needed, is both simpler and avoids unnecessary data.

---

## Topic 8: Stock Validation — Two Different Checks for Two Different Operations

**What & why:** `addItemToCart` checks `alreadyInCart + newQuantity` against stock (an additive check); `updateCartItem` checks the new quantity directly against stock (an absolute check) — genuinely different logic, not a copy-paste inconsistency.

**Interview Q&A:**

**Q: Why do add-to-cart and update-quantity validate stock differently?**
A: They represent different operations. Adding to cart is *incremental* — if 3 units are already in the cart and someone adds 2 more, the real question is whether the product can supply the new total of 5, not just the 2 being newly added — hence `alreadyInCart + request.getQuantity()`. Updating a cart item sets an *absolute* new quantity, replacing whatever was there before, so the check is simply whether that new number itself fits within available stock — no addition needed.

**Q: Is this stock check actually safe against two users buying the last unit simultaneously?**
A: No, and that's an intentional, acknowledged limitation for this stage of the project. This check reads `product.getStockQty()` at request time but never reserves it, and doesn't use the `@Version` optimistic locking field built into `Product` back in Week 1. Two users could both pass this check for the same last unit at nearly the same moment, since adding to a cart isn't a real commitment. This is a UX nicety — preventing someone from obviously over-adding — not the actual concurrency-safe guarantee, which only matters (and only gets built) at checkout in Phase 2, where `@Version` will finally be used for real stock deduction.

---

## Topic 9: `@AuthenticationPrincipal` in Practice

**What & why:** Every `CartController` method uses `@AuthenticationPrincipal UserPrincipal principal`, then `principal.getUser().getId()` — the first real usage of this pattern on business logic (the earlier `RbacTestController` that would have demonstrated it was skipped back in Week 3).

**Interview Q&A:**

**Q: How does `@AuthenticationPrincipal` know what to inject, and where does that object actually come from?**
A: It's Spring Security automatically retrieving whatever object `JwtAuthFilter` placed into `SecurityContextHolder` during request processing (Week 2) and injecting it directly as a method parameter — no manual lookup code needed in the controller. This only works because that stored object is a `UserPrincipal`, matching the parameter's declared type.

**Q: Why `principal.getUser().getId()` instead of just `principal.getId()`?**
A: `UserPrincipal` implements Spring Security's `UserDetails` interface, which has no `getId()` method in its contract — it only guarantees things like `getUsername()`. `getUser()` was added back in Week 2 specifically to expose the real wrapped `User` entity for cases exactly like this, where actual domain data (the real database primary key) is needed beyond what the `UserDetails` contract provides.

---

## Topic 10: No Client-Supplied User or Cart IDs, Anywhere

**What & why:** No `CartController` endpoint accepts a `userId` or `cartId` from the client — not in the URL, not in the request body. `GET /api/cart`, never `GET /api/cart/{userId}`.

**Interview Q&A:**

**Q: What would go wrong if the API were instead designed as `GET /api/cart/{userId}`?**
A: Any authenticated user could potentially view or modify *any* other user's cart just by changing the ID in the URL — a real, common vulnerability class called IDOR (Insecure Direct Object Reference), the same underlying concept from the UUID discussion in Week 3, but showing up here as an actual authorization bug rather than a theoretical ID-guessing concern. Designing the endpoint so the target resource is *always* derived from the authenticated identity, never from client-supplied input, eliminates this entire vulnerability class by construction.

---

## Topic 11: Testing Methodology — Proving Isolation, Not Just Function

**What & why:** The test plan specifically included a two-user scenario (Test #33) — User B's token targeting User A's `cartItemId` — expecting `404`, not just single-user happy-path tests.

**Interview Q&A:**

**Q: Why is a single-user test insufficient to validate this module?**
A: Every single-user test can pass perfectly while a serious cross-user data leak exists — the individual endpoints "work" from one account's perspective regardless of whether isolation is actually enforced. Only a test that deliberately uses one user's credentials against another user's resource ID can actually prove the ownership guarantee holds. This is exactly the class of gap that's easy to miss in real development and turns into a genuine security incident in production — testing it explicitly, and being able to explain *why* it was tested, is a stronger signal in an interview than just saying "I tested my API."

---

## Master Self-Test (Week 4)

1. Why aren't `subtotal` and `grandTotal` stored as database columns, and what's the tradeoff of computing them on every request instead?
2. Explain, using the Week 1 Cart-vs-Order design decision, why `CartItemResponse` shows the *current* product price rather than a fixed one.
3. Why does `AddToCartRequest.quantity` reject `0` while `Product.stockQty` (Week 3) allows it?
4. What's the actual difference between role-based access control and ownership-based access control — and which one does `CartService` use, and why can't `@PreAuthorize("hasRole(...)")` express it?
5. Trace, in your own words, exactly how a request using User B's token is prevented from modifying User A's cart item — name the specific line/query where the actual enforcement happens.
6. Why does the cross-user cart item test return `404` instead of `403`?
7. Why is cart creation lazy (on first access) rather than happening at registration?
8. Explain why `addItemToCart` and `updateCartItem` validate stock differently — what's the difference in what each operation actually represents?
9. What's the current, acknowledged limitation of the stock check in this module, and why is it acceptable at this stage of the project?
10. What would go wrong, concretely, if cart endpoints were designed as `/api/cart/{userId}` instead of just `/api/cart`?

If more than 2-3 feel shaky, this is worth solidifying before Week 5 (Order module) — Order will need this exact same ownership pattern (a user can only see their own orders), applied on top of everything Cart already established, plus the added complexity of converting cart items into frozen order item snapshots at checkout time.
