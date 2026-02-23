package com.movie.booking.model;

import jakarta.persistence.*;

@Entity
@Table(name = "booking_seat")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "booking_id")
    private Long bookingId;
    @Column(name = "show_seat_id")
    private Long showSeatId;

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getShowSeatId() { return showSeatId; }
    public void setShowSeatId(Long showSeatId) { this.showSeatId = showSeatId; }
}
