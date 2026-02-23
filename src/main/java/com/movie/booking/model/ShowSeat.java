
package com.movie.booking.model;

import jakarta.persistence.*;

@Entity
@Table(name = "show_seat")
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id")
    private Long showId;
    @Column(name = "seat_number")
    private String seatNumber;
    private String status;

    @Version
    private Integer version;

    @Column(name = "locked_until")
    private java.time.Instant lockedUntil;

    // getters & setters
    public Long getId() { return id; }
    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public java.time.Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(java.time.Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}
