package com.movie.booking.service;

import com.movie.booking.model.Screen;
import com.movie.booking.model.Show;
import com.movie.booking.model.ShowSeat;
import com.movie.booking.repository.ScreenRepository;
import com.movie.booking.repository.SeatRepository;
import com.movie.booking.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatInventoryService {

    private final ShowRepository showRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;

    public SeatInventoryService(ShowRepository showRepository,
                                ScreenRepository screenRepository,
                                SeatRepository seatRepository) {
        this.showRepository = showRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
    }

    /**
     * Theatres allocate seat inventory for the show: create show_seat rows for given seat numbers.
     * Screen must belong to the theatre.
     */
    @Transactional
    public int allocateSeats(Long showId, Long theatreId, List<String> seatNumbers) {
        Show show = showRepository.findById(showId).orElseThrow(() -> new IllegalArgumentException("Show not found"));
        Screen screen = screenRepository.findById(show.getScreenId()).orElseThrow(() -> new IllegalArgumentException("Screen not found"));
        if (!screen.getTheatreId().equals(theatreId))
            throw new IllegalArgumentException("Show does not belong to this theatre");

        int created = 0;
        for (String seatNo : seatNumbers) {
            if (seatRepository.findByShowIdAndSeatNumber(showId, seatNo).isPresent()) continue;
            ShowSeat seat = new ShowSeat();
            seat.setShowId(showId);
            seat.setSeatNumber(seatNo);
            seat.setStatus("AVAILABLE");
            seatRepository.save(seat);
            created++;
        }
        return created;
    }

    /**
     * Theatres can update seat inventory (e.g. block/unblock a seat).
     */
    @Transactional
    public ShowSeat updateSeatStatus(Long showId, Long theatreId, String seatNumber, String status) {
        Show show = showRepository.findById(showId).orElseThrow(() -> new IllegalArgumentException("Show not found"));
        Screen screen = screenRepository.findById(show.getScreenId()).orElseThrow(() -> new IllegalArgumentException("Screen not found"));
        if (!screen.getTheatreId().equals(theatreId))
            throw new IllegalArgumentException("Show does not belong to this theatre");

        ShowSeat seat = seatRepository.findByShowIdAndSeatNumber(showId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found for this show"));
        if ("BOOKED".equals(seat.getStatus()))
            throw new IllegalArgumentException("Cannot change status of a booked seat");
        seat.setStatus(status);
        return seatRepository.save(seat);
    }
}
