package com.paytm.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.project.dto.AuthRequest;
import com.paytm.project.dto.AuthResponse;
import com.paytm.project.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.enabled=true"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testRegisterLoginAndAccessProtectedEndpointFlow() throws Exception {
        // 1. Register a new user
        RegisterRequest registerReq = RegisterRequest.builder()
                .username("testuser")
                .password("secretPassword123")
                .email("test@paytm.com")
                .fullName("Test User")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andReturn();

        AuthResponse registerResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        assertThat(registerResponse.getToken()).isNotBlank();

        // 2. Obtain Auth Token via POST /api/v1/auth/token
        AuthRequest authReq = AuthRequest.builder()
                .username("testuser")
                .password("secretPassword123")
                .build();

        MvcResult tokenResult = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andReturn();

        AuthResponse tokenResponse = objectMapper.readValue(
                tokenResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String jwtToken = tokenResponse.getToken();
        assertThat(jwtToken).isNotBlank();

        // 3. Access protected /api/v1/auth/me with the generated Bearer token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("testuser"));

        // 4. Invalid credentials should be rejected with 401 Unauthorized
        AuthRequest invalidReq = AuthRequest.builder()
                .username("testuser")
                .password("wrongPassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isUnauthorized());
    }
}
