# ShopSphere — Backend

A full-stack ecommerce platform built to demonstrate production-relevant Java backend patterns: JWT auth, RBAC, Redis caching, Kafka events, WebSocket notifications, and cloud-native deployment (Docker, Kubernetes, AWS).

## Status
🚧 Week 1 — Foundation: schema design, entity modeling, repository layer.

## Tech Stack
Java 17 · Spring Boot 3.3 · Spring Security · PostgreSQL · Redis · Apache Kafka · WebSocket/STOMP · Docker

## Architecture Decisions Log
Decisions worth remembering (and explaining in interviews):

- **`OrderItem` snapshots `productName` and `price`** at order time instead of joining live to `Product` — an order must never change retroactively if the product's price or name changes later.
- **`Product.version`** uses JPA optimistic locking (`@Version`) to guard against lost updates when concurrent orders deduct stock — ties directly into the multi-threading work in Phase 2.
- **Roles are an enum (`RoleName`)**, not a separate table — sufficient for a fixed, small role set. A table-based approach would be the right call for admin-configurable permissions.
- **`users` / `orders` table names** are deliberately not `user` / `order` — both are reserved words in PostgreSQL SQL syntax.
- **`open-in-view: false`** — avoids the Open Session in View anti-pattern (lazy-loading exceptions surfaced early and intentionally, not masked until the view layer).

## Local Setup
1. PostgreSQL running locally, database named `shopsphere`
2. Redis running locally on default port
3. `mvn spring-boot:run`

## Roadmap
See project roadmap doc for the full 24-week plan (Phases 1–8: foundation → RBAC → caching/concurrency → Kafka/WebSocket → frontend → testing → Docker/CI → Kubernetes/nginx → AWS).
