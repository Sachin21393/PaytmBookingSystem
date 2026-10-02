package com.paytm.project.controller;

import com.paytm.project.dto.ReservationResponse;
import com.paytm.project.dto.ReserveSeatRequest;
import com.paytm.project.entity.User;
import com.paytm.project.exception.ResourceNotFoundException;
import com.paytm.project.repository.UserRepository;
import com.paytm.project.security.RequiresAuthentication;
import com.paytm.project.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = {"/shows", "/api/v1/shows"})
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final UserRepository userRepository;

    @PostMapping("/{showId}/reserve")
    @RequiresAuthentication
    public ResponseEntity<ReservationResponse> reserveSeats(
            @PathVariable("showId") Long showId,
            @Valid @RequestBody ReserveSeatRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency-Key header cannot be blank");
        }

        User user = userRepository.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + jwt.getSubject()));

        ReservationResponse response = reservationService.reserveSeats(showId, request, user, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
