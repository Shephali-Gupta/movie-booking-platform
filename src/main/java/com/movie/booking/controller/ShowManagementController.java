package com.movie.booking.controller;

import com.movie.booking.dto.ShowRequest;
import com.movie.booking.model.Show;
import com.movie.booking.service.ShowManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/theatres/{theatreId}/shows")
public class ShowManagementController {

    private final ShowManagementService showManagementService;

    public ShowManagementController(ShowManagementService showManagementService) {
        this.showManagementService = showManagementService;
    }

    @PostMapping
    public ResponseEntity<Show> createShow(@PathVariable Long theatreId, @RequestBody ShowRequest request) {
        if (request.getScreenId() != null && !showManagementService.isScreenOwnedByTheatre(request.getScreenId(), theatreId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Show created = showManagementService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{showId}")
    public ResponseEntity<Show> updateShow(@PathVariable Long theatreId, @PathVariable Long showId, @RequestBody ShowRequest request) {
        if (request.getScreenId() != null && !showManagementService.isScreenOwnedByTheatre(request.getScreenId(), theatreId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Show updated = showManagementService.updateShow(showId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{showId}")
    public ResponseEntity<Void> deleteShow(@PathVariable Long theatreId, @PathVariable Long showId) {
        showManagementService.deleteShow(showId);
        return ResponseEntity.noContent().build();
    }
}
