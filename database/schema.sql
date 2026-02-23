-- ============================================================
-- MOVIE BOOKING PLATFORM - PRODUCTION ORIENTED SCHEMA
-- Designed for Scalability, Idempotency & Fault Tolerance
-- ============================================================

-- ============================================================
-- 1. MOVIE CATALOG
-- ============================================================

CREATE TABLE movie (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    language        VARCHAR(50),
    duration_mins   INT NOT NULL,
    rating          VARCHAR(10),
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_movie_title ON movie(title);


-- ============================================================
-- 2. THEATRE & SCREEN STRUCTURE
-- ============================================================

CREATE TABLE theatre (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    address     TEXT,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_theatre_city ON theatre(city);


CREATE TABLE screen (
    id              BIGSERIAL PRIMARY KEY,
    theatre_id      BIGINT NOT NULL REFERENCES theatre(id),
    name            VARCHAR(100),
    total_seats     INT NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_screen_theatre ON screen(theatre_id);


-- ============================================================
-- 3. SHOW (Shard Key Candidate)
--    ⚠️ Designed to shard by show_id or city_id later
--    ⚠️ "show" is quoted (reserved word in PostgreSQL)
-- ============================================================

CREATE TABLE "show" (
    id              BIGSERIAL PRIMARY KEY,
    movie_id        BIGINT NOT NULL REFERENCES movie(id),
    screen_id       BIGINT NOT NULL REFERENCES screen(id),
    show_time       TIMESTAMP NOT NULL,
    price           NUMERIC(10,2) NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_show_time ON "show"(show_time);
CREATE INDEX idx_show_movie ON "show"(movie_id);


-- ============================================================
-- 4. SHOW_SEAT (CRITICAL TABLE)
--    Source of truth for concurrency control
-- ============================================================

CREATE TABLE show_seat (
    id              BIGSERIAL PRIMARY KEY,
    show_id         BIGINT NOT NULL REFERENCES "show"(id),
    seat_number     VARCHAR(10) NOT NULL,

    status          VARCHAR(20) NOT NULL, -- AVAILABLE | LOCKED | BOOKED
    version         INT NOT NULL DEFAULT 0, -- Optimistic locking column

    locked_until    TIMESTAMP NULL, -- Seat hold expiry
    created_at      TIMESTAMP DEFAULT NOW(),

    UNIQUE(show_id, seat_number)
);

-- Hot-path index for booking
CREATE INDEX idx_show_seat_lookup
ON show_seat(show_id, status);

-- Used by seat-expiry cleanup job
CREATE INDEX idx_show_seat_locked_until
ON show_seat(locked_until);


-- ============================================================
-- 5. BOOKINGS
-- ============================================================

CREATE TABLE booking (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             VARCHAR(100) NOT NULL,
    show_id             BIGINT NOT NULL REFERENCES "show"(id),

    booking_status      VARCHAR(20) NOT NULL, -- INITIATED | CONFIRMED | FAILED | CANCELLED
    total_amount        NUMERIC(10,2),

    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_booking_user ON booking(user_id);
CREATE INDEX idx_booking_show ON booking(show_id);


-- ============================================================
-- 6. BOOKED SEATS (Mapping Table)
-- ============================================================

CREATE TABLE booking_seat (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL REFERENCES booking(id),
    show_seat_id    BIGINT NOT NULL REFERENCES show_seat(id),

    UNIQUE(booking_id, show_seat_id)
);

CREATE INDEX idx_booking_seat_booking ON booking_seat(booking_id);


-- ============================================================
-- 7. PAYMENT RECORD (Async Saga Controlled)
-- ============================================================

CREATE TABLE payment (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT NOT NULL REFERENCES booking(id),

    payment_status      VARCHAR(20) NOT NULL, -- PENDING | SUCCESS | FAILED
    gateway_ref         VARCHAR(255),
    amount              NUMERIC(10,2),

    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),

    UNIQUE(booking_id)
);


-- ============================================================
-- 8. IDEMPOTENCY TABLE (Prevents Duplicate Booking/Payment)
-- ============================================================

CREATE TABLE idempotency_record (
    id                  BIGSERIAL PRIMARY KEY,
    idempotency_key     VARCHAR(255) NOT NULL,
    request_hash        VARCHAR(255) NOT NULL,

    response_payload    JSONB,
    status              VARCHAR(20),

    created_at          TIMESTAMP DEFAULT NOW(),
    expires_at          TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_idempotency_key
ON idempotency_record(idempotency_key);

CREATE INDEX idx_idempotency_expiry
ON idempotency_record(expires_at);


-- ============================================================
-- 9. OUTBOX TABLE (Reliable Event Publishing)
--    Enables exactly-once event delivery pattern
-- ============================================================

CREATE TABLE outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100),
    aggregate_id    BIGINT,

    event_type      VARCHAR(100),
    payload         JSONB,

    published       BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_outbox_unpublished
ON outbox_event(published);


-- ============================================================
-- 10. CLEANUP JOB SUPPORT (Seat Lock Expiry)
-- ============================================================

-- Query used by scheduler:
-- Release stale locks automatically
--
-- UPDATE show_seat
-- SET status='AVAILABLE', locked_until=NULL
-- WHERE status='LOCKED' AND locked_until < NOW();

-- ============================================================
-- VERIFICATION SUMMARY (vs JPA entities in booking-service)
-- ============================================================
-- movie       : id, title, language, duration_mins, rating, created_at     ✓
-- theatre     : id, name, city, address, created_at                        ✓
-- screen      : id, theatre_id, name, total_seats, created_at               ✓
-- "show"      : id, movie_id, screen_id, show_time, price, created_at     ✓ (quoted: reserved word)
-- show_seat   : id, show_id, seat_number, status, version, locked_until, created_at, UNIQUE(show_id,seat_number) ✓
-- booking     : id, user_id, show_id, booking_status, total_amount, created_at, updated_at ✓
-- booking_seat: id, booking_id, show_seat_id, UNIQUE(booking_id, show_seat_id) ✓
-- payment, idempotency_record, outbox_event : for future use (no entities yet)
