package com.movie.booking.repository;

import com.movie.booking.model.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByMovieId(Long movieId);

    @Query("SELECT s FROM Show s WHERE s.movieId = :movieId AND s.screenId IN :screenIds " +
           "AND s.showTime >= :dayStart AND s.showTime < :dayEnd ORDER BY s.showTime")
    List<Show> findByMovieIdAndScreenIdsAndDate(
            @Param("movieId") Long movieId,
            @Param("screenIds") List<Long> screenIds,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);
}
