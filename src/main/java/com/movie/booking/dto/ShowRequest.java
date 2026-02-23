package com.movie.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class ShowRequest {
    private Long movieId;
    private Long screenId;
    private Instant showTime;
    private BigDecimal price;

    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }
    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }
    public Instant getShowTime() { return showTime; }
    public void setShowTime(Instant showTime) { this.showTime = showTime; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
