package com.movie.booking.dto;

import java.util.List;

public class BulkCancelRequest {
    private List<Long> bookingIds;

    public List<Long> getBookingIds() { return bookingIds; }
    public void setBookingIds(List<Long> bookingIds) { this.bookingIds = bookingIds; }
}
