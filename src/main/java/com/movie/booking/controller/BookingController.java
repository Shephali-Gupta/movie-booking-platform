package com.movie.booking.controller;

import com.movie.booking.dto.BookingRequest;
import com.movie.booking.dto.BookingResponse;
import com.movie.booking.dto.BulkCancelRequest;
import com.movie.booking.service.BookingService;
import com.movie.booking.service.CancellationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CancellationService cancellationService;

    public BookingController(BookingService bookingService, CancellationService cancellationService) {
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
    }

    /**
     * Book movie tickets by selecting show and preferred seats.
     * Supports single seat (seatNumber) or multiple seats (seatNumbers). Bulk booking when seatNumbers has multiple.
     * Applies 50% discount on 3rd ticket and 20% afternoon show discount.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> book(@RequestBody BookingRequest request) {
        List<String> seats = request.getSeatNumbers();
        if (seats == null || seats.isEmpty()) {
            if (request.getSeatNumber() != null && !request.getSeatNumber().isBlank())
                seats = List.of(request.getSeatNumber().trim());
        }
        if (seats == null || seats.isEmpty()) {
            BookingResponse bad = new BookingResponse();
            bad.setStatus("Provide seatNumber or seatNumbers");
            bad.setTotalAmount(java.math.BigDecimal.ZERO);
            return ResponseEntity.badRequest().body(bad);
        }
        BookingResponse result = bookingService.bookSeats(
                request.getShowId(),
                seats.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()),
                request.getUserId()
        );
        return ResponseEntity.ok(result);
    }

    /** Cancel a single booking. */
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long bookingId) {
        boolean done = cancellationService.cancelBooking(bookingId);
        if (!done)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("status", "Cancelled", "bookingId", bookingId));
    }

    /** Bulk cancellation: cancel multiple bookings. */
    @PostMapping("/bulk-cancel")
    public ResponseEntity<Map<String, Object>> bulkCancel(@RequestBody BulkCancelRequest request) {
        List<Long> ids = request.getBookingIds() != null ? request.getBookingIds() : List.of();
        int cancelled = cancellationService.cancelBulk(ids);
        return ResponseEntity.ok(Map.of("cancelled", cancelled, "requested", ids.size()));
    }
}
