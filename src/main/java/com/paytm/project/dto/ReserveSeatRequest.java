package com.paytm.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveSeatRequest {

    @NotEmpty(message = "seat_numbers cannot be empty")
    @JsonProperty("seat_numbers")
    private List<String> seatNumbers;
}
