package com.movie.booking.controller;

import com.movie.booking.dto.TheatreShowDto;
import com.movie.booking.model.ShowSeat;
import com.movie.booking.repository.SeatRepository;
import com.movie.booking.service.BrowseTheatreShowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/browse")
public class BrowseController {

    private final BrowseTheatreShowService browseTheatreShowService;
    private final SeatRepository seatRepository;

    public BrowseController(BrowseTheatreShowService browseTheatreShowService, SeatRepository seatRepository) {
        this.browseTheatreShowService = browseTheatreShowService;
        this.seatRepository = seatRepository;
    }

    /**
     * Browse theatres running the given movie in the city, with show timings for the chosen date.
     * GET /api/browse/theatres?movieId=1&city=Mumbai&date=2025-02-25
     */
    @GetMapping("/theatres")
    public ResponseEntity<List<TheatreShowDto>> getTheatresAndShows(
            @RequestParam Long movieId,
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<TheatreShowDto> result = browseTheatreShowService.findTheatresAndShowsByMovieCityAndDate(movieId, city, date);
        return ResponseEntity.ok(result);
    }

    /**
     * List seats for a show (to select preferred seats before booking).
     * GET /api/browse/shows/{showId}/seats
     */
    @GetMapping("/shows/{showId}/seats")
    public ResponseEntity<List<ShowSeat>> getSeatsForShow(@PathVariable Long showId) {
        return ResponseEntity.ok(seatRepository.findByShowId(showId));
    }
}
