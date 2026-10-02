package com.paytm.project.metrics;

import com.paytm.project.entity.SeatStatus;
import com.paytm.project.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final MeterRegistry registry;
    private final Counter confirmedCounter;

    public ReservationMetrics(MeterRegistry registry, SeatRepository seatRepository) {
        this.registry = registry;
        this.confirmedCounter = Counter.builder("reservations_confirmed_total")
                .description("Total number of successfully confirmed reservations")
                .register(registry);

        registry.gauge("seats_available", seatRepository, repo -> (double) repo.countByStatus(SeatStatus.AVAILABLE));
    }

    public void incrementConfirmed() {
        confirmedCounter.increment();
    }

    public void incrementDeclined(String reason) {
        registry.counter("reservations_declined_total", "reason", reason).increment();
    }
}
