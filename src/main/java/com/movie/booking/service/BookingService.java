package com.movie.booking.service;

import com.movie.booking.dto.BookingResponse;
import com.movie.booking.model.Booking;
import com.movie.booking.model.BookingSeat;
import com.movie.booking.model.Show;
import com.movie.booking.model.ShowSeat;
import com.movie.booking.repository.BookingRepository;
import com.movie.booking.repository.BookingSeatRepository;
import com.movie.booking.repository.SeatRepository;
import com.movie.booking.repository.ShowRepository;
import com.movie.booking.locking.SeatLockManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatLockManager lockManager;
    private final DiscountService discountService;

    public BookingService(SeatRepository seatRepository,
                          ShowRepository showRepository,
                          BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          SeatLockManager lockManager,
                          DiscountService discountService) {
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.lockManager = lockManager;
        this.discountService = discountService;
    }

    /**
     * Book one or more seats for a show. Applies offers: 50% on 3rd ticket, 20% afternoon discount.
     */
    @Transactional
    public BookingResponse bookSeats(Long showId, List<String> seatNumbers, String userId) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            BookingResponse resp = new BookingResponse();
            resp.setStatus("No seats selected");
            resp.setTotalAmount(BigDecimal.ZERO);
            return resp;
        }

        Optional<Show> showOpt = showRepository.findById(showId);
        if (showOpt.isEmpty()) {
            BookingResponse resp = new BookingResponse();
            resp.setStatus("Show not found");
            resp.setTotalAmount(BigDecimal.ZERO);
            return resp;
        }
        Show show = showOpt.get();
        BigDecimal basePrice = show.getPrice();
        int count = seatNumbers.size();

        List<String> lockKeys = new ArrayList<>();
        try {
            for (String seatNo : seatNumbers) {
                String lockKey = showId + "-" + seatNo;
                if (!lockManager.lockSeat(lockKey, userId)) {
                    BookingResponse resp = new BookingResponse();
                    resp.setStatus("Seat " + seatNo + " is being booked by another user");
                    resp.setTotalAmount(BigDecimal.ZERO);
                    return resp;
                }
                lockKeys.add(lockKey);
            }

            List<ShowSeat> seats = seatRepository.findByShowIdAndSeatNumberIn(showId, seatNumbers);
            if (seats.size() != seatNumbers.size()) {
                BookingResponse resp = new BookingResponse();
                resp.setStatus("One or more seats not found for this show");
                resp.setTotalAmount(BigDecimal.ZERO);
                return resp;
            }
            for (ShowSeat seat : seats) {
                if (!"AVAILABLE".equals(seat.getStatus())) {
                    BookingResponse resp = new BookingResponse();
                    resp.setStatus("Seat " + seat.getSeatNumber() + " is not available");
                    resp.setTotalAmount(BigDecimal.ZERO);
                    return resp;
                }
            }

            BigDecimal totalAmount = discountService.calculateTotal(basePrice, count, show.getShowTime());

            Booking booking = new Booking();
            booking.setUserId(userId);
            booking.setShowId(showId);
            booking.setBookingStatus("CONFIRMED");
            booking.setTotalAmount(totalAmount);
            booking = bookingRepository.save(booking);

            for (ShowSeat seat : seats) {
                seat.setStatus("BOOKED");
                seatRepository.save(seat);
                BookingSeat bs = new BookingSeat();
                bs.setBookingId(booking.getId());
                bs.setShowSeatId(seat.getId());
                bookingSeatRepository.save(bs);
            }

            BookingResponse resp = new BookingResponse();
            resp.setBookingId(booking.getId());
            resp.setStatus("Booking Confirmed");
            resp.setTotalAmount(totalAmount);
            return resp;
        } finally {
            for (String lockKey : lockKeys) {
                lockManager.releaseSeat(lockKey);
            }
        }
    }
}
