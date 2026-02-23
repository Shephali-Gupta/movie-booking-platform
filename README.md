# Movie Booking Platform — Scalable Distributed System Design

This repository showcases the design and reference implementation of a **cloud-native, highly scalable movie ticket booking system** built to handle real-world production challenges like:

* Massive traffic spikes during blockbuster releases
* High read/write imbalance
* Concurrent seat booking conflicts
* Payment retries and failure handling
* Horizontal scalability with zero oversell risk

---

## Objective

Design a backend system that is:

* **Concurrency-safe**
* **Auto-scalable**
* **Fault-tolerant**
* **Event-driven**
* **Production-ready**

This project prioritizes **engineering depth over UI**.

---

## Implemented Scenarios

The following scenarios are **implemented** in this codebase (single Spring Boot app + PostgreSQL). See [DESIGN.md](architecture/DESIGN.md) and [SequenceDiagram.md](architecture/SequenceDiagram.md) for flows and target evolution.

| # | Scenario | API / Behaviour | Notes |
|---|----------|-----------------|--------|
| **1** | **Browse theatres and shows** | `GET /api/browse/theatres?movieId=&city=&date=` | Returns theatres running the movie in the given city on the given date, with show timings. Read-only; no locks. |
| **2** | **List seats for a show** | `GET /api/browse/shows/{showId}/seats` | Returns all seats for the show with status (AVAILABLE / LOCKED / BOOKED). Used to pick seats before booking. |
| **3** | **Book tickets (single or multi-seat)** | `POST /bookings` — body: `{ "userId", "showId", "seatNumbers": ["A1","A2","A3"] }` | Creates one `Booking` and N `BookingSeat` rows; applies **50% off 3rd ticket** and **20% afternoon show** discount; uses in-memory seat lock + `@Transactional` + `ShowSeat` version for concurrency. |
| **4** | **Theatre show CRUD** | `POST /api/theatres/{theatreId}/shows`, `PUT .../shows/{showId}`, `DELETE .../shows/{showId}` | Create, update, delete shows. Screen must belong to the theatre (ownership validated). |
| **5** | **Seat inventory (allocate & update)** | `POST /api/theatres/{theatreId}/shows/{showId}/seats` (allocate), `PATCH .../seats/{seatNumber}?status=` (update) | Allocate creates `show_seat` rows; update changes seat status (e.g. block/unblock). Theatre ownership verified. |
| **6** | **Cancel booking** | `DELETE /bookings/{bookingId}` | Releases seats (status → AVAILABLE), marks booking as CANCELLED. Idempotent for already-cancelled. |
| **7** | **Bulk cancel** | `POST /bookings/bulk-cancel` — body: `{ "bookingIds": [1,2,3] }` | Cancels multiple bookings; returns count of cancelled vs requested. |

**Not yet implemented (target):** Distributed seat lock (Redis), Kafka event stream, payment saga, API idempotency keys, read replicas/cache.

---

## Key Assumptions

| Area            | Assumption                               |
| --------------- | ---------------------------------------- |
| Traffic Pattern | Bursty traffic (weekends / launches)     |
| Seat Inventory  | Managed per-show, not global             |
| Payments        | External gateway handles PCI compliance  |
| User Data       | Eventually consistent is acceptable      |
| Search          | Slight staleness allowed for performance |
| Failures        | Must never cause double booking          |
| Scale           | Designed for horizontal scaling          |

### Implementation-specific assumptions

| Area | Assumption |
|------|------------|
| **Concurrency** | In-memory `SeatLockManager` (per seat key) — safe for **single instance** only; multi-node requires Redis/distributed lock. |
| **Booking flow** | Booking is **synchronous**; no payment step in code. `total_amount` is computed with discounts and stored; booking status is CONFIRMED. |
| **Discounts** | **50% off 3rd ticket** (when booking ≥3 seats); **20% off** for **afternoon shows** (12:00–17:00 in server default timezone). |
| **Cancellation** | Any CONFIRMED booking can be cancelled; seats are released and status set to CANCELLED. Already-cancelled returns success (idempotent). |
| **Data model** | Movie → Theatre → Screen → Show → ShowSeat → Booking / BookingSeat. PostgreSQL; schema in `database/schema.sql`. Tables `payment`, `idempotency_record`, `outbox_event` exist for future use. |
| **Deployment** | Single monolithic Spring Boot app; `docker-compose` for DB (and optional services). |

---

## High-Level Architecture

```
Client
  ↓
API Gateway
  ↓
Booking Service ───── Inventory Service
  ↓                       ↓
Kafka Event Stream ───────┘
  ↓
Async Consumers:
   → Payment
   → Notification
   → Analytics
```

---

## 💡 Design Philosophy

### Prevent Double Booking Without DB Locks

Instead of pessimistic DB locking (which kills scale), we use:

* Short-lived **distributed seat locks (Redis)**
* **Optimistic versioning** at DB layer
* **Idempotent APIs**

Result → High throughput with guaranteed correctness.

---

### Scale Based on Demand, Not CPU

Autoscaling driven by:

* Kafka consumer lag
* Partition parallelism
* Stateless service replicas

This ensures scale reacts to **real workload pressure**.

---

### Build for Failure First

All operations are:

* Retry-safe
* Idempotent
* Eventually consistent where possible

Failures never corrupt booking state.

---

## Core Data Model

```
Movie → Theatre → Screen → Show → ShowSeat → Booking
```

### ShowSeat = Source of Truth

Each seat contains:

* Status → AVAILABLE / LOCKED / BOOKED
* Version → Used for concurrency control

---

## Non-Functional Guarantees

| Requirement   | Strategy                                       |
| ------------- | ---------------------------------------------- |
| Scalability   | Stateless microservices + partitioned workload |
| Availability  | Event-driven async processing                  |
| Consistency   | Strong only for booking flow                   |
| Latency       | Cached reads + indexed queries                 |
| Resilience    | Retry + DLQ + Idempotency                      |
| Extensibility | Event-first architecture                       |

---

## Observability

Metrics designed for production ops:

* Seat conflict rate
* Booking success %
* Payment latency
* Consumer lag
* Revenue throughput

---

## Future Evolution

### Phase 1 — Hardening

* Multi-region deployment
* Circuit breakers
* Advanced monitoring

### Phase 2 — Business Expansion

* Dynamic pricing engine
* Loyalty programs
* Theatre dashboards

### Phase 3 — Intelligence Layer

* AI demand prediction
* Personalized recommendations
* Fraud detection

### Phase 4 — Globalization

* Currency engine
* Regional data compliance
* Cross-border payment orchestration

---

## 📄 Further Documentation

- **[NFR_PLATFORM_AND_STAKEHOLDERS.md](NFR_PLATFORM_AND_STAKEHOLDERS.md)** — Non-functional requirements (transactions, theatre integration, scale, payment, OWASP, compliance), platform provisioning and sizing, release and monitoring, KPIs, project plan, and product/stakeholder management.

---

## ▶️ Run Locally

```
docker-compose up
mvn spring-boot:run
```
