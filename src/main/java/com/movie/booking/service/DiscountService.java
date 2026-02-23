package com.movie.booking.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Booking platform offers (in selected cities/theatres):
 * - 50% discount on the third ticket
 * - 20% discount for afternoon show tickets
 */
@Service
public class DiscountService {

    private static final BigDecimal THIRD_TICKET_DISCOUNT = new BigDecimal("0.50");
    private static final BigDecimal AFTERNOON_DISCOUNT = new BigDecimal("0.20");
    private static final int AFTERNOON_START_HOUR = 12;
    private static final int AFTERNOON_END_HOUR = 17;

    /**
     * @param basePricePerSeat price for one seat
     * @param seatCount        number of tickets (3rd gets 50% off)
     * @param showTime         afternoon shows get 20% off per ticket
     * @return total amount after applying offers
     */
    public BigDecimal calculateTotal(BigDecimal basePricePerSeat, int seatCount, Instant showTime) {
        if (basePricePerSeat == null || seatCount <= 0) return BigDecimal.ZERO;

        boolean afternoonShow = isAfternoonShow(showTime);
        BigDecimal pricePerSeat = basePricePerSeat;
        if (afternoonShow) {
            pricePerSeat = basePricePerSeat.multiply(BigDecimal.ONE.subtract(AFTERNOON_DISCOUNT)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < seatCount; i++) {
            if (i == 2) {
                total = total.add(pricePerSeat.multiply(BigDecimal.ONE.subtract(THIRD_TICKET_DISCOUNT)).setScale(2, RoundingMode.HALF_UP));
            } else {
                total = total.add(pricePerSeat);
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isAfternoonShow(Instant showTime) {
        if (showTime == null) return false;
        LocalTime time = showTime.atZone(ZoneId.systemDefault()).toLocalTime();
        int hour = time.getHour();
        return hour >= AFTERNOON_START_HOUR && hour < AFTERNOON_END_HOUR;
    }

    public boolean isThirdTicketDiscountApplicable(int seatCount) {
        return seatCount >= 3;
    }
}
