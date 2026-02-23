# 🔁 SequenceDiagram.md — Runtime Flows of the Booking Platform

This document captures the **critical execution paths** of the system as **implemented** in the booking-com.movie.booking.service. Diagrams are in **Mermaid** (render in GitHub or any Mermaid viewer).

---

# 1️⃣ Browse Theatres and Shows (by Movie, City, Date)

**API:** `GET /api/browse/theatres?movieId=1&city=Mumbai&date=2025-02-25`

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant BrowseController
    participant BrowseTheatreShowService
    participant TheatreRepository
    participant ScreenRepository
    participant ShowRepository

    Client->>BrowseController: GET /api/browse/theatres?movieId,city,date
    BrowseController->>BrowseTheatreShowService: findTheatresAndShowsByMovieCityAndDate(movieId, city, date)

    BrowseTheatreShowService->>TheatreRepository: findByCityIgnoreCase(city)
    TheatreRepository-->>BrowseTheatreShowService: List<Theatre>

    loop For each theatre
        BrowseTheatreShowService->>ScreenRepository: findByTheatreId(theatreId)
        ScreenRepository-->>BrowseTheatreShowService: List<Screen>
    end

    BrowseTheatreShowService->>ShowRepository: findByMovieIdAndScreenIdsAndDate(movieId, screenIds, dayStart, dayEnd)
    ShowRepository-->>BrowseTheatreShowService: List<Show>

    BrowseTheatreShowService-->>BrowseController: List<TheatreShowDto>
    BrowseController-->>Client: 200 OK (theatres + show timings)
```

✅ Read-only; no locks. Used to choose theatre and show before booking.

---

# 2️⃣ List Seats for a Show

**API:** `GET /api/browse/shows/{showId}/seats`

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant BrowseController
    participant SeatRepository

    Client->>BrowseController: GET /api/browse/shows/{showId}/seats
    BrowseController->>SeatRepository: findByShowId(showId)
    SeatRepository-->>BrowseController: List<ShowSeat>
    BrowseController-->>Client: 200 OK (seats with status)
```

✅ Lets user pick preferred seats (e.g. A1, A2, A3) for the booking request.

---

# 3️⃣ Book Seats (Single or Multi-Seat, with Discounts)

**API:** `POST /bookings` — Body: `{ "userId", "showId", "seatNumbers": ["A1","A2","A3"] }`

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant BookingController
    participant BookingService
    participant SeatLockManager
    participant ShowRepository
    participant SeatRepository
    participant DiscountService
    participant BookingRepository
    participant BookingSeatRepository

    Client->>BookingController: POST /bookings (showId, seatNumbers, userId)
    BookingController->>BookingService: bookSeats(showId, seatNumbers, userId)

    loop For each seat
        BookingService->>SeatLockManager: lockSeat(showId-seatNo, userId)
        alt Lock failed
            SeatLockManager-->>BookingService: false
            BookingService-->>BookingController: Response "Seat being booked by another user"
        end
        SeatLockManager-->>BookingService: true
    end

    BookingService->>ShowRepository: findById(showId)
    ShowRepository-->>BookingService: Show (price, showTime)

    BookingService->>SeatRepository: findByShowIdAndSeatNumberIn(showId, seatNumbers)
    SeatRepository-->>BookingService: List<ShowSeat>

    Note over BookingService: Validate all seats AVAILABLE

    BookingService->>DiscountService: calculateTotal(price, seatCount, showTime)
    Note over DiscountService: 50% off 3rd ticket, 20% afternoon
    DiscountService-->>BookingService: totalAmount

    BookingService->>BookingRepository: save(Booking CONFIRMED, totalAmount)
    BookingRepository-->>BookingService: Booking

    loop For each seat
        BookingService->>SeatRepository: save(ShowSeat status=BOOKED)
        BookingService->>BookingSeatRepository: save(BookingSeat)
    end

    BookingService->>SeatLockManager: releaseSeat(lockKey) [finally]
    BookingService-->>BookingController: BookingResponse(bookingId, status, totalAmount)
    BookingController-->>Client: 200 OK
```

✅ Single transaction; in-memory lock per seat; discounts applied; one Booking + N BookingSeat rows.

---

# 4️⃣ Theatre Creates / Updates / Deletes Show

**APIs:** `POST /api/theatres/{theatreId}/shows`, `PUT .../shows/{showId}`, `DELETE .../shows/{showId}`

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant ShowManagementController
    participant ShowManagementService
    participant ScreenRepository
    participant ShowRepository

    Client->>ShowManagementController: POST /api/theatres/{theatreId}/shows
    ShowManagementController->>ShowManagementService: createShow(ShowRequest)

    ShowManagementService->>ShowRepository: save(Show)
    ShowRepository-->>ShowManagementService: Show

    ShowManagementService-->>ShowManagementController: Show
    ShowManagementController-->>Client: 201 Created

    Note over Client,ShowRepository: Update: load show, apply request fields, save. Delete: deleteById(showId).
```

✅ Theatre ownership checked on create/update via screen → theatre.

---

# 5️⃣ Theatre Allocates or Updates Seat Inventory

**APIs:** `POST /api/theatres/{theatreId}/shows/{showId}/seats` (allocate), `PATCH .../seats/{seatNumber}?status=...` (update)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant SeatInventoryController
    participant SeatInventoryService
    participant ShowRepository
    participant ScreenRepository
    participant SeatRepository

    Client->>SeatInventoryController: POST .../seats { "seatNumbers": ["A1","A2"] }
    SeatInventoryController->>SeatInventoryService: allocateSeats(showId, theatreId, seatNumbers)

    SeatInventoryService->>ShowRepository: findById(showId)
    SeatInventoryService->>ScreenRepository: findById(screenId)
    Note over SeatInventoryService: Verify screen.theatreId == theatreId

    loop For each seat number
        SeatInventoryService->>SeatRepository: findByShowIdAndSeatNumber (skip if exists)
        SeatInventoryService->>SeatRepository: save(ShowSeat AVAILABLE)
    end

    SeatInventoryService-->>SeatInventoryController: allocated count
    SeatInventoryController-->>Client: 200 OK { allocated, requested }
```

✅ Allocate creates `show_seat` rows. Update flow: load seat, validate not BOOKED, set status, save.

---

# 6️⃣ Cancel Booking and Bulk Cancel

**APIs:** `DELETE /bookings/{bookingId}`, `POST /bookings/bulk-cancel` { "bookingIds": [1,2,3] }

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant BookingController
    participant CancellationService
    participant BookingRepository
    participant BookingSeatRepository
    participant SeatRepository

    Client->>BookingController: DELETE /bookings/{bookingId}
    BookingController->>CancellationService: cancelBooking(bookingId)

    CancellationService->>BookingRepository: findById(bookingId)
    BookingRepository-->>CancellationService: Booking

    CancellationService->>BookingSeatRepository: findByBookingId(bookingId)
    BookingSeatRepository-->>CancellationService: List<BookingSeat>

    loop For each BookingSeat
        CancellationService->>SeatRepository: findById(showSeatId)
        CancellationService->>SeatRepository: save(ShowSeat status=AVAILABLE)
    end

    CancellationService->>BookingSeatRepository: deleteAll(bookingSeats)
    CancellationService->>BookingRepository: save(Booking status=CANCELLED)

    CancellationService-->>BookingController: true
    BookingController-->>Client: 200 OK { status: Cancelled }
```

✅ Bulk cancel: same flow per bookingId; return count of cancelled.

---

# 7️⃣ Target: Payment Saga (Future)

When payment is integrated asynchronously:

```mermaid
sequenceDiagram
    autonumber
    participant Booking Service
    participant Outbox
    participant Kafka
    participant Payment Service
    participant Gateway
    participant DB

    Note over Booking Service: Booking confirmed, seats BOOKED
    Booking Service->>Outbox: Persist BookingConfirmed (same TX)
    Booking Service-->>Client: 201 Booking Confirmed

    Outbox->>Kafka: Publish BookingConfirmed
    Kafka->>Payment Service: Consume

    Payment Service->>Gateway: Charge (idempotency key)
    alt Success
        Gateway-->>Payment Service: Success
        Payment Service->>Kafka: Publish PaymentSuccess
    else Failure
        Gateway-->>Payment Service: Failed
        Payment Service->>Kafka: Publish PaymentFailed
        Kafka->>Booking Service: Consume → release seats, booking FAILED
    end
```

✅ Saga: confirm booking first; payment async; compensate on failure.

---

# ✔ Summary

| Flow | Implemented | Description |
|------|-------------|-------------|
| Browse theatres/shows | ✅ | By movie, city, date |
| List seats | ✅ | Per show |
| Book seats | ✅ | Multi-seat, discounts, in-memory lock, single TX |
| Show CRUD | ✅ | Theatre create/update/delete shows |
| Seat inventory | ✅ | Allocate and update seat status |
| Cancel / bulk cancel | ✅ | Release seats, mark CANCELLED |
| Payment saga | 🔜 Target | Async event-driven with compensation |

Current implementation uses a single com.movie.booking.service and database; concurrency is handled with in-memory locks and transactions. The target architecture (Redis lock, Kafka, idempotency, payment saga) is described in [DESIGN.md](DESIGN.md).
