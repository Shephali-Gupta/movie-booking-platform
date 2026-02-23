package com.movie.booking.service;

import com.movie.booking.dto.BookingResponse;
import com.movie.booking.locking.SeatLockManager;
import com.movie.booking.model.Booking;
import com.movie.booking.model.BookingSeat;
import com.movie.booking.model.Show;
import com.movie.booking.model.ShowSeat;
import com.movie.booking.repository.BookingRepository;
import com.movie.booking.repository.BookingSeatRepository;
import com.movie.booking.repository.SeatRepository;
import com.movie.booking.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService")
class BookingServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private ShowRepository showRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private SeatLockManager lockManager;
    @Mock private DiscountService discountService;

    private BookingService bookingService;
    private static final Long SHOW_ID = 1L;
    private static final String USER_ID = "user1";

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                seatRepository, showRepository, bookingRepository, bookingSeatRepository,
                lockManager, discountService
        );
    }

    @Nested
    @DisplayName("bookSeats - failure scenarios")
    class BookSeatsFailure {

        @Test
        @DisplayName("returns No seats selected when seatNumbers is null")
        void nullSeatNumbers() {
            BookingResponse resp = bookingService.bookSeats(SHOW_ID, null, USER_ID);
            assertThat(resp.getStatus()).isEqualTo("No seats selected");
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verifyNoInteractions(showRepository, lockManager, seatRepository);
        }

        @Test
        @DisplayName("returns No seats selected when seatNumbers is empty")
        void emptySeatNumbers() {
            BookingResponse resp = bookingService.bookSeats(SHOW_ID, List.of(), USER_ID);
            assertThat(resp.getStatus()).isEqualTo("No seats selected");
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns Show not found when show does not exist")
        void showNotFound() {
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.empty());

            BookingResponse resp = bookingService.bookSeats(SHOW_ID, List.of("A1"), USER_ID);

            assertThat(resp.getStatus()).isEqualTo("Show not found");
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(lockManager, never()).lockSeat(any(), any());
        }

        @Test
        @DisplayName("returns seat locked message when another user holds lock")
        void lockFailed() {
            Show show = createShow(SHOW_ID, new BigDecimal("100"));
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(lockManager.lockSeat(SHOW_ID + "-A1", USER_ID)).thenReturn(false);

            BookingResponse resp = bookingService.bookSeats(SHOW_ID, List.of("A1"), USER_ID);

            assertThat(resp.getStatus()).isEqualTo("Seat A1 is being booked by another user");
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(seatRepository, never()).findByShowIdAndSeatNumberIn(any(), any());
        }

        @Test
        @DisplayName("returns One or more seats not found when seat count mismatch")
        void seatsNotFoundForShow() {
            Show show = createShow(SHOW_ID, new BigDecimal("100"));
            ShowSeat seat = createShowSeat(1L, SHOW_ID, "A1", "AVAILABLE");
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(lockManager.lockSeat(any(), eq(USER_ID))).thenReturn(true);
            when(seatRepository.findByShowIdAndSeatNumberIn(SHOW_ID, List.of("A1", "A2")))
                    .thenReturn(List.of(seat)); // only one seat returned

            BookingResponse resp = bookingService.bookSeats(SHOW_ID, List.of("A1", "A2"), USER_ID);

            assertThat(resp.getStatus()).isEqualTo("One or more seats not found for this show");
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns seat not available when seat status is not AVAILABLE")
        void seatNotAvailable() {
            Show show = createShow(SHOW_ID, new BigDecimal("100"));
            ShowSeat seat = createShowSeat(1L, SHOW_ID, "A1", "BOOKED");
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(lockManager.lockSeat(any(), eq(USER_ID))).thenReturn(true);
            when(seatRepository.findByShowIdAndSeatNumberIn(SHOW_ID, List.of("A1"))).thenReturn(List.of(seat));

            BookingResponse resp = bookingService.bookSeats(SHOW_ID, List.of("A1"), USER_ID);

            assertThat(resp.getStatus()).isEqualTo("Seat A1 is not available");
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(bookingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("bookSeats - success scenarios")
    class BookSeatsSuccess {

        @Test
        @DisplayName("success: single seat booking")
        void singleSeatSuccess() {
            Show show = createShow(SHOW_ID, new BigDecimal("100"));
            ShowSeat seat = createShowSeat(1L, SHOW_ID, "A1", "AVAILABLE");
            Booking savedBooking = new Booking();
            savedBooking.setId(10L);
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(lockManager.lockSeat(SHOW_ID + "-A1", USER_ID)).thenReturn(true);
            when(seatRepository.findByShowIdAndSeatNumberIn(SHOW_ID, List.of("A1"))).thenReturn(List.of(seat));
            when(discountService.calculateTotal(any(), eq(1), any())).thenReturn(new BigDecimal("100.00"));
            when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

            BookingResponse resp = bookingService.bookSeats(SHOW_ID, List.of("A1"), USER_ID);

            assertThat(resp.getStatus()).isEqualTo("Booking Confirmed");
            assertThat(resp.getBookingId()).isEqualTo(10L);
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            verify(seatRepository).save(argThat(s -> "BOOKED".equals(s.getStatus())));
            verify(bookingSeatRepository).save(any(BookingSeat.class));
            verify(lockManager).releaseSeat(SHOW_ID + "-A1");
        }

        @Test
        @DisplayName("success: multi-seat booking")
        void multiSeatSuccess() {
            Show show = createShow(SHOW_ID, new BigDecimal("100"));
            ShowSeat s1 = createShowSeat(1L, SHOW_ID, "A1", "AVAILABLE");
            ShowSeat s2 = createShowSeat(2L, SHOW_ID, "A2", "AVAILABLE");
            Booking savedBooking = new Booking();
            savedBooking.setId(20L);
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
            when(lockManager.lockSeat(any(), eq(USER_ID))).thenReturn(true);
            when(seatRepository.findByShowIdAndSeatNumberIn(SHOW_ID, List.of("A1", "A2")))
                    .thenReturn(List.of(s1, s2));
            when(discountService.calculateTotal(any(), eq(2), any())).thenReturn(new BigDecimal("200.00"));
            when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

            BookingResponse resp = bookingService.bookSeats(SHOW_ID, List.of("A1", "A2"), USER_ID);

            assertThat(resp.getStatus()).isEqualTo("Booking Confirmed");
            assertThat(resp.getBookingId()).isEqualTo(20L);
            assertThat(resp.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
            verify(bookingSeatRepository, times(2)).save(any(BookingSeat.class));
        }
    }

    private static Show createShow(Long id, BigDecimal price) {
        Show show = new Show();
        show.setId(id);
        show.setPrice(price);
        show.setShowTime(Instant.now());
        return show;
    }

    private static ShowSeat createShowSeat(Long id, Long showId, String seatNumber, String status) {
        ShowSeat seat = new ShowSeat();
        seat.setId(id);
        seat.setShowId(showId);
        seat.setSeatNumber(seatNumber);
        seat.setStatus(status);
        return seat;
    }
}
