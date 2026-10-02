package com.paytm.project.service.impl;

import com.paytm.project.dto.CreateShowRequest;
import com.paytm.project.dto.SeatDetailDto;
import com.paytm.project.dto.ShowResponse;
import com.paytm.project.entity.Seat;
import com.paytm.project.entity.SeatStatus;
import com.paytm.project.entity.Show;
import com.paytm.project.repository.SeatRepository;
import com.paytm.project.repository.ShowRepository;
import com.paytm.project.service.ShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowServiceImpl implements ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        log.info("Creating show '{}' with {} seats", request.getName(), request.getSeats().size());

        // Validate uniqueness of seat numbers in input
        Set<String> uniqueSeats = new HashSet<>(request.getSeats());
        if (uniqueSeats.size() != request.getSeats().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate seat numbers provided in request");
        }

        Show show = Show.builder()
                .name(request.getName())
                .pricePaise(request.getPricePaise())
                .perUserLimit(request.getPerUserLimit() != null ? request.getPerUserLimit() : 4)
                .totalSeats(request.getSeats().size())
                .build();

        for (String seatNumber : request.getSeats()) {
            Seat seat = Seat.builder()
                    .seatNumber(seatNumber.trim())
                    .status(SeatStatus.AVAILABLE)
                    .build();
            show.addSeat(seat);
        }

        Show savedShow = showRepository.save(show);
        log.info("Show created successfully with id {}", savedShow.getId());

        List<SeatDetailDto> seatDtos = savedShow.getSeats().stream()
                .map(s -> SeatDetailDto.builder()
                        .seatNumber(s.getSeatNumber())
                        .status(s.getStatus())
                        .build())
                .toList();

        return ShowResponse.builder()
                .id(savedShow.getId())
                .name(savedShow.getName())
                .pricePaise(savedShow.getPricePaise())
                .perUserLimit(savedShow.getPerUserLimit())
                .totalSeats(savedShow.getTotalSeats())
                .availableCount((long) savedShow.getTotalSeats())
                .heldCount(0L)
                .confirmedCount(0L)
                .createdAt(savedShow.getCreatedAt())
                .seats(seatDtos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShowResponse getShow(Long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found with id: " + showId));

        List<Seat> seats = seatRepository.findByShowIdOrderBySeatNumberAsc(showId);

        long availableCount = 0;
        long heldCount = 0;
        long confirmedCount = 0;

        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                availableCount++;
            } else if (seat.getStatus() == SeatStatus.HELD) {
                heldCount++;
            } else if (seat.getStatus() == SeatStatus.CONFIRMED) {
                confirmedCount++;
            }
        }

        List<SeatDetailDto> seatDtos = seats.stream()
                .map(s -> SeatDetailDto.builder()
                        .seatNumber(s.getSeatNumber())
                        .status(s.getStatus())
                        .build())
                .toList();

        return ShowResponse.builder()
                .id(show.getId())
                .name(show.getName())
                .pricePaise(show.getPricePaise())
                .perUserLimit(show.getPerUserLimit())
                .totalSeats(show.getTotalSeats())
                .availableCount(availableCount)
                .heldCount(heldCount)
                .confirmedCount(confirmedCount)
                .createdAt(show.getCreatedAt())
                .seats(seatDtos)
                .build();
    }
}
