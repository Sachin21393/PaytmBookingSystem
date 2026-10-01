package com.paytm.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShowRequest {

    @NotBlank(message = "Show name is required")
    private String name;

    @NotEmpty(message = "Seats list cannot be empty")
    private List<String> seats;

    @NotNull(message = "Price in paise is required")
    @Min(value = 1, message = "Price in paise must be greater than zero")
    @JsonProperty("price_paise")
    private Long pricePaise;

    @Min(value = 1, message = "Per-user limit must be at least 1")
    @JsonProperty("per_user_limit")
    @Builder.Default
    private Integer perUserLimit = 4;
}
