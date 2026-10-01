package com.paytm.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytm.project.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDetailDto {

    @JsonProperty("seat_number")
    private String seatNumber;

    private SeatStatus status;
}
