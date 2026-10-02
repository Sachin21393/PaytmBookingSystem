package com.paytm.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShowResponse {

    private Long id;
    private String name;

    @JsonProperty("price_paise")
    private Long pricePaise;

    @JsonProperty("per_user_limit")
    private Integer perUserLimit;

    @JsonProperty("total_seats")
    private Integer totalSeats;

    @JsonProperty("available_count")
    private Long availableCount;

    @JsonProperty("held_count")
    private Long heldCount;

    @JsonProperty("confirmed_count")
    private Long confirmedCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    private List<SeatDetailDto> seats;
}
