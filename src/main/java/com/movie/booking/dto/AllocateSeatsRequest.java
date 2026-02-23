package com.movie.booking.dto;

import java.util.List;

public class AllocateSeatsRequest {
    private List<String> seatNumbers;

    public List<String> getSeatNumbers() { return seatNumbers; }
    public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
}
