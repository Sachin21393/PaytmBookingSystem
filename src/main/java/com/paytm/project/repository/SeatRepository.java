package com.paytm.project.repository;

import com.paytm.project.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {


    @Query("SELECT s FROM Seat s JOIN s.show sh WHERE sh.id = :showId ORDER BY s.seatNumber ASC")
    List<Seat> findByShowIdOrderBySeatNumberAsc(@Param("showId") Long showId);


    @Query("SELECT s FROM Seat s JOIN FETCH s.show sh WHERE sh.id = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber ASC")
    List<Seat> findByShowIdAndSeatNumberInOrderBySeatNumberAsc(
            @Param("showId") Long showId,
            @Param("seatNumbers") Collection<String> seatNumbers
    );
}
