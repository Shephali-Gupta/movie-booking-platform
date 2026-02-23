# 🏗️ DESIGN.md — Scalable Booking Platform

This document explains **how the system is engineered** and reflects the **current implementation** in this com.movie.booking.repository, with target evolution called out where relevant.

---

# 0. 📦 Current Implementation Overview

The codebase is a **single Spring Boot application** (booking-com.movie.booking.service) that implements:

| Capability | Implementation |
| ----------- | ----------------- |
| **Browse** | `GET /api/browse/theatres?movieId&city&date` — theatres and show timings for a movie in a city on a date; `GET /api/browse/shows/{showId}/seats` — list seats for a show. |
| **Book tickets** | `POST /bookings` — single or multiple seats; creates `Booking` + `BookingSeat` rows; applies **50% off 3rd ticket** and **20% afternoon show** discount. |
| **Concurrency** | In-memory `SeatLockManager` (ConcurrentHashMap) per seat key; `@Transactional` single-DB transaction; `ShowSeat` has `@Version` for optimistic com.movie.booking.locking. |
| **Theatre show CRUD** | `POST/PUT/DELETE /api/theatres/{theatreId}/shows` — create, update, delete shows (screen must belong to theatre). |
| **Seat inventory** | `POST /api/theatres/{theatreId}/shows/{showId}/seats` — allocate seats; `PATCH .../seats/{seatNumber}?status=...` — update seat status. |
| **Cancellation** | `DELETE /bookings/{bookingId}` — cancel one; `POST /bookings/bulk-cancel` — cancel many; releases seats and marks booking CANCELLED. |

**Data com.movie.booking.model (implemented):** Movie, Theatre, Screen, Show, ShowSeat, Booking, BookingSeat (PostgreSQL; see `database/schema.sql`).

**Target (not yet in code):** Distributed lock (Redis), event stream (Kafka), payment saga, API-level idempotency keys, read replicas/cache. These remain the **design direction** for scale and resilience.

---

# 1. 🎯 Design Goals

The system is designed to guarantee:

* ❌ No double booking (strong correctness)
* 📈 Horizontal scalability during traffic spikes
* 🔁 Safe retries (idempotency — target)
* ⚡ Low-latency browsing (read optimization)
* 🧩 Fault isolation via async architecture (target)
* 🔄 Automatic recovery from failures
* 📊 Elastic scaling without manual intervention (target)

---

# 2. 🧱 Architecture Style

**Current: Monolithic com.movie.booking.service with clear domain boundaries. Target: Event-driven microservices + CQRS-style read/write separation.**

### Current implementation (single com.movie.booking.service)

```
                    ┌─────────────────────────────────────────────────────────┐
                    │              Booking Service (Spring Boot)               │
                    │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐│
  Client ──────────▶│  │ Browse      │ │ Booking    │ │ ShowManagement      ││
                    │  │ Controller  │ │ Controller │ │ SeatInventory        ││
                    │  └──────┬──────┘ └──────┬──────┘ └──────────┬──────────┘│
                    │         │               │                    │           │
                    │         ▼               ▼                    ▼           │
                    │  SeatLockManager (in-memory)  DiscountService           │
                    │         │               │                    │           │
                    │         └───────────────┼────────────────────┘           │
                    │                         ▼                                │
                    │                  Repositories (JPA)                      │
                    └─────────────────────────┬─────────────────────────────────┘
                                             │
                                             ▼
                                    ┌────────────────┐
                                    │  PostgreSQL    │
                                    │  (single DB)   │
                                    └────────────────┘
```

### Target architecture (multi-com.movie.booking.service, event-driven)

```
                ┌──────────────┐
                │  API Gateway │
                └──────┬───────┘
                       │
        ┌──────────────┼──────────────┐
        │                              │
┌──────────────┐               ┌──────────────┐
│ Booking Svc  │──────────────▶│ Event Stream │
└──────┬───────┘               └──────┬───────┘
       │                               │
┌──────────────┐              ┌──────────────┐
│ InventorySvc │              │ Payment Svc  │
└──────────────┘              └──────────────┘
       │                               │
       └────────────▶ Async Consumers ◀┘
```

---

# 3. 🔐 Concurrency Control Strategy

**Current implementation:** In-process lock + single DB transaction.

| Layer     | Current implementation        | Target / scale-out              |
| --------- | ------------------------------ | ------------------------------- |
| App Layer | In-memory `SeatLockManager` (ConcurrentHashMap, `putIfAbsent`) per seat key; released in `finally` after book | **Distributed Seat Lock (Redis)** for multi-node |
| DB Layer  | JPA `save()` on `ShowSeat`; entity has `@Version` for optimistic com.movie.booking.locking | **Explicit CAS**: `UPDATE show_seat SET status='BOOKED', version=version+1 WHERE id=? AND version=?` |
| API Layer | Not applied                   | **Idempotency Keys** (header + store) for retry safety |

Single-node correctness: lock prevents two threads from booking the same seat in one instance; transaction + version prevent inconsistent writes. For **horizontal scale**, replace in-memory lock with Redis (same key shape: `showId-seatNo`) and use optimistic DB update as the final authority.

### Seat reservation (target CAS query)

```
UPDATE show_seat
SET status='BOOKED', version=version+1
WHERE id=? AND version=?
```

If rows affected = 0 → another user won the race.

---

# 4. 🔁 Idempotency (Exactly-Once Semantics)

**Current implementation:** Not applied at API layer. Schema includes `idempotency_record`; optional in-memory filter exists but is not wired to booking endpoints.

**Target design:** Clients send `Idempotency-Key: <UUID>`; we persist in `idempotency_record` (key, request_hash, response, status, ttl). Retries return stored response. Protects payment retries, network failures, and double-clicks.

---

# 5. ⚡ Multithreading Model

**Current:** Stateless controllers and services; Tomcat thread pool handles concurrent requests. Synchronization via in-memory `SeatLockManager` (per seat key) and single-DB transaction. Safe for **one instance**; for multiple instances, move to Redis lock + CAS update as above.

**Target:** `Tomcat Threads → Booking Workers → Distributed Lock (Redis) → DB CAS Update` for safe processing across multiple instances.

---

# 6. 📩 Event-Driven Workflow (Why Async?)

**Current implementation:** Booking is **synchronous**: create booking, update seats, return response. No payment or notification in code; total amount is computed with discounts and stored on `Booking`.

**Target:** Convert `Booking → Payment → Notification` into `Booking → Event → Independent Consumers` (Kafka). Benefits: backpressure resistance, failure isolation, independent scaling. Payment would follow saga: confirm booking → charge gateway → on failure compensate (release seats).

---

# 7. 📊 Handling Consumer Lag Automatically (Target)

**Current:** No event stream or consumers; all processing is synchronous in the single com.movie.booking.service.

**Target:** Scale consumers based on **Kafka lag**, not CPU.

### Why Lag-Based Scaling?

CPU ≠ Demand
Lag = Real backlog = True scaling signal

Autoscaling Rule:

```
IF lag_per_partition > threshold
THEN scale replicas
```

Implementation options:

* Kubernetes HPA + Prometheus metrics
* KEDA event-driven autoscaling

Result:

| Scenario            | Consumers                |
| ------------------- | ------------------------ |
| Normal Traffic      | 3 Pods                   |
| Movie Release Spike | 20 Pods                  |
| Post Spike          | Scale Down Automatically |

No manual intervention required.

---

# 8. 📚 Database Design for Scale

**Current implementation:** Single PostgreSQL database. All writes (show_seat, booking, booking_seat, show CRUD, seat allocation) go to the same DB in ACID transactions. Reads (browse theatres/shows, list seats) hit the same DB; no read replicas or cache yet.

## Write Path → Strong Consistency (Primary DB)

Handles:

* Seat allocation and status updates
* Booking and booking_seat records
* Show CRUD (theatre management)

## Read Path (target: scaled independently)

Target uses:

* Read replicas for browse/search
* Cached show data (e.g. Redis)
* Eventually consistent projection where acceptable

---

# 9. 🧩 Do We Need Sharding?

✅ Yes — once data crosses single-node limits.

### Sharding Strategy

Shard Key:

```
show_id (or city_id)
```

Why?

* Booking traffic is localized to a show/city
* Prevents cross-shard transactions
* Ensures even distribution during releases

```
Shard 1 → Delhi Shows
Shard 2 → Mumbai Shows
Shard 3 → Bangalore Shows
```

This keeps seat-com.movie.booking.locking local and scalable.

---

# 10. 🧠 Caching Strategy (Target)

**Current:** No cache; all reads go to the primary DB.

**Target:**

| Data          | Cache Type | TTL    |
| ------------- | ---------- | ------ |
| Show Listings | Redis      | 5 min  |
| Seat Map      | Redis      | 30 sec |
| Pricing       | In-memory  | Short  |

We never cache **final booking state**.

---

# 11. 🛡️ Fault Tolerance

We assume everything fails.

### Protection Mechanisms

* Retry with exponential backoff
* Dead Letter Queue for poison messages
* Seat lock expiry auto-releases stale reservations
* Idempotent consumers prevent duplicate processing

---

# 12. 💳 Payment Integration (Target — Saga Pattern)

**Current:** No payment in code; booking stores `total_amount` and status CONFIRMED. Payment can be added later as a separate step or async consumer.

**Target:** We avoid distributed transactions. Flow: reserve seat & confirm booking → emit event → payment consumer charges gateway → on success emit PaymentSuccess; on failure emit PaymentFailed → compensate (release seat, mark booking FAILED). No 2PC; fully recoverable.

---

# 13. 📈 Scalability Characteristics

| Component    | Scaling Method               |
| ------------ | ---------------------------- |
| API          | Horizontal stateless scaling |
| DB           | Read replicas + sharding     |
| Consumers    | Lag-based autoscaling        |
| Cache        | Clustered Redis              |
| Event Stream | Partition scaling            |

System scales linearly by adding nodes.

---

# 14. 🔎 Observability

**Current:** Application logs and DB; no dedicated metrics or tracing in code.

**Target — key metrics:** Seat conflict rate, booking latency, Kafka lag, payment success %, lock contention rate. See [NFR_PLATFORM_AND_STAKEHOLDERS.md](../NFR_PLATFORM_AND_STAKEHOLDERS.md) for monitoring and SLOs.

---

# 15. ⚖️ Trade-Offs

| Choice                         | Trade-Off                  |
| ------------------------------ | -------------------------- |
| Eventual consistency for reads | Faster UX vs stale data    |
| Distributed locks              | Extra infra vs correctness |
| Saga over transactions         | More logic vs scalability  |
| Sharding complexity            | Needed for long-term scale |

---

# 16. 🚀 Implemented Features vs Future Enhancements

**Already implemented (this repo):**

* Browse theatres/shows by movie, city, and date
* List seats per show
* Multi-seat booking with 50% off 3rd ticket and 20% afternoon discount
* Theatre show CRUD (create, update, delete)
* Seat inventory allocation and status update
* Single and bulk cancellation

**Future enhancements:**

* Redis-based distributed lock and Kafka event stream
* Payment gateway integration (saga)
* API idempotency keys
* Dynamic pricing engine, real-time demand prediction, multi-region failover
* AI seat recommendation, fraud detection layer

---

# 17. ✅ Summary

This design prioritizes **correctness under concurrency** and **elastic scalability under burst traffic** — the two hardest problems in booking systems.

We intentionally avoided:

* Monolithic DB com.movie.booking.locking
* Distributed transactions
* Manual scaling approaches

Instead, we rely on:

✔ Event-driven flows
✔ Idempotent APIs
✔ Optimistic concurrency
✔ Lag-based autoscaling
✔ Shard-ready storage com.movie.booking.model