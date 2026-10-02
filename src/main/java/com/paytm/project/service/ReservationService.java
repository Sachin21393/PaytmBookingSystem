package com.paytm.project.service;

import com.paytm.project.dto.ReservationResponse;
import com.paytm.project.dto.ReserveSeatRequest;
import com.paytm.project.entity.User;

public interface ReservationService {

    ReservationResponse reserveSeats(Long showId, ReserveSeatRequest request, User user, String idempotencyKey);

    ReservationResponse cancelReservation(Long reservationId, User user);
}
