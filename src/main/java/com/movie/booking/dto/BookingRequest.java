
package com.movie.booking.dto;

import java.util.List;

public class BookingRequest {
    private String userId;
    private Long showId;
    private String seatNumber;       // single seat (optional if seatNumbers provided)
    private List<String> seatNumbers; // preferred seats for the day (multi-seat / bulk)

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public List<String> getSeatNumbers() { return seatNumbers; }
    public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
}
