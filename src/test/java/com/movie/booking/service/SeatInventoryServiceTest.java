package com.movie.booking.service;

import com.movie.booking.model.Screen;
import com.movie.booking.model.Show;
import com.movie.booking.model.ShowSeat;
import com.movie.booking.repository.ScreenRepository;
import com.movie.booking.repository.SeatRepository;
import com.movie.booking.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatInventoryService")
class SeatInventoryServiceTest {

    @Mock private ShowRepository showRepository;
    @Mock private ScreenRepository screenRepository;
    @Mock private SeatRepository seatRepository;

    private SeatInventoryService seatInventoryService;
    private static final Long SHOW_ID = 1L;
    private static final Long THEATRE_ID = 10L;
    private static final Long SCREEN_ID = 100L;

    @BeforeEach
    void setUp() {
        seatInventoryService = new SeatInventoryService(
                showRepository, screenRepository, seatRepository
        );
    }

    @Nested
    @DisplayName("allocateSeats")
    class AllocateSeats {

        @Test
        @DisplayName("failure: throws when show not found")
        void showNotFound() {
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    seatInventoryService.allocateSeats(SHOW_ID, THEATRE_ID, List.of("A1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Show not found");
        }

        @Test
        @DisplayName("failure: throws when screen not found")
        void screenNotFound() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    seatInventoryService.allocateSeats(SHOW_ID, THEATRE_ID, List.of("A1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Screen not found");
        }

        @Test
        @DisplayName("failure: throws when show does not belong to theatre")
        void wrongTheatre() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            Screen screen = new Screen();
            screen.setTheatreId(999L);
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));

            assertThatThrownBy(() ->
                    seatInventoryService.allocateSeats(SHOW_ID, THEATRE_ID, List.of("A1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to this theatre");
        }

        @Test
        @DisplayName("success: creates new seats")
        void success() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            Screen screen = new Screen();
            screen.setTheatreId(THEATRE_ID);
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));
            when(seatRepository.findByShowIdAndSeatNumber(SHOW_ID, "A1")).thenReturn(Optional.empty());
            when(seatRepository.findByShowIdAndSeatNumber(SHOW_ID, "A2")).thenReturn(Optional.empty());
            when(seatRepository.save(any(ShowSeat.class))).thenAnswer(i -> {
                ShowSeat s = i.getArgument(0);
                s.setId(1L);
                return s;
            });

            int created = seatInventoryService.allocateSeats(SHOW_ID, THEATRE_ID, List.of("A1", "A2"));

            assertThat(created).isEqualTo(2);
            verify(seatRepository, times(2)).save(argThat(s ->
                    s.getShowId().equals(SHOW_ID) && "AVAILABLE".equals(s.getStatus())));
        }

        @Test
        @DisplayName("success: skips already existing seat numbers")
        void skipsExisting() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            Screen screen = new Screen();
            screen.setTheatreId(THEATRE_ID);
            ShowSeat existing = new ShowSeat();
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));
            when(seatRepository.findByShowIdAndSeatNumber(SHOW_ID, "A1")).thenReturn(Optional.of(existing));
            when(seatRepository.findByShowIdAndSeatNumber(SHOW_ID, "A2")).thenReturn(Optional.empty());
            when(seatRepository.save(any(ShowSeat.class))).thenAnswer(i -> i.getArgument(0));

            int created = seatInventoryService.allocateSeats(SHOW_ID, THEATRE_ID, List.of("A1", "A2"));

            assertThat(created).isEqualTo(1);
            verify(seatRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("updateSeatStatus")
    class UpdateSeatStatus {

        @Test
        @DisplayName("failure: throws when show not found")
        void showNotFound() {
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    seatInventoryService.updateSeatStatus(SHOW_ID, THEATRE_ID, "A1", "LOCKED"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Show not found");
        }

        @Test
        @DisplayName("failure: throws when show does not belong to theatre")
        void wrongTheatre() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            Screen screen = new Screen();
            screen.setTheatreId(999L);
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));

            assertThatThrownBy(() ->
                    seatInventoryService.updateSeatStatus(SHOW_ID, THEATRE_ID, "A1", "LOCKED"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to this theatre");
        }

        @Test
        @DisplayName("failure: throws when seat not found")
        void seatNotFound() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            Screen screen = new Screen();
            screen.setTheatreId(THEATRE_ID);
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));
            when(seatRepository.findByShowIdAndSeatNumber(SHOW_ID, "A1")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    seatInventoryService.updateSeatStatus(SHOW_ID, THEATRE_ID, "A1", "LOCKED"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Seat not found");
        }

        @Test
        @DisplayName("failure: throws when seat is BOOKED")
        void cannotChangeBookedSeat() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            Screen screen = new Screen();
            screen.setTheatreId(THEATRE_ID);
            ShowSeat seat = new ShowSeat();
            seat.setStatus("BOOKED");
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));
            when(seatRepository.findByShowIdAndSeatNumber(SHOW_ID, "A1")).thenReturn(Optional.of(seat));

            assertThatThrownBy(() ->
                    seatInventoryService.updateSeatStatus(SHOW_ID, THEATRE_ID, "A1", "AVAILABLE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot change status of a booked seat");
        }

        @Test
        @DisplayName("success: updates seat status")
        void success() {
            Show show = new Show();
            show.setScreenId(SCREEN_ID);
            Screen screen = new Screen();
            screen.setTheatreId(THEATRE_ID);
            ShowSeat seat = new ShowSeat();
            seat.setId(1L);
            seat.setStatus("AVAILABLE");
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));
            when(seatRepository.findByShowIdAndSeatNumber(SHOW_ID, "A1")).thenReturn(Optional.of(seat));
            when(seatRepository.save(any(ShowSeat.class))).thenAnswer(i -> i.getArgument(0));

            ShowSeat result = seatInventoryService.updateSeatStatus(SHOW_ID, THEATRE_ID, "A1", "LOCKED");

            assertThat(result.getStatus()).isEqualTo("LOCKED");
            verify(seatRepository).save(argThat(s -> "LOCKED".equals(s.getStatus())));
        }
    }
}
