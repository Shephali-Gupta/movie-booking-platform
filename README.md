# Movie Booking Platform — Scalable Distributed System Design

This com.movie.booking.repository showcases the design and reference implementation of a **cloud-native, highly scalable movie ticket booking system** built to handle real-world production challenges like:

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

Instead of pessimistic DB com.movie.booking.locking (which kills scale), we use:

* Short-lived **distributed seat locks (Redis)**
* **Optimistic versioning** at DB layer
* **Idempotent APIs**

Result → High throughput with guaranteed correctness.

---

### Scale Based on Demand, Not CPU

Autoscaling driven by:

* Kafka consumer lag
* Partition parallelism
* Stateless com.movie.booking.service replicas

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
