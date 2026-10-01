package com.paytm.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    @NotBlank(message = "Username/Email cannot be blank")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    private String password;
}
