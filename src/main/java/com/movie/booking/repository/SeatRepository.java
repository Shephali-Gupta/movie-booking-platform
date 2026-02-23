
package com.movie.booking.repository;

import com.movie.booking.model.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<ShowSeat, Long> {
    Optional<ShowSeat> findByShowIdAndSeatNumber(Long showId, String seatNumber);
    List<ShowSeat> findByShowId(Long showId);
    List<ShowSeat> findByShowIdAndSeatNumberIn(Long showId, List<String> seatNumbers);
}
