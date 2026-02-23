package com.movie.booking.service;

import com.movie.booking.dto.TheatreShowDto;
import com.movie.booking.model.Screen;
import com.movie.booking.model.Show;
import com.movie.booking.model.Theatre;
import com.movie.booking.repository.ScreenRepository;
import com.movie.booking.repository.ShowRepository;
import com.movie.booking.repository.TheatreRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BrowseTheatreShowService {

    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final ShowRepository showRepository;

    public BrowseTheatreShowService(TheatreRepository theatreRepository,
                                    ScreenRepository screenRepository,
                                    ShowRepository showRepository) {
        this.theatreRepository = theatreRepository;
        this.screenRepository = screenRepository;
        this.showRepository = showRepository;
    }

    /**
     * Browse theatres currently running the given movie in the city, with show timings for the chosen date.
     */
    public List<TheatreShowDto> findTheatresAndShowsByMovieCityAndDate(Long movieId, String city, LocalDate date) {
        List<Theatre> theatres = theatreRepository.findByCityIgnoreCase(city);
        if (theatres.isEmpty()) return List.of();

        List<Screen> screens = new ArrayList<>();
        for (Theatre t : theatres) {
            screens.addAll(screenRepository.findByTheatreId(t.getId()));
        }
        List<Long> screenIds = screens.stream().map(Screen::getId).toList();
        if (screenIds.isEmpty()) return List.of();

        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Show> shows = showRepository.findByMovieIdAndScreenIdsAndDate(movieId, screenIds, dayStart, dayEnd);
        Map<Long, Theatre> theatreMap = theatres.stream().collect(Collectors.toMap(Theatre::getId, t -> t));
        Map<Long, Screen> screenMap = screens.stream().collect(Collectors.toMap(Screen::getId, s -> s));

        List<TheatreShowDto> result = new ArrayList<>();
        for (Show show : shows) {
            Screen screen = screenMap.get(show.getScreenId());
            if (screen == null) continue;
            Theatre theatre = theatreMap.get(screen.getTheatreId());
            if (theatre == null) continue;
            TheatreShowDto dto = new TheatreShowDto();
            dto.setTheatreId(theatre.getId());
            dto.setTheatreName(theatre.getName());
            dto.setCity(theatre.getCity());
            dto.setScreenId(screen.getId());
            dto.setScreenName(screen.getName());
            dto.setShowId(show.getId());
            dto.setShowTime(show.getShowTime());
            dto.setPrice(show.getPrice());
            result.add(dto);
        }
        return result;
    }
}
