package com.movie.booking.controller;

import com.movie.booking.dto.BookingRequest;
import com.movie.booking.dto.BookingResponse;
import com.movie.booking.dto.BulkCancelRequest;
import com.movie.booking.service.BookingService;
import com.movie.booking.service.CancellationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@DisplayName("BookingController")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private CancellationService cancellationService;

    @Nested
    @DisplayName("POST /bookings")
    class PostBookings {

        @Test
        @DisplayName("failure: 400 when no seatNumber or seatNumbers provided")
        void badRequestNoSeats() throws Exception {
            BookingRequest request = new BookingRequest();
            request.setUserId("user1");
            request.setShowId(1L);

            mockMvc.perform(post("/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("Provide seatNumber or seatNumbers"));
            // Service not called when request validation fails
        }

        @Test
        @DisplayName("failure: 400 when seatNumbers empty and seatNumber blank")
        void badRequestEmptySeats() throws Exception {
            BookingRequest request = new BookingRequest();
            request.setUserId("user1");
            request.setShowId(1L);
            request.setSeatNumbers(List.of());

            mockMvc.perform(post("/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("success: 200 with seatNumber (single seat)")
        void successSingleSeat() throws Exception {
            BookingRequest request = new BookingRequest();
            request.setUserId("user1");
            request.setShowId(1L);
            request.setSeatNumber("A1");
            BookingResponse response = new BookingResponse();
            response.setBookingId(10L);
            response.setStatus("Booking Confirmed");
            response.setTotalAmount(new BigDecimal("100.00"));
            when(bookingService.bookSeats(eq(1L), eq(List.of("A1")), eq("user1"))).thenReturn(response);

            mockMvc.perform(post("/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(10))
                    .andExpect(jsonPath("$.status").value("Booking Confirmed"))
                    .andExpect(jsonPath("$.totalAmount").value(100.00));
        }

        @Test
        @DisplayName("success: 200 with seatNumbers (multi-seat)")
        void successMultiSeat() throws Exception {
            BookingRequest request = new BookingRequest();
            request.setUserId("user1");
            request.setShowId(1L);
            request.setSeatNumbers(List.of("A1", "A2", "A3"));
            BookingResponse response = new BookingResponse();
            response.setBookingId(20L);
            response.setStatus("Booking Confirmed");
            response.setTotalAmount(new BigDecimal("250.00"));
            when(bookingService.bookSeats(eq(1L), any(), eq("user1"))).thenReturn(response);

            mockMvc.perform(post("/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(20))
                    .andExpect(jsonPath("$.totalAmount").value(250.00));
        }

        @Test
        @DisplayName("success: 200 when service returns failure message (e.g. seat locked)")
        void serviceReturnsFailureMessage() throws Exception {
            BookingRequest request = new BookingRequest();
            request.setUserId("user1");
            request.setShowId(1L);
            request.setSeatNumber("A1");
            BookingResponse response = new BookingResponse();
            response.setStatus("Seat A1 is being booked by another user");
            response.setTotalAmount(BigDecimal.ZERO);
            when(bookingService.bookSeats(any(), any(), any())).thenReturn(response);

            mockMvc.perform(post("/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("Seat A1 is being booked by another user"))
                    .andExpect(jsonPath("$.totalAmount").value(0));
        }
    }

    @Nested
    @DisplayName("DELETE /bookings/{bookingId}")
    class DeleteBooking {

        @Test
        @DisplayName("failure: 404 when booking not found")
        void notFound() throws Exception {
            when(cancellationService.cancelBooking(99L)).thenReturn(false);

            mockMvc.perform(delete("/bookings/99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("success: 200 when cancellation succeeds")
        void success() throws Exception {
            when(cancellationService.cancelBooking(1L)).thenReturn(true);

            mockMvc.perform(delete("/bookings/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("Cancelled"))
                    .andExpect(jsonPath("$.bookingId").value(1));
        }
    }

    @Nested
    @DisplayName("POST /bookings/bulk-cancel")
    class BulkCancel {

        @Test
        @DisplayName("success: 200 with cancelled count")
        void success() throws Exception {
            BulkCancelRequest request = new BulkCancelRequest();
            request.setBookingIds(List.of(1L, 2L, 3L));
            when(cancellationService.cancelBulk(List.of(1L, 2L, 3L))).thenReturn(2);

            mockMvc.perform(post("/bookings/bulk-cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelled").value(2))
                    .andExpect(jsonPath("$.requested").value(3));
        }

        @Test
        @DisplayName("success: 200 when bookingIds null (empty list used)")
        void nullBookingIds() throws Exception {
            mockMvc.perform(post("/bookings/bulk-cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelled").value(0))
                    .andExpect(jsonPath("$.requested").value(0));
        }
    }
}
