package com.movie.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class TheatreShowDto {
    private Long theatreId;
    private String theatreName;
    private String city;
    private Long screenId;
    private String screenName;
    private Long showId;
    private Instant showTime;
    private BigDecimal price;

    public Long getTheatreId() { return theatreId; }
    public void setTheatreId(Long theatreId) { this.theatreId = theatreId; }
    public String getTheatreName() { return theatreName; }
    public void setTheatreName(String theatreName) { this.theatreName = theatreName; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }
    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }
    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }
    public Instant getShowTime() { return showTime; }
    public void setShowTime(Instant showTime) { this.showTime = showTime; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
