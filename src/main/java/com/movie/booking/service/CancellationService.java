package com.movie.booking.service;

import com.movie.booking.model.Booking;
import com.movie.booking.model.BookingSeat;
import com.movie.booking.repository.BookingRepository;
import com.movie.booking.repository.BookingSeatRepository;
import com.movie.booking.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CancellationService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;

    public CancellationService(BookingRepository bookingRepository,
                               BookingSeatRepository bookingSeatRepository,
                               SeatRepository seatRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public boolean cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) return false;
        if ("CANCELLED".equals(booking.getBookingStatus())) return true;

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        for (BookingSeat bs : bookingSeats) {
            seatRepository.findById(bs.getShowSeatId()).ifPresent(seat -> {
                seat.setStatus("AVAILABLE");
                seatRepository.save(seat);
            });
        }
        bookingSeatRepository.deleteAll(bookingSeats);
        booking.setBookingStatus("CANCELLED");
        bookingRepository.save(booking);
        return true;
    }

    @Transactional
    public int cancelBulk(List<Long> bookingIds) {
        int cancelled = 0;
        for (Long id : bookingIds) {
            if (cancelBooking(id)) cancelled++;
        }
        return cancelled;
    }
}
