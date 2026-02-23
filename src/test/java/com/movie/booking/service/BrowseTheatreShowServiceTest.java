package com.movie.booking.service;

import com.movie.booking.dto.TheatreShowDto;
import com.movie.booking.model.Screen;
import com.movie.booking.model.Show;
import com.movie.booking.model.Theatre;
import com.movie.booking.repository.ScreenRepository;
import com.movie.booking.repository.ShowRepository;
import com.movie.booking.repository.TheatreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrowseTheatreShowService")
class BrowseTheatreShowServiceTest {

    @Mock private TheatreRepository theatreRepository;
    @Mock private ScreenRepository screenRepository;
    @Mock private ShowRepository showRepository;

    private BrowseTheatreShowService browseTheatreShowService;
    private static final Long MOVIE_ID = 1L;
    private static final String CITY = "Mumbai";
    private static final LocalDate DATE = LocalDate.of(2025, 2, 25);

    @BeforeEach
    void setUp() {
        browseTheatreShowService = new BrowseTheatreShowService(
                theatreRepository, screenRepository, showRepository
        );
    }

    @Nested
    @DisplayName("findTheatresAndShowsByMovieCityAndDate")
    class FindTheatresAndShows {

        @Test
        @DisplayName("failure: returns empty list when no theatres in city")
        void emptyCity() {
            when(theatreRepository.findByCityIgnoreCase(CITY)).thenReturn(List.of());

            List<TheatreShowDto> result =
                    browseTheatreShowService.findTheatresAndShowsByMovieCityAndDate(MOVIE_ID, CITY, DATE);

            assertThat(result).isEmpty();
            verify(showRepository, never()).findByMovieIdAndScreenIdsAndDate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("failure: returns empty list when theatres have no screens")
        void noScreens() {
            Theatre theatre = new Theatre();
            theatre.setId(1L);
            theatre.setCity(CITY);
            when(theatreRepository.findByCityIgnoreCase(CITY)).thenReturn(List.of(theatre));
            when(screenRepository.findByTheatreId(1L)).thenReturn(List.of());

            List<TheatreShowDto> result =
                    browseTheatreShowService.findTheatresAndShowsByMovieCityAndDate(MOVIE_ID, CITY, DATE);

            assertThat(result).isEmpty();
            verify(showRepository, never()).findByMovieIdAndScreenIdsAndDate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("success: returns DTOs when theatres, screens and shows exist")
        void success() {
            Theatre theatre = new Theatre();
            theatre.setId(1L);
            theatre.setName("PVR");
            theatre.setCity(CITY);
            Screen screen = new Screen();
            screen.setId(10L);
            screen.setTheatreId(1L);
            screen.setName("Screen 1");
            Show show = new Show();
            show.setId(100L);
            show.setScreenId(10L);
            show.setShowTime(DATE.atTime(14, 0).atZone(ZoneOffset.UTC).toInstant());
            show.setPrice(new BigDecimal("200.00"));

            when(theatreRepository.findByCityIgnoreCase(CITY)).thenReturn(List.of(theatre));
            when(screenRepository.findByTheatreId(1L)).thenReturn(List.of(screen));
            Instant dayStart = DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = DATE.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            when(showRepository.findByMovieIdAndScreenIdsAndDate(
                    eq(MOVIE_ID), eq(List.of(10L)), eq(dayStart), eq(dayEnd)))
                    .thenReturn(List.of(show));

            List<TheatreShowDto> result =
                    browseTheatreShowService.findTheatresAndShowsByMovieCityAndDate(MOVIE_ID, CITY, DATE);

            assertThat(result).hasSize(1);
            TheatreShowDto dto = result.get(0);
            assertThat(dto.getTheatreId()).isEqualTo(1L);
            assertThat(dto.getTheatreName()).isEqualTo("PVR");
            assertThat(dto.getCity()).isEqualTo(CITY);
            assertThat(dto.getScreenId()).isEqualTo(10L);
            assertThat(dto.getScreenName()).isEqualTo("Screen 1");
            assertThat(dto.getShowId()).isEqualTo(100L);
            assertThat(dto.getPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("success: returns empty list when no shows for movie/date")
        void noShows() {
            Theatre theatre = new Theatre();
            theatre.setId(1L);
            Screen screen = new Screen();
            screen.setId(10L);
            screen.setTheatreId(1L);
            when(theatreRepository.findByCityIgnoreCase(CITY)).thenReturn(List.of(theatre));
            when(screenRepository.findByTheatreId(1L)).thenReturn(List.of(screen));
            Instant dayStart = DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = DATE.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            when(showRepository.findByMovieIdAndScreenIdsAndDate(
                    eq(MOVIE_ID), eq(List.of(10L)), eq(dayStart), eq(dayEnd)))
                    .thenReturn(List.of());

            List<TheatreShowDto> result =
                    browseTheatreShowService.findTheatresAndShowsByMovieCityAndDate(MOVIE_ID, CITY, DATE);

            assertThat(result).isEmpty();
        }
    }
}
