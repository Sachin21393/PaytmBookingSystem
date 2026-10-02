package com.paytm.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    @JsonProperty("reservation_id")
    private Long reservationId;

    @JsonProperty("show_id")
    private Long showId;

    @JsonProperty("user_id")
    private Long userId;

    private List<String> seats;

    @JsonProperty("total_amount_paise")
    private Long totalAmountPaise;

    @JsonProperty("amount_paise")
    public Long getAmountPaise() {
        return totalAmountPaise;
    }

    private String status;

    @JsonProperty("created_at")
    private Instant createdAt;
}
