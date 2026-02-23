package com.movie.booking.service;

import com.movie.booking.dto.ShowRequest;
import com.movie.booking.model.Screen;
import com.movie.booking.model.Show;
import com.movie.booking.repository.ScreenRepository;
import com.movie.booking.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowManagementService {

    private final ShowRepository showRepository;
    private final ScreenRepository screenRepository;

    public ShowManagementService(ShowRepository showRepository, ScreenRepository screenRepository) {
        this.showRepository = showRepository;
        this.screenRepository = screenRepository;
    }

    /**
     * Theatres can create shows for the day (screen belongs to theatre).
     */
    @Transactional
    public Show createShow(ShowRequest request) {
        Show show = new Show();
        show.setMovieId(request.getMovieId());
        show.setScreenId(request.getScreenId());
        show.setShowTime(request.getShowTime());
        show.setPrice(request.getPrice());
        return showRepository.save(show);
    }

    /**
     * Theatres can update shows for the day.
     */
    @Transactional
    public Show updateShow(Long showId, ShowRequest request) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        if (request.getMovieId() != null) show.setMovieId(request.getMovieId());
        if (request.getScreenId() != null) show.setScreenId(request.getScreenId());
        if (request.getShowTime() != null) show.setShowTime(request.getShowTime());
        if (request.getPrice() != null) show.setPrice(request.getPrice());
        return showRepository.save(show);
    }

    /**
     * Theatres can delete shows for the day.
     */
    @Transactional
    public void deleteShow(Long showId) {
        if (!showRepository.existsById(showId))
            throw new IllegalArgumentException("Show not found: " + showId);
        showRepository.deleteById(showId);
    }

    public boolean isScreenOwnedByTheatre(Long screenId, Long theatreId) {
        return screenRepository.findById(screenId)
                .map(Screen::getTheatreId)
                .filter(tid -> tid.equals(theatreId))
                .isPresent();
    }
}
