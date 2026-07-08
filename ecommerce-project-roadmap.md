# ShopSphere — Java Full Stack Ecommerce Capstone
### Mentor-Guided Roadmap | 2 hrs/day | ~24 weeks

---

## 1. Project Identity

**Name (working title):** ShopSphere
**Elevator pitch:** A full-stack ecommerce platform with role-based access (Customer/Seller/Admin), real-time inventory updates, cached product catalog, async order processing, and a fully containerized, CI/CD-deployed cloud infrastructure.

**Why this project covers interview topics well:**
- CRUD + relational modeling → PostgreSQL schema design
- Security → JWT + RBAC
- Performance → Redis caching + multi-threading
- Distributed systems → Kafka events
- Real-time → WebSocket (order status / seller notifications)
- DevOps → Docker, GitHub Actions, Jenkins, Kubernetes, nginx, AWS

---

## 2. Tech Stack (Final)

| Layer | Choice |
|---|---|
| Backend | Java 17, Spring Boot 3.x, Spring Security, Spring Data JPA |
| Database | PostgreSQL |
| Cache | Redis |
| Messaging | Apache Kafka |
| Realtime | WebSocket + STOMP |
| Frontend | React + Axios + React Query |
| Auth | JWT (access + refresh token), RBAC (CUSTOMER, SELLER, ADMIN) |
| Testing | JUnit5, Mockito |
| Containerization | Docker, docker-compose |
| CI/CD | GitHub Actions (primary), Jenkins (secondary — interview talking point) |
| Orchestration | Kubernetes (minikube locally → EKS later) |
| Reverse Proxy | nginx |
| Cloud | AWS (EC2/EKS, RDS, ElastiCache, S3 for product images) |

---

## 3. Core Modules

1. **Auth Service** — Register/Login, JWT issue + refresh, RBAC roles
2. **Catalog Service** — Products, categories, search, Redis-cached reads
3. **Cart Service** — Add/update/remove, per-user cart in Redis
4. **Order Service** — Checkout, order creation, multi-threaded inventory deduction, Kafka event on order placed
5. **Notification Service** — Kafka consumer → WebSocket push to seller/admin
6. **Admin Dashboard** — Manage products, view orders, seller approval (RBAC-gated)
7. **Payment (Mock)** — Simulated payment gateway with success/failure states (real gateways need business KYC, so mock is standard practice even in interviews)

---

## 4. Phase-by-Phase Roadmap (24 weeks)

### Phase 1 — Foundation (Weeks 1–5)
- Week 1: Project setup, ERD design, PostgreSQL schema, repo structure, README skeleton
- Week 2: User entity, Spring Security config, JWT issue/validate, refresh token flow
- Week 3: RBAC — role entity, `@PreAuthorize`, method-level security, admin-only endpoints
- Week 4: Product + Category CRUD, pagination, filtering
- Week 5: Cart module (add/update/remove), order entity + checkout flow (single-threaded first)

**Notes to write this phase:** JWT internals (access vs refresh), Spring Security filter chain, RBAC vs ABAC, normalization decisions in your ERD

### Phase 2 — Performance & Concurrency (Weeks 6–9)
- Week 6: Redis setup, cache product listing + cache invalidation on update
- Week 7: Multi-threading — `ExecutorService` for inventory deduction, `CompletableFuture` for parallel calls (e.g., stock check + price check), thread-safety (synchronized/AtomicInteger for stock counter)
- Week 8: Kafka setup — order-placed producer, notification consumer
- Week 9: WebSocket/STOMP — push order status updates live to seller dashboard

**Notes to write this phase:** cache-aside vs write-through, race conditions in stock deduction, Kafka partitions/consumer groups, WebSocket vs polling

### Phase 3 — Frontend (Weeks 10–13)
- Week 10: React setup, auth pages (login/register), protected routes by role
- Week 11: Product listing, search, product detail page
- Week 12: Cart + checkout UI
- Week 13: Admin/seller dashboard, live order notifications (WebSocket client)

### Phase 4 — Testing (Weeks 14–15)
- Week 14: JUnit5 unit tests for services (mock repositories with Mockito)
- Week 15: Integration tests for auth flow and order flow

**Notes to write this phase:** mocking vs stubbing, test pyramid, `@SpringBootTest` vs `@WebMvcTest`

### Phase 5 — Containerization & CI (Weeks 16–18)
- Week 16: Dockerfile for backend + frontend, docker-compose (app + Postgres + Redis + Kafka)
- Week 17: GitHub Actions — build, test, docker build/push pipeline
- Week 18: Jenkins — replicate a basic pipeline locally (Jenkinsfile) just for interview fluency, not as your main CI

### Phase 6 — Kubernetes & nginx (Weeks 19–21)
- Week 19: minikube setup, Deployment + Service YAMLs for backend/frontend/Postgres/Redis
- Week 20: nginx as ingress controller / reverse proxy, ConfigMaps + Secrets
- Week 21: Horizontal pod autoscaling basics (even if just conceptually tested)

### Phase 7 — AWS Deployment (Weeks 22–23)
- Week 22: RDS (Postgres), ElastiCache (Redis), S3 (product images)
- Week 23: EC2 or EKS deployment, security groups, environment config

### Phase 8 — Wrap-up (Week 24)
- Polish README, architecture diagram, deployment guide
- Finalize notes into an interview-prep doc
- Update resume + LinkedIn with project bullets
- Record a 2-min project walkthrough (optional but great for interviews)

---

## 5. Weekly Rhythm (2 hrs/day)
- **Mon–Thu:** Build (feature work)
- **Fri:** Write notes for the week's topics (plain-language + your own annotated diagrams)
- **Sat:** HLD practice (1 case study, whiteboard/paper reasoning, 45–60 min)
- **Sun (if available):** Buffer/catch-up + review

---

## 5a. LLD — Where It's Built Into the Project

LLD isn't a separate track — it's embedded in *how* you build each module. For every feature, before writing code, ask: "What classes do I need, how do they relate, what pattern fits?" Document the decision in your notes.

| Project Feature | LLD Concept / Pattern | When (Phase) |
|---|---|---|
| Notification dispatch (email/websocket) | Factory Pattern | Phase 2 |
| Discount/pricing rules | Strategy Pattern | Phase 1 |
| Order object construction | Builder Pattern | Phase 1 |
| Kafka event → multiple consumers | Observer Pattern | Phase 2 |
| Redis/Kafka connection beans | Singleton (via Spring bean scope) | Phase 2 |
| Service layer separation | SOLID (SRP, DIP) | Throughout |
| Thread-safe stock deduction | Concurrency-safe class design | Phase 2 |
| DTO vs Entity separation | API contract design | Phase 1 |

**LLD interview-readiness bonus round (do after Phase 4):** Once the core project is stable, spend 2–3 weekends designing 3–4 classic LLD problems from scratch on paper (not coding them) to prove the patterns generalize beyond your project:
- Design a Parking Lot system
- Design a Vending Machine
- Design an Elevator system
- Design a Rate Limiter (class-level, not distributed)

---

## 5b. HLD — Separate Weekly Practice Track

Run this in parallel with the build, starting Week 1 — don't wait for the project to finish. One case study per week (Saturday slot above), reasoning on paper: requirements → back-of-envelope estimation → API design → high-level components → deep dive on 1–2 tricky parts → tradeoffs.

**Suggested rotation (12-week cycle, repeat/extend as needed):**

| Week | HLD Problem | Core Concepts Tested |
|---|---|---|
| 1 | Design a URL Shortener | Hashing, DB schema, read-heavy scaling |
| 2 | Design a Rate Limiter (distributed) | Token bucket/sliding window, Redis |
| 3 | Design a Notification System | Queueing, fan-out, retries |
| 4 | Design an Ecommerce Product Catalog at scale | Sharding, caching, search indexing (ties directly to your project!) |
| 5 | Design a Chat Application | WebSocket at scale, message ordering, delivery guarantees |
| 6 | Design an Order/Payment System | Idempotency, distributed transactions, saga pattern |
| 7 | Design a News Feed (Twitter-like) | Fan-out on write vs read, timeline generation |
| 8 | Design a File Storage System (Dropbox-like) | Chunking, dedup, metadata DB |
| 9 | Design a Web Crawler | Queue management, dedup, distributed workers |
| 10 | Design a Ticket Booking System (BookMyShow) | Concurrency/locking, seat inventory race conditions |
| 11 | Design an API Gateway / Load Balancer | Routing, health checks, circuit breaker |
| 12 | Design a Video Streaming Service (basic) | CDN, chunked delivery, adaptive bitrate |

**Resources for this track:** Gaurav Sen (YouTube), Arpit Bhayani (great for both HLD and distributed systems fundamentals), "Grokking the System Design Interview" style write-ups for structure.

**Key HLD building blocks to nail conceptually** (show up across almost every problem above): load balancing, caching strategies, database sharding/replication, SQL vs NoSQL tradeoffs, message queues, CAP theorem, consistent hashing, rate limiting, CDN basics. If ShopSphere gives you hands-on experience with any of these (caching, messaging, load balancing via nginx), draw the explicit connection in your notes — it makes your HLD answers concrete instead of textbook-recited.

## 6. Notes System
For each topic, keep a short doc with:
1. What problem it solves (plain language)
2. How you implemented it in ShopSphere (code snippet)
3. Common interview question + your answer
4. Gotchas you hit while building it

This becomes your **interview prep bank** — far more valuable than generic notes because it's tied to code you actually wrote and can defend.

---

## 7. Realistic Timeline Summary
- **Solo, 2 hrs/day, no other commitments:** ~5–6 months
- **With parallel job hunting + DSA prep (your actual situation):** ~6–8 months
- Resume gets updated incrementally — don't wait till the end; add bullets as each phase ships

---

## 8. Immediate Next Step
Start Week 1: ERD + repo setup. I can help you design the database schema and set up the initial Spring Boot project structure whenever you're ready.
