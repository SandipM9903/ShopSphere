# ShopSphere — Week 2 Master Notes
### Spring Security, JWT, RBAC — Every Topic Covered, With Interview Q&A

---

## Topic 1: JWT Structure & the Access/Refresh Token Strategy

**What & why:** A JWT is `header.payload.signature` — three Base64-encoded segments. Anyone can *decode* and read the payload (it's not encrypted); the signature only proves it wasn't *tampered with*. This is why sensitive data (passwords, secrets) should never go in a JWT payload — assume it's public, just verified.

We issue two tokens: a short-lived access token (15 min) sent with every request, and a long-lived refresh token (7 days) used only to mint new access tokens. This limits the damage window if a token leaks — a stolen access token is useless within 15 minutes; a stolen refresh token is a bigger risk but is only ever sent to one endpoint (`/api/auth/refresh`), reducing exposure.

**Interview Q&A:**

**Q: Is a JWT encrypted?**
A: No — it's signed, not encrypted. The payload is just Base64-encoded, fully readable by anyone who has the token. The signature (created with a secret key) proves the token wasn't tampered with after issuance, but provides no confidentiality. If you need to hide the payload contents, you'd need JWE (JSON Web Encryption), a different, less common standard.

**Q: Why two tokens instead of one long-lived token?**
A: A single long-lived token sent on every request maximizes exposure — if it leaks (logged, intercepted, stored insecurely on a client), an attacker has full access until it expires, which could be weeks. Splitting into a short-lived access token + long-lived refresh token means the token actually exposed to every request expires quickly, while the more sensitive long-lived credential is used rarely and only against one narrow endpoint.

**Q: Where should the client store these tokens?**
A: This is a genuinely debated topic — `localStorage` is vulnerable to XSS (any injected script can read it), while an `httpOnly` cookie is not readable by JavaScript but reopens CSRF concerns, requiring CSRF protection to come back for that specific route. There's no universally "correct" answer — it depends on the app's threat model — but it's the right thing to be able to discuss, not just "put it in localStorage" without knowing the tradeoff.

---

## Topic 2: HMAC Signing & the `SecretKey`

**What & why:** `Keys.hmacShaKeyFor(secret.getBytes())` converts a plain string secret into a cryptographic key object suitable for HMAC-SHA signing. HS256 requires the key be at least 256 bits (32 characters) — a shorter secret throws an error at startup rather than silently using a weak key.

**Interview Q&A:**

**Q: What's the difference between HMAC-based JWT signing (HS256) and RSA-based (RS256)?**
A: HS256 is symmetric — the same secret key both signs and verifies the token, meaning anything that can verify a token could also forge one. RS256 is asymmetric — a private key signs, and a public key verifies; services that only need to *verify* tokens (not issue them) can be given the public key safely, without gaining the ability to mint fake tokens. RS256 is preferred in distributed systems where multiple services verify tokens issued by one central auth service.

**Q: What happens if your JWT secret leaks?**
A: With HS256, anyone with the secret can forge a valid, signed token for any user, including fake admin tokens — full compromise. That's why it must never be hardcoded or committed to Git, and should live in environment variables or a secrets manager in any real deployment.

---

## Topic 3: `UserDetails` / `UserPrincipal` — The Adapter Pattern

**What & why:** Spring Security's authentication machinery is built entirely around its own `UserDetails` interface, not your domain entities. `UserPrincipal` wraps your `User` entity to satisfy that contract, keeping Spring Security concerns out of your domain model entirely.

**Interview Q&A:**

**Q: Why not just implement `UserDetails` directly on the `User` entity?**
A: That would couple your core domain model to a specific framework interface — if you ever changed authentication approaches, or wanted to reuse the entity in a non-web context (a batch job, a CLI tool), it would carry Spring Security baggage it doesn't need. The adapter pattern (a thin wrapper class) keeps the entity clean and framework-agnostic, at the cost of one small extra class.

**Q: What's the most common bug people hit implementing this adapter?**
A: Forgetting the `"ROLE_"` prefix when converting roles to `GrantedAuthority` objects. Spring Security's `hasRole("ADMIN")` checks internally for an authority literally named `"ROLE_ADMIN"` — if you create authorities without that prefix, every `hasRole()`/`@PreAuthorize("hasRole(...)")` check silently fails to match, and you get confusing 403s with no obvious cause.

---

## Topic 4: `UserDetailsService` — How Spring Security Loads a User

**What & why:** A single-method interface (`loadUserByUsername`) that Spring Security calls internally during authentication. It's the seam between "Spring Security's generic authentication process" and "your actual data source" — here, a PostgreSQL lookup via `UserRepository`.

**Interview Q&A:**

**Q: When does `loadUserByUsername` actually get called, and by what?**
A: It's called by `DaoAuthenticationProvider` (configured in `SecurityConfig`) during the `authenticationManager.authenticate(...)` call inside `login()`. You never call it directly — it's part of the internal authentication flow Spring Security drives on your behalf.

**Q: Why throw `UsernameNotFoundException` specifically, instead of returning `null`?**
A: Spring Security's authentication flow expects this specific exception type when a user isn't found, and handles it as part of producing a clean, generic authentication failure. Returning `null` instead would likely cause a `NullPointerException` deeper in Spring's internals — an unhandled crash rather than a clean auth failure.

---

## Topic 5: `JwtAuthFilter` — Filter Design and the "Never Reject" Principle

**What & why:** A custom `OncePerRequestFilter` that runs on every request, reads the `Authorization` header, and — if a valid token is present — populates `SecurityContextHolder` so the rest of the request "knows" who's making it. Critically, this filter never itself rejects a request; it only ever adds authentication info when it can. Whether an endpoint requires auth at all is a separate concern, owned by `SecurityConfig`.

**Interview Q&A:**

**Q: Why does `JwtAuthFilter` never reject requests itself?**
A: Single responsibility — this filter's only job is "authenticate if a valid token is present." Deciding *which endpoints require authentication* is a completely separate concern (open catalog browsing vs. protected checkout, for example), owned by `SecurityConfig`'s `authorizeHttpRequests` rules. Merging both responsibilities into one filter would make the security logic harder to reason about and modify independently.

**Q: What happens if token validation throws an exception inside the filter, and why is that caught rather than left to propagate?**
A: An expired or malformed token would otherwise surface as a raw 500 Internal Server Error — a confusing response for something as ordinary as "your session expired." The filter catches it, clears the security context, and lets the request continue unauthenticated; if the target endpoint requires auth, `SecurityConfig`'s rules correctly produce a 401/403 instead.

**Q: Why `OncePerRequestFilter` instead of implementing `Filter` directly?**
A: It guarantees the filter logic runs exactly once per request even in scenarios involving internal request forwarding/including, which could otherwise trigger a raw `Filter` twice in the same request lifecycle — a subtle bug `OncePerRequestFilter` exists specifically to prevent.

---

## Topic 6: `SecurityConfig` — Wiring It All Together

**What & why:** The central configuration class: which endpoints need auth, how sessions work (or don't), which encoder hashes passwords, and where the custom filter slots into Spring's internal filter chain.

**Interview Q&A:**

**Q: What does `SessionCreationPolicy.STATELESS` actually mean, and why does it matter for scaling?**
A: It tells Spring Security never to create or rely on an `HttpSession`. Every request must carry everything needed to authenticate it (the JWT) since the server remembers nothing between requests. This is what makes horizontal scaling straightforward — any server instance can handle any request, since there's no session state tied to a specific machine that a load balancer would need to route around.

**Q: Why disable CSRF protection here — isn't that a security risk?**
A: CSRF protection defends against a specific attack: a malicious site tricking a user's browser into automatically sending their session *cookie* to your API. We authenticate via a JWT sent explicitly in an `Authorization` header by the client, not an automatically-attached cookie — a malicious site can't force a browser to add that header on our behalf. Disabling CSRF here removes a protection against an attack vector that doesn't apply to this auth mechanism, not "turning off security" generally.

**Q: What's the real-world bug you'd hit if you used the old `DaoAuthenticationProvider` setter pattern from an older tutorial?**
A: Spring Boot 3.2+ removed the no-arg constructor + `setPasswordEncoder()`/`setUserDetailsService()` setter pattern in favor of passing the encoder directly into the constructor. Following an older guide gives you compile errors or deprecation warnings — a real, commonly-hit breaking change, not a hypothetical.

**Q: What does `@EnableMethodSecurity` actually enable, and what happens if you forget it?**
A: It activates Spring Security's method-level annotations, `@PreAuthorize` included. Without it present somewhere in your configuration, every `@PreAuthorize` annotation in the codebase is silently ignored — no error, no warning, it just does nothing. That silent-failure characteristic makes it a nasty one to debug without knowing to check for this annotation specifically.

---

## Topic 7: Password Hashing with BCrypt

**What & why:** `BCryptPasswordEncoder` is used instead of a fast hash like SHA-256, because BCrypt is deliberately slow (via an internal work factor) — imperceptible for one legitimate login, but computationally expensive at the scale an attacker would need to brute-force a leaked password database.

**Interview Q&A:**

**Q: Why not just use SHA-256 to hash passwords?**
A: SHA-256 is designed to be *fast* — great for verifying file integrity, terrible for passwords, because that same speed makes brute-forcing millions of guesses against a leaked hash database cheap. BCrypt (and similar algorithms like Argon2) are deliberately slow and configurable in cost, specifically to make large-scale offline brute-force attacks impractical even if your password hashes leak.

**Q: Does BCrypt need a separately-stored salt?**
A: No — BCrypt generates and embeds a random salt directly into its own output hash string, so you don't need a separate salt column. Each call to `.encode()` on the same password produces a different hash string, which is expected and correct.

---

## Topic 8: Bean Validation (`@Valid`, `@NotBlank`, `@Email`, `@Size`)

**What & why:** Declarative validation on request DTOs, checked automatically by Spring before the controller method body runs, when the parameter is annotated `@Valid`. Keeps validation logic out of the service layer entirely — services only ever see already-valid data.

**Interview Q&A:**

**Q: What actually happens internally when `@Valid` finds a violation?**
A: Spring throws `MethodArgumentNotValidException` before your controller method body executes at all — the method is never entered. That's why `GlobalExceptionHandler` needs an explicit handler for that exception type, since it happens outside your normal code path.

**Q: Why did the login DTO skip `@Email`/`@Size` validation that the register DTO has?**
A: Over-validating a login form can leak information — telling a client "this isn't a valid email format" during login reveals validation rules that a generic "invalid credentials" response wouldn't. Login only needs to confirm the fields aren't blank; the actual credential check (via `AuthenticationManager`) is the real source of truth.

---

## Topic 9: Exception Handling Architecture

**What & why:** `@RestControllerAdvice` centralizes exception-to-HTTP-response mapping across the whole application, instead of duplicating try/catch blocks in every controller. Each exception type maps to a semantically correct HTTP status.

**Interview Q&A:**

**Q: Why is the "wrong password" and "email not found" error message identical?**
A: To prevent account enumeration — if an attacker could distinguish "that email doesn't exist" from "that email exists but the password is wrong," they could probe your system to build a list of valid registered emails one guess at a time. A single generic "Invalid email or password" message for both cases closes that information leak.

**Q: What happens to an exception type with no explicit `@ExceptionHandler`?**
A: It falls through to Spring Boot's default error handling, producing a generic 500. This is intentional, not a gap to always avoid — it's a visible signal during development that a case hasn't been explicitly thought through yet. (We actually hit exactly this case testing the `/api/auth/refresh` endpoint with a garbage token this week — a real, live example of this principle catching a genuine gap.)

**Q: Why does the 400 validation handler join multiple field errors into one message instead of returning just the first one?**
A: So the client can see and fix every validation problem in one round trip, instead of fixing one error, resubmitting, hitting the next error, and repeating — a meaningfully better experience for whoever's consuming the API.

---

## Topic 10: `AuthService` — Business Logic Decisions

**What & why:** The service layer where register/login/refresh logic actually lives, with two specific, deliberate security decisions embedded in it.

**Interview Q&A:**

**Q: Why can't a client specify their own role during registration?**
A: `RegisterRequest` has no `role` field on purpose — every self-registered user is hardcoded to `RoleName.CUSTOMER` inside `AuthService`. If the client could pass a role, anyone could register themselves as `ADMIN` through the public API. Elevated roles must only ever be assignable through a separate, protected action (e.g. an admin-only endpoint), never through open self-registration.

**Q: What is refresh token rotation, and why does `login()` and `refresh()` both issue a brand-new refresh token instead of reusing the old one?**
A: Rotation means every time a refresh token is used, it's replaced with a new one rather than reused. This limits how long any single refresh token stays valid/useful — if one ever leaks, the exposure window shrinks each time a legitimate refresh cycle happens, rather than the same leaked token remaining exploitable for its entire original lifetime.

**Q: Why does `login()` delegate the actual credential check to `AuthenticationManager` instead of manually comparing the password?**
A: It reuses Spring Security's already-correct, already-tested authentication pipeline (`DaoAuthenticationProvider` → `UserDetailsService` → `PasswordEncoder.matches()`) instead of re-implementing password comparison logic by hand, which is exactly the kind of security-critical code you don't want to hand-roll and risk getting subtly wrong.

---

## Topic 11: `AuthController` — REST Conventions

**What & why:** A thin layer — validate input, call one service method, return the right HTTP status. No business logic lives here.

**Interview Q&A:**

**Q: Why does `register` return 201 while `login` and `refresh` return 200?**
A: 201 Created specifically communicates "a new resource was created" — accurate for registration, which creates a new user account. Login and refresh don't create anything; they return a result for an existing resource, which 200 OK correctly represents. Using the right status code isn't cosmetic — clients and tooling can programmatically rely on this distinction.

**Q: Why should business logic never live in the controller layer?**
A: Keeping controllers thin (routing + HTTP concerns only) means the actual business logic in the service layer can be unit-tested without any HTTP machinery involved, and can be reused if a second entry point ever needs the same logic (e.g. an internal admin tool calling `AuthService` directly rather than over HTTP).

---

## Topic 12: RBAC End-to-End — `@PreAuthorize` in Practice

**What & why:** `RbacTestController` proved the full chain works: `@PreAuthorize("hasRole('ADMIN')")` and `@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")` gate specific endpoints, verified against real tokens carrying different roles.

**Interview Q&A:**

**Q: Walk through what happens end-to-end when a `CUSTOMER` hits an `@PreAuthorize("hasRole('ADMIN')")` endpoint.**
A: `JwtAuthFilter` validates the token and populates `SecurityContextHolder` with an authenticated `UserPrincipal` carrying `ROLE_CUSTOMER` as its only authority. The request reaches the point where Spring's method security proxy intercepts the call, evaluates `hasRole('ADMIN')` against the authenticated authorities, finds no match, and throws `AccessDeniedException` — resulting in a 403, without the controller method body ever executing.

**Q: Why did the token need to be refreshed after changing the user's role directly in the database?**
A: The role claim is baked into the JWT at the moment it's issued — the token itself is the source of truth for that request, not a live database lookup on every call (that would defeat the purpose of a stateless token). Changing the DB doesn't retroactively change an already-issued token; a new token has to be issued (via login or refresh) to reflect the updated role.

---

## Topic 13: Postman Testing — What the Test Plan Actually Verifies

**What & why:** Testing wasn't just "does it return 200" — the plan specifically included negative cases (duplicate email, wrong password, missing token, wrong role, malformed refresh token) because those are exactly the cases interviewers probe, and exactly the cases that reveal whether error handling is actually correct end-to-end, not just the happy path.

**Interview Q&A:**

**Q: Why is testing the failure cases (409, 401, 403) just as important as testing the success case?**
A: The happy path (valid registration, correct login) is usually the easiest part to get right and the least likely to be broken. Failure handling is where real bugs hide — wrong status codes, leaked information in error messages, unhandled exceptions surfacing as 500s. A production-quality API is judged as much by its error responses as by its success responses.

**Q: What did testing the refresh endpoint with a garbage token reveal, and why does that matter?**
A: It surfaced an unhandled exception path — `jwtService.extractUsername()` on a malformed token, or the `IllegalArgumentException` thrown inside `refresh()`, had no corresponding `@ExceptionHandler`, so it fell through to a generic 500 instead of a clean 401. This is a completely normal, expected part of building a real system — the value here is having a systematic test plan that actually catches gaps like this before a real user (or interviewer) does.

---

## Master Self-Test (All Week 2 Topics)

1. Explain the difference between a JWT being "signed" versus "encrypted," and why that distinction matters for what you put in the payload.
2. Why does the access token include roles as a claim, but the refresh token doesn't?
3. Walk through, step by step, what happens inside `JwtAuthFilter` when a request arrives with an expired token.
4. Why is `UserPrincipal` a separate class instead of having `User` implement `UserDetails` directly?
5. What's the single most common bug when wiring up `GrantedAuthority` objects, and why does it happen?
6. Why is CSRF protection safely disabled here, and under what circumstances would that decision be wrong?
7. Why can't a user register themselves as an ADMIN, structurally — where exactly is that prevented in the code?
8. What is refresh token rotation and what problem does it solve?
9. Why does changing a user's role in the database not immediately affect their existing token?
10. Name the account-enumeration protection built into the login flow, and explain exactly how it works.

If more than 2-3 feel shaky, revisit those sections before starting Week 3 (Product CRUD + Category management) — Week 3 builds directly on the auth foundation, so gaps here compound.
