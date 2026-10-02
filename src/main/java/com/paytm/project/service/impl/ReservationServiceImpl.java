package com.paytm.project.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.project.dto.ReservationResponse;
import com.paytm.project.dto.ReserveSeatRequest;
import com.paytm.project.entity.IdempotencyRecord;
import com.paytm.project.entity.Reservation;
import com.paytm.project.entity.Seat;
import com.paytm.project.entity.SeatStatus;
import com.paytm.project.entity.Show;
import com.paytm.project.entity.User;
import com.paytm.project.exception.InvalidIdempotencyKeyException;
import com.paytm.project.exception.ResourceNotFoundException;
import com.paytm.project.exception.SeatConflictException;
import com.paytm.project.exception.UserLimitExceededException;
import com.paytm.project.repository.IdempotencyRepository;
import com.paytm.project.repository.ReservationRepository;
import com.paytm.project.repository.SeatRepository;
import com.paytm.project.repository.ShowRepository;
import com.paytm.project.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final com.paytm.project.metrics.ReservationMetrics reservationMetrics;

    @Override
    @Transactional
    public ReservationResponse reserveSeats(Long showId, ReserveSeatRequest request, User user, String idempotencyKey) {
        if (request == null || request.getSeatNumbers() == null || request.getSeatNumbers().isEmpty()) {
            throw new IllegalArgumentException("At least one seat number is required");
        }

        List<String> sortedSeatNumbers = request.getSeatNumbers().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();

        if (sortedSeatNumbers.isEmpty()) {
            throw new IllegalArgumentException("Valid seat numbers must be provided");
        }

        String requestHash = computeRequestHash(showId, sortedSeatNumbers);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existingRecord = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
            if (existingRecord.isPresent()) {
                IdempotencyRecord record = existingRecord.get();
                if (record.getRequestHash().equals(requestHash)) {
                    log.info("Returning cached response for idempotency key: {}", idempotencyKey);
                    reservationMetrics.incrementDeclined("idempotent-replay");
                    return deserializeResponse(record.getResponseBody());
                } else {
                    reservationMetrics.incrementDeclined("idempotent-mismatch");
                    throw new InvalidIdempotencyKeyException(
                            "Idempotency-Key '" + idempotencyKey + "' has already been used with a different request payload"
                    );
                }
            }
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        int perUserLimit = show.getPerUserLimit() != null ? show.getPerUserLimit() : 4;
        long currentConfirmed = reservationRepository.countConfirmedSeatsByUserAndShow(user.getId(), showId);
        if (currentConfirmed + sortedSeatNumbers.size() > perUserLimit) {
            reservationMetrics.incrementDeclined("per-user-limit");
            throw new UserLimitExceededException(
                    "Reservation exceeds per-user limit of " + perUserLimit + " seats (currently confirmed: "
                            + currentConfirmed + ", requested: " + sortedSeatNumbers.size() + ")"
            );
        }

        List<Seat> lockedSeats = seatRepository.findByShowIdAndSeatNumberInForUpdate(showId, sortedSeatNumbers);
        if (lockedSeats.size() != sortedSeatNumbers.size()) {
            throw new ResourceNotFoundException("One or more requested seats do not exist for show " + showId);
        }

        for (Seat seat : lockedSeats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                reservationMetrics.incrementDeclined("seat-taken");
                throw new SeatConflictException("Seat " + seat.getSeatNumber() + " is already booked or held");
            }
        }

        long totalAmountPaise = (long) lockedSeats.size() * show.getPricePaise();
        Reservation reservation = Reservation.builder()
                .show(show)
                .user(user)
                .status("CONFIRMED")
                .totalAmountPaise(totalAmountPaise)
                .idempotencyKey(idempotencyKey)
                .build();

        for (Seat seat : lockedSeats) {
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setReservation(reservation);
            reservation.getSeats().add(seat);
        }

        Reservation savedReservation = reservationRepository.save(reservation);

        ReservationResponse response = ReservationResponse.builder()
                .reservationId(savedReservation.getId())
                .showId(show.getId())
                .userId(user.getId())
                .seats(sortedSeatNumbers)
                .totalAmountPaise(totalAmountPaise)
                .status("CONFIRMED")
                .createdAt(savedReservation.getCreatedAt())
                .build();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .userId(user.getId())
                    .requestHash(requestHash)
                    .responseBody(serializeResponse(response))
                    .build();
            idempotencyRepository.save(record);
        }

        reservationMetrics.incrementConfirmed();
        return response;
    }

    @Override
    @Transactional
    public ReservationResponse cancelReservation(Long reservationId, User user) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + reservationId));

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to cancel this reservation");
        }

        List<Seat> seats = seatRepository.findByReservationId(reservationId);
        List<String> seatNumbers = seats.stream().map(Seat::getSeatNumber).sorted().toList();

        if (!"CANCELLED".equals(reservation.getStatus())) {
            reservation.setStatus("CANCELLED");
            reservationRepository.save(reservation);

            for (Seat seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setReservation(null);
            }
            seatRepository.saveAll(seats);
            log.info("Reservation {} cancelled by user {}. Seats released: {}", reservationId, user.getUsername(), seatNumbers);
        }

        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .showId(reservation.getShow().getId())
                .userId(reservation.getUser().getId())
                .seats(seatNumbers)
                .totalAmountPaise(reservation.getTotalAmountPaise())
                .status("CANCELLED")
                .createdAt(reservation.getCreatedAt())
                .build();
    }

    private String computeRequestHash(Long showId, List<String> sortedSeatNumbers) {
        try {
            String payload = showId + ":" + String.join(",", sortedSeatNumbers);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeResponse(ReservationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reservation response", e);
        }
    }

    private ReservationResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, ReservationResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached reservation response", e);
        }
    }
}
