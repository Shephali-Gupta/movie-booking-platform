package com.movie.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiscountService")
class DiscountServiceTest {

    private DiscountService discountService;
    private static final BigDecimal PRICE = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        discountService = new DiscountService();
    }

    @Nested
    @DisplayName("calculateTotal")
    class CalculateTotal {

        @Test
        @DisplayName("returns zero when base price is null")
        void nullPrice() {
            assertThat(discountService.calculateTotal(null, 2, Instant.now()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns zero when seat count is zero")
        void zeroSeats() {
            assertThat(discountService.calculateTotal(PRICE, 0, Instant.now()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns zero when seat count is negative")
        void negativeSeats() {
            assertThat(discountService.calculateTotal(PRICE, -1, Instant.now()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("success: single seat, morning show (no afternoon discount)")
        void singleSeatMorning() {
            Instant morning = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant();
            BigDecimal total = discountService.calculateTotal(PRICE, 1, morning);
            assertThat(total).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("success: two seats, no third-ticket discount")
        void twoSeats() {
            BigDecimal total = discountService.calculateTotal(PRICE, 2, Instant.now());
            assertThat(total).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("success: three seats — 50% off on third ticket")
        void threeSeatsThirdTicketDiscount() {
            Instant morning = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant();
            BigDecimal total = discountService.calculateTotal(PRICE, 3, morning);
            assertThat(total).isEqualByComparingTo(new BigDecimal("250.00")); // 100 + 100 + 50
        }

        @Test
        @DisplayName("success: afternoon show — 20% off per ticket")
        void afternoonShowDiscount() {
            Instant afternoon = LocalDate.now().atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant();
            BigDecimal total = discountService.calculateTotal(PRICE, 1, afternoon);
            assertThat(total).isEqualByComparingTo(new BigDecimal("80.00")); // 100 * 0.8
        }

        @Test
        @DisplayName("success: afternoon + three seats (20% off each, 50% off 3rd)")
        void afternoonAndThirdTicket() {
            Instant afternoon = LocalDate.now().atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant();
            BigDecimal pricePerSeat = PRICE.multiply(new BigDecimal("0.80")); // 80
            BigDecimal expected = pricePerSeat.add(pricePerSeat).add(pricePerSeat.multiply(new BigDecimal("0.50")));
            BigDecimal total = discountService.calculateTotal(PRICE, 3, afternoon);
            assertThat(total).isEqualByComparingTo(expected.setScale(2, java.math.RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("success: null showTime treated as not afternoon")
        void nullShowTime() {
            BigDecimal total = discountService.calculateTotal(PRICE, 2, null);
            assertThat(total).isEqualByComparingTo(new BigDecimal("200.00"));
        }
    }

    @Nested
    @DisplayName("isAfternoonShow")
    class IsAfternoonShow {

        @Test
        @DisplayName("returns false when showTime is null")
        void nullTime() {
            assertThat(discountService.isAfternoonShow(null)).isFalse();
        }

        @Test
        @DisplayName("returns false before noon")
        void beforeNoon() {
            Instant morning = LocalDate.now().atTime(11, 59).atZone(ZoneId.systemDefault()).toInstant();
            assertThat(discountService.isAfternoonShow(morning)).isFalse();
        }

        @Test
        @DisplayName("returns true at 12:00")
        void atNoon() {
            Instant noon = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
            assertThat(discountService.isAfternoonShow(noon)).isTrue();
        }

        @Test
        @DisplayName("returns true between 12 and 17")
        void duringAfternoon() {
            Instant afternoon = LocalDate.now().atTime(14, 30).atZone(ZoneId.systemDefault()).toInstant();
            assertThat(discountService.isAfternoonShow(afternoon)).isTrue();
        }

        @Test
        @DisplayName("returns false at 17:00 (end exclusive)")
        void atFivePM() {
            Instant five = LocalDate.now().atTime(17, 0).atZone(ZoneId.systemDefault()).toInstant();
            assertThat(discountService.isAfternoonShow(five)).isFalse();
        }
    }

    @Nested
    @DisplayName("isThirdTicketDiscountApplicable")
    class IsThirdTicketDiscountApplicable {

        @Test
        @DisplayName("returns false for 2 seats")
        void twoSeats() {
            assertThat(discountService.isThirdTicketDiscountApplicable(2)).isFalse();
        }

        @Test
        @DisplayName("returns true for 3 seats")
        void threeSeats() {
            assertThat(discountService.isThirdTicketDiscountApplicable(3)).isTrue();
        }

        @Test
        @DisplayName("returns true for more than 3")
        void moreThanThree() {
            assertThat(discountService.isThirdTicketDiscountApplicable(5)).isTrue();
        }
    }
}
