package com.paytm.project.repository;

import com.paytm.project.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.reservation.user.id = :userId AND s.show.id = :showId AND s.status = com.paytm.project.entity.SeatStatus.CONFIRMED")
    long countConfirmedSeatsByUserAndShow(@Param("userId") Long userId, @Param("showId") Long showId);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
}
