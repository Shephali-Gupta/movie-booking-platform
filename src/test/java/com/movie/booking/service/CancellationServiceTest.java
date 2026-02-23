package com.movie.booking.service;

import com.movie.booking.model.Booking;
import com.movie.booking.model.BookingSeat;
import com.movie.booking.model.ShowSeat;
import com.movie.booking.repository.BookingRepository;
import com.movie.booking.repository.BookingSeatRepository;
import com.movie.booking.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancellationService")
class CancellationServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private SeatRepository seatRepository;

    private CancellationService cancellationService;
    private static final Long BOOKING_ID = 1L;

    @BeforeEach
    void setUp() {
        cancellationService = new CancellationService(
                bookingRepository, bookingSeatRepository, seatRepository
        );
    }

    @Nested
    @DisplayName("cancelBooking")
    class CancelBooking {

        @Test
        @DisplayName("failure: returns false when booking not found")
        void bookingNotFound() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

            boolean result = cancellationService.cancelBooking(BOOKING_ID);

            assertThat(result).isFalse();
            verify(bookingSeatRepository, never()).findByBookingId(any());
        }

        @Test
        @DisplayName("success: idempotent when already CANCELLED")
        void alreadyCancelled() {
            Booking booking = new Booking();
            booking.setId(BOOKING_ID);
            booking.setBookingStatus("CANCELLED");
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

            boolean result = cancellationService.cancelBooking(BOOKING_ID);

            assertThat(result).isTrue();
            verify(bookingSeatRepository, never()).findByBookingId(any());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("success: cancels booking and releases seats")
        void success() {
            Booking booking = new Booking();
            booking.setId(BOOKING_ID);
            booking.setBookingStatus("CONFIRMED");
            BookingSeat bs = new BookingSeat();
            bs.setId(1L);
            bs.setBookingId(BOOKING_ID);
            bs.setShowSeatId(100L);
            ShowSeat seat = new ShowSeat();
            seat.setId(100L);
            seat.setStatus("BOOKED");
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(bookingSeatRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of(bs));
            when(seatRepository.findById(100L)).thenReturn(Optional.of(seat));

            boolean result = cancellationService.cancelBooking(BOOKING_ID);

            assertThat(result).isTrue();
            verify(seatRepository).save(argThat(s -> "AVAILABLE".equals(s.getStatus())));
            verify(bookingSeatRepository).deleteAll(any());
            ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(captor.capture());
            assertThat(captor.getValue().getBookingStatus()).isEqualTo("CANCELLED");
        }
    }

    @Nested
    @DisplayName("cancelBulk")
    class CancelBulk {

        @Test
        @DisplayName("success: cancels all existing bookings")
        void allCancelled() {
            Booking b1 = new Booking();
            b1.setId(1L);
            b1.setBookingStatus("CONFIRMED");
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(b1));
            when(bookingSeatRepository.findByBookingId(1L)).thenReturn(List.of());

            int count = cancellationService.cancelBulk(List.of(1L, 2L));

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("success: empty list returns 0")
        void emptyList() {
            int count = cancellationService.cancelBulk(List.of());
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("success: two bookings both cancelled")
        void twoCancelled() {
            Booking b1 = new Booking();
            b1.setId(1L);
            b1.setBookingStatus("CONFIRMED");
            Booking b2 = new Booking();
            b2.setId(2L);
            b2.setBookingStatus("CONFIRMED");
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(b1));
            when(bookingRepository.findById(2L)).thenReturn(Optional.of(b2));
            when(bookingSeatRepository.findByBookingId(1L)).thenReturn(List.of());
            when(bookingSeatRepository.findByBookingId(2L)).thenReturn(List.of());

            int count = cancellationService.cancelBulk(List.of(1L, 2L));
            assertThat(count).isEqualTo(2);
        }
    }
}
