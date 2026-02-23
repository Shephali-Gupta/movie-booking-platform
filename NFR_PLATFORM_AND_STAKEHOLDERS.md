# Movie Booking Platform — Non-Functional Requirements, Platform & Stakeholder Management

This document addresses **non-functional requirements**, **platform provisioning & release**, and **product/stakeholder management** for the Movie Booking Platform. It complements [README.md](README.md) and [architecture/DESIGN.md](architecture/DESIGN.md).

---

# 2. Non-Functional Requirements

## 2.1 Transactional Scenarios and Design Decisions

### Key transactional scenarios

| Scenario | Description | Design decision |
|----------|-------------|------------------|
| **Seat reservation** | Multiple users try to book the same seat | **Distributed lock (Redis)** for short-held lock + **optimistic version update** in DB (`UPDATE show_seat SET status='BOOKED', version=version+1 WHERE id=? AND version=?`). No double booking; zero rows updated → retry or fail. |
| **Multi-seat booking** | User books 3 seats in one request | **Single logical transaction**: lock all seats, validate all AVAILABLE, compute total (with discounts), create one `Booking` + N `BookingSeat` rows, mark all seats BOOKED. Rollback on any failure; no partial booking. |
| **Payment after booking** | Booking confirmed → payment charged | **Saga (event-driven)**: Booking com.movie.booking.service emits `BookingConfirmed`; payment consumer charges gateway. On success → emit `PaymentSuccess`; on failure → emit `PaymentFailed` and **compensating action** (release seats, mark booking FAILED). No 2PC; eventual consistency. |
| **Bulk cancellation** | User cancels multiple bookings | **Per-booking transaction**: each cancellation in its own transaction (release seats, update booking status, delete booking_seat rows). Partial success is acceptable; return count of cancelled. |
| **Show/seat inventory updates** | Theatre creates/updates shows or allocates seats | **CRUD transactions** with **theatre ownership check** (screen belongs to theatre). Idempotent where possible (e.g. allocate same seat numbers again = no-op). |

### Design principles applied

- **Strong consistency** only where required: seat state and booking records.
- **Eventual consistency** for read projections, search, and cross-com.movie.booking.service views.
- **Idempotency keys** on booking and payment APIs to make retries safe.
- **No distributed 2PC**; use sagas and compensating transactions for cross-com.movie.booking.service flows.

---

## 2.2 Integration with Theatres: Existing IT vs New Theatres and Localization

### Integration models

| Theatre type | Integration approach | Design decisions |
|--------------|------------------------|------------------|
| **Existing IT (legacy)** | **Adapter pattern**: platform exposes a stable API/contract; theatre side implements an **adapter** that maps their system (ERP, ticketing, POS) to our APIs. Optional: **batch file drop** (CSV/XML) for daily show/seat feeds. | REST/JSON primary; optional async (Kafka) or scheduled file ingestion for high-volume partners. Versioned API (e.g. `/v1/`) and backward compatibility window. |
| **New theatres** | **Native integration**: theatres onboard via partner portal; they use our **theatre APIs** (show CRUD, seat allocation, settlement reports) directly. | Same APIs; simplified onboarding and documentation; optional white-label UI. |
| **Localization (movies)** | **Content and locale**: movie metadata, languages, and certifications are **locale-aware**. Catalogue com.movie.booking.service supports `locale`/`region`; show titles and ratings can vary by country. | Separate **content** (movie, language, rating) from **business data** (show, theatre); locale as first-class dimension in APIs and DB. |

### Concrete decisions

- **Single platform contract**: One set of APIs (theatre, show, seat, booking) for all theatre types; adapters live on partner or platform side.
- **Localization**: `movie` and related content tables include `language`, `region`, or `locale`; APIs accept `Accept-Language` / `X-Region`; pricing and currency by region (see monetization).
- **Backward compatibility**: Deprecation policy (e.g. 12 months) for API versions; feature flags for gradual rollout to partners.

---

## 2.3 Scaling to Multiple Cities/Countries and 99.99% Platform Availability

### Multi-city / multi-country scale

| Driver | Approach |
|--------|----------|
| **Data locality** | **Shard by region/city** (e.g. `city_id` or `region_id`). Booking and inventory traffic stays within shard; no cross-shard booking transaction. |
| **Read scale** | **Read replicas** per region; browse/search served from replicas; writes to primary. |
| **Compliance & latency** | **Regional data residency**: deploy app + DB (or shard) in region (e.g. EU, APAC). User traffic routed to nearest region via **global load balancer** (e.g. Cloud DNS + regional backends). |
| **Failover** | **Multi-region active-passive or active-active**: at least two regions; RTO/RPO defined (e.g. RTO &lt; 1 h, RPO &lt; 5 min). |

### 99.99% availability (target ~52 min downtime/year)

| Layer | Measures |
|-------|----------|
| **Application** | Stateless services; horizontal scaling; health checks (/health, /ready); graceful shutdown; no single point of failure. |
| **Data** | DB: primary + sync/async replicas; automated failover; backups and point-in-time recovery. |
| **Network** | Multi-AZ deployment; load balancer with health checks; optional CDN for static/content. |
| **Dependencies** | Circuit breakers and timeouts for payment gateway and external theatre systems; retries with backoff; fallbacks where acceptable. |
| **Operations** | Blue-green or canary deployments; automated rollback; 24/7 on-call and incident playbooks. |

Design artifacts: **multi-region architecture diagram**, **RTO/RPO matrix**, **runbooks** for failover and incident response.

---

## 2.4 Integration with Payment Gateways

### Approach

- **Platform does not store card data**; PCI scope is limited to redirect or gateway-hosted flows.
- **Payment provider** (Stripe, Adyen, PayPal, or local gateways) handles tokenization, 3DS, and compliance.
- **Orchestration** in our system:
  - Booking com.movie.booking.service creates booking (CONFIRMED) and emits **BookingConfirmed** (booking_id, amount, currency, idempotency_key).
  - **Payment com.movie.booking.service** consumes event, calls gateway with idempotency key, and:
    - On success: emits **PaymentSuccess**; notification/analytics consumers run.
    - On failure: emits **PaymentFailed**; **compensation**: release seats, set booking to FAILED (saga).
- **Idempotency** at gateway and our side to avoid double charge on retries.
- **Multi-currency and multi-gateway**: gateway selected by region/currency; configuration per tenant/region.

Design artifacts: **payment sequence diagram** (booking → event → payment → confirm/compensate), **gateway adapter interface**, **idempotency and reconciliation** design.

---

## 2.5 Monetization

| Model | Description |
|------|-------------|
| **Transaction fee** | Percentage or fixed fee per ticket sold (e.g. 2–5% or flat per booking). Billing based on settled bookings. |
| **Subscription (B2B)** | Theatres pay monthly/annual for platform access, support, and SLA (tiers: basic / premium). |
| **Listing / placement** | Promoted placement of shows or theatres on browse/search (sponsored results). |
| **Data and analytics** | Aggregated, anonymized insights sold to studios/theatres (demand patterns, demographics). |
| **Premium features** | Dynamic pricing engine, loyalty integration, advanced reporting as paid add-ons. |

Platform supports monetization by: **recording booking and settlement data**, **tenant/theatre and region** in data com.movie.booking.model, and **billing/usage pipelines** (can be event-driven from booking/payment events).

---

## 2.6 Protection Against OWASP Top 10

| Threat | Mitigation |
|--------|-------------|
| **A01 Broken Access Control** | AuthZ on every API (user/theatre/admin); tenant isolation (theatre can only manage own shows/seats); role-based access (RBAC); validate resource ownership (e.g. screen belongs to theatre). |
| **A02 Cryptographic Failures** | TLS everywhere; no sensitive data in logs; hashed passwords (bcrypt/Argon2); secrets in vault (e.g. AWS Secrets Manager / HashiCorp Vault). |
| **A03 Injection** | Parameterized queries / JPA; no string-concatenated SQL; input validation and allowlists; secure headers (CSP, etc.). |
| **A04 Insecure Design** | Threat modelling; secure SDLC; idempotency and saga design to avoid race and duplicate payment; least privilege. |
| **A05 Security Misconfiguration** | Hardened images; no default credentials; security headers; minimal exposed surface; regular dependency and config reviews. |
| **A06 Vulnerable Components** | Dependency scanning (e.g. OWASP Dependency-Check, Snyk); timely upgrades and patching; curated dependency list. |
| **A07 Auth Failures** | Strong authentication (MFA for admin/theatre); secure session/token handling; no credential in URLs; OAuth2/OpenID where applicable. |
| **A08 Software and Data Integrity** | Signed artifacts; CI/CD integrity; secure supply chain; integrity checks on config and critical data. |
| **A09 Logging and Monitoring** | Audit logs for sensitive actions (booking, payment, admin); no secrets in logs; alerting on anomalies and failures. |
| **A10 SSRF** | Validate and allowlist outbound URLs (payment callbacks, webhooks); no user-controlled URLs in server-side fetch. |

Compliance and security docs: **secure coding guidelines**, **OWASP mapping**, **pen-test and vulnerability management** process.

---

## 2.7 Compliance

| Area | Considerations |
|------|----------------|
| **Data protection (GDPR, CCPA, etc.)** | Lawful basis for processing; consent where required; right to access, rectify, erase, port; data retention and deletion; DPA with processors; regional data residency where mandated. |
| **PCI DSS** | No card storage on platform; use PCI-compliant gateway; secure handling of gateway tokens; network segmentation and access control. |
| **Financial / tax** | Invoicing and tax calculation per region; audit trail for bookings and payments; settlement reports for theatres. |
| **Accessibility** | WCAG alignment for any customer-facing UI; considered in API design for clients that expose UI. |
| **Industry / local** | Cinema-specific or local regulations (e.g. age ratings, content classification); contractual obligations with studios and theatres. |

Design artifacts: **data classification**, **retention policy**, **privacy notice and consent flows**, **compliance checklist** per region.

---

# 3. Platform Provisioning, Sizing & Release Requirements

## 3.1 Technology Choices and Key Drivers

| Driver | Choice | Rationale |
|--------|--------|-----------|
| **Scale and elasticity** | Cloud-native (Kubernetes, managed services) | Auto-scaling, multi-AZ, managed DB and messaging. |
| **Developer productivity** | Java/Spring Boot, REST, event-driven | Ecosystem, talent pool, alignment with DESIGN.md (events, sagas). |
| **Correctness under load** | Event-driven + optimistic concurrency + idempotency | Avoids global locks; supports safe retries and sagas. |
| **Operability** | Containers, infra-as-code, observability first | Repeatable deployments; monitoring and logging as first-class. |
| **Vendor flexibility** | Abstraction over messaging (Kafka), DB (PostgreSQL), cache (Redis) | Reducе lock-in; swap implementations per cloud or region. |

---

## 3.2 Database, Transactions, and Data Modelling

- **Primary store**: **PostgreSQL** (or compatible). Strong consistency for booking, seats, and payments; ACID for single-shard transactions.
- **Transactions**: Short, well-scoped (e.g. lock seats → create booking → update seats); no long-running transactions; saga for cross-com.movie.booking.service (booking ↔ payment).
- **Data modelling**: Normalized core (movie, theatre, screen, show, show_seat, booking, booking_seat, payment); idempotency and outbox tables for reliability; shard key (e.g. `city_id` or `show_id`) for future sharding.
- **Read scaling**: Read replicas; caching (e.g. Redis) for browse/search with TTL; no cache for final booking state.
- **Artifacts**: ER diagram, sharding strategy doc, transaction boundaries and saga flows.

---

## 3.3 COTS / Enterprise Systems (Optional)

| Area | Possible COTS / managed com.movie.booking.service |
|------|---------------------------------|
| **Identity and access** | Okta, Auth0, or cloud IAM (e.g. Cognito, Azure AD). |
| **Payments** | Stripe, Adyen, or regional gateways (e.g. Razorpay, PayU). |
| **CRM / partner management** | Salesforce or similar for theatre onboarding and support. |
| **Analytics and BI** | Snowflake, BigQuery, or Redshift for reporting and monetization analytics. |
| **Support and ticketing** | Zendesk, ServiceNow, or equivalent for incidents and change. |

Choice driven by cost, compliance, and integration effort; platform APIs remain the single integration surface for core booking flows.

---

## 3.4 Hosting and Sizing (Cloud / Hybrid / Multi-Cloud)

- **Preferred**: **Single cloud (e.g. AWS or Azure)** per region for simplicity; **multi-region** for availability and data residency.
- **Sizing (indicative)**:
  - **App tier**: Stateless; scale by request rate (e.g. 2–10 pods per region normally; 5–30 during peaks); CPU/memory based on load tests.
  - **DB**: Managed RDS/Cloud SQL; instance size from write TPS and connection count; read replicas for read-heavy browse.
  - **Cache**: Redis cluster; size from key volume and hit-rate targets.
  - **Messaging**: Managed Kafka or equivalent; partitions and consumer groups per DESIGN.md lag-based scaling.
- **Hybrid / multi-cloud**: If required (e.g. existing on-prem or different clouds per geo), use **API-first design** and **event-driven sync**; consider mesh or API management for cross-cloud routing.

Artifacts: **sizing worksheet**, **multi-region topology diagram**, **cost com.movie.booking.model** (per region and per tier).

---

## 3.5 Release Management Across Geos and Internationalization

- **Release strategy**: **Blue-green or canary** per region; feature flags for gradual rollout; backward-compatible API versions.
- **Geo rollout**: Deploy to lower env (dev → staging) then production by region (e.g. EU → APAC → Americas); approval gates and rollback plan per region.
- **Internationalization (i18n)**:
  - **Backend**: Locale/region in APIs; content (movie titles, ratings) and pricing by locale; date/time in ISO and user timezone where needed.
  - **Clients**: String externalization, locale selection, currency and number formatting.
- **Artifacts**: Release runbook, geo rollout matrix, i18n/l10n checklist.

---

## 3.6 Monitoring and Log Analysis

| Concern | Solution |
|---------|----------|
| **Metrics** | Prometheus (or cloud equivalent); scrape app and infra; dashboards (Grafana or cloud console). |
| **Logs** | Centralized logging (e.g. ELK, Splunk, or cloud Logging); structured logs (JSON); correlation IDs across services. |
| **Traces** | Distributed tracing (e.g. Jaeger, Zipkin, or cloud trace); trace ID from gateway to booking and payment. |
| **Alerting** | Alerts on latency, error rate, availability, DB and Kafka lag; on-call and runbooks. |
| **SLOs** | Availability (e.g. 99.99%), latency (e.g. p99 &lt; 500 ms for booking), and error budget; dashboards and reviews. |

Artifacts: **monitoring architecture**, **alert runbooks**, **SLO/SLI definitions**.

---

## 3.7 Overall KPIs

| Category | Example KPIs |
|----------|------------------|
| **Availability** | Uptime %, error budget consumption. |
| **Performance** | Booking API p95/p99 latency; browse API latency; payment success rate. |
| **Business** | Bookings per day; revenue (GMV/platform fee); conversion (browse → book). |
| **Quality** | Double-booking incidents (target 0); failed payment recovery rate; dispute rate. |
| **Operations** | Deployment frequency; MTTR; change failure rate. |
| **Security** | Critical vulnerabilities open; authZ/authn failures; audit coverage. |

---

## 3.8 High-Level Project Plan and Estimates Breakup

| Phase | Scope | Duration (indicative) | Outcomes |
|-------|--------|------------------------|----------|
| **Discovery and design** | NFR, architecture, API contract, security and compliance outline | 4–6 weeks | Design docs, API spec, risk register. |
| **Foundation** | Infra (cloud, CI/CD, observability), core domains (movie, theatre, show, seat), DB and events | 6–8 weeks | Deployable skeleton; dev/staging env. |
| **Booking and payment** | Booking flow, idempotency, saga, payment gateway integration, seat com.movie.booking.locking | 6–8 weeks | End-to-end book and pay; no double book. |
| **Theatre and inventory** | Theatre onboarding, show/seat APIs, adapter pattern for legacy | 4–6 weeks | Theatres can manage shows and inventory. |
| **Scale and resilience** | Multi-region, failover, performance and chaos testing | 4–6 weeks | Meets availability and scale targets. |
| **Compliance and launch** | GDPR/PCI alignment, pen-test, documentation, geo rollout | 4–6 weeks | Production-ready; first regions live. |

**Total (sequential)**: ~28–40 weeks; overlap and parallel streams can reduce calendar time. Estimates to be refined with scope freeze and team size.

---

# 4. Product Management and Stakeholder Management

## 4.1 Stakeholder Management and Decision Closure

| Stakeholder | Typical concerns | Decisions and actions for closure |
|-------------|------------------|-----------------------------------|
| **Business / product** | Scope, launch date, revenue and conversion | **Prioritization**: agree on MVP (e.g. book + pay + one gateway); **trade-offs**: e.g. “no double booking” over “maximize features in v1”; **go/no-go** at phase gates with clear criteria. |
| **Theatres (existing IT)** | Integration effort, stability, support | **Contract first**: publish API spec and adapter guide; **pilot** with 1–2 partners; **SLAs** and support com.movie.booking.model; **change management**: versioning and deprecation policy. |
| **Theatres (new)** | Ease of onboarding, reporting | **Self-serve** where possible; **documentation and sandbox**; **feedback loop** (e.g. partner council) for roadmap. |
| **Finance / legal** | Compliance, contracts, risk | **Compliance checklist** (GDPR, PCI, etc.); **DPA and terms**; **audit trail** for bookings and payments; sign-off before go-live. |
| **Engineering / ops** | Tech choices, operability, tech debt | **Architecture review** and NFR sign-off; **observability and runbooks** before production; **tech radar** and refactor time in plan. |

**Practices**: Document decisions (ADR or decision log); single owner per decision; time-boxed discussions; escalate only when criteria for closure are clear.

---

## 4.2 Overall Technology Management

- **Standards**: Common stack (e.g. Java, Spring, PostgreSQL, Kafka, Redis); approved libraries and security guidelines.
- **Lifecycle**: Dependency and upgrade policy; deprecation and sunset process for APIs and components.
- **Innovation**: Allocate time for POCs (e.g. new gateway, new region); evaluate against NFR and cost.
- **Risk**: Tech risk register (e.g. single region, vendor lock-in); mitigation and review in governance.

---

## 4.3 Enabling Team and Introducing Efficiencies

- **Onboarding**: Runbooks, env setup, and “day one” guide; pairing and shadowing for new joiners.
- **Quality**: Automated tests (unit, integration, contract); CI/CD with quality gates; shift-left security (SAST, dependency scan).
- **Reuse**: Shared libraries (auth, idempotency, logging); platform APIs consumed by multiple clients.
- **Feedback**: Retrospectives; blameless post-mortems; continuous improvement backlog.
- **Tooling**: Dev and staging parity; local Docker Compose; feature flags to reduce branch complexity.

---

## 4.4 Delivery Planning and Estimates

- **Planning**: Phased plan (as in §3.8); milestones and deliverables per phase; dependencies and critical path visible.
- **Estimates**: Top-down from phases; bottom-up from stories/tasks; buffer for integration, compliance, and unknowns; re-estimate at phase boundaries.
- **Tracking**: Progress vs plan; scope change process; risk and dependency dashboard.
- **Communication**: Regular stakeholder updates (e.g. weekly); steering committee for major scope or timeline decisions; transparent reporting (green/amber/red) with reasons and actions.

---

## Document References

- [README.md](README.md) — Project overview and run instructions.
- [architecture/DESIGN.md](architecture/DESIGN.md) — Concurrency, events, scaling, and technical design.
