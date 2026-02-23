package com.movie.booking.controller;

import com.movie.booking.dto.AllocateSeatsRequest;
import com.movie.booking.model.ShowSeat;
import com.movie.booking.service.SeatInventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/theatres/{theatreId}/shows/{showId}/seats")
public class SeatInventoryController {

    private final SeatInventoryService seatInventoryService;

    public SeatInventoryController(SeatInventoryService seatInventoryService) {
        this.seatInventoryService = seatInventoryService;
    }

    /**
     * Theatres allocate seat inventory for the show (create show_seat rows).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> allocateSeats(
            @PathVariable Long theatreId,
            @PathVariable Long showId,
            @RequestBody AllocateSeatsRequest request) {
        List<String> seatNumbers = request.getSeatNumbers() != null ? request.getSeatNumbers() : List.of();
        int created = seatInventoryService.allocateSeats(showId, theatreId, seatNumbers);
        return ResponseEntity.ok(Map.of("allocated", created, "requested", seatNumbers.size()));
    }

    /**
     * Theatres update seat status (e.g. block/unblock: status AVAILABLE, LOCKED, etc.).
     */
    @PatchMapping("/{seatNumber}")
    public ResponseEntity<ShowSeat> updateSeatStatus(
            @PathVariable Long theatreId,
            @PathVariable Long showId,
            @PathVariable String seatNumber,
            @RequestParam String status) {
        ShowSeat updated = seatInventoryService.updateSeatStatus(showId, theatreId, seatNumber, status);
        return ResponseEntity.ok(updated);
    }
}
