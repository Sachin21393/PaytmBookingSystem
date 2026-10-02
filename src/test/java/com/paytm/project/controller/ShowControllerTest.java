package com.paytm.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.project.dto.CreateShowRequest;
import com.paytm.project.dto.ShowResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.enabled=true"
})
@AutoConfigureMockMvc
class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateShowAndRetrieveStateWithReconciliation() throws Exception {
        // 1. Create a show with 4 seats
        CreateShowRequest request = CreateShowRequest.builder()
                .name("friday-night")
                .seats(List.of("A1", "A2", "A3", "A4"))
                .pricePaise(25000L)
                .perUserLimit(4)
                .build();

        MvcResult result = mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("friday-night"))
                .andExpect(jsonPath("$.price_paise").value(25000))
                .andExpect(jsonPath("$.total_seats").value(4))
                .andExpect(jsonPath("$.per_user_limit").value(4))
                .andExpect(jsonPath("$.available_count").value(4))
                .andExpect(jsonPath("$.held_count").value(0))
                .andExpect(jsonPath("$.confirmed_count").value(0))
                .andReturn();

        ShowResponse showResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ShowResponse.class
        );
        Long showId = showResponse.getId();
        assertThat(showId).isNotNull();
        assertThat(showResponse.getSeats()).hasSize(4);

        // 2. Fetch show state and verify reconciliation invariant
        MvcResult stateResult = mockMvc.perform(get("/shows/" + showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(showId))
                .andExpect(jsonPath("$.total_seats").value(4))
                .andExpect(jsonPath("$.available_count").value(4))
                .andExpect(jsonPath("$.held_count").value(0))
                .andExpect(jsonPath("$.confirmed_count").value(0))
                .andReturn();

        ShowResponse getResponse = objectMapper.readValue(
                stateResult.getResponse().getContentAsString(),
                ShowResponse.class
        );
        assertThat(getResponse.getAvailableCount() + getResponse.getHeldCount() + getResponse.getConfirmedCount())
                .isEqualTo(getResponse.getTotalSeats().longValue());
    }

    @Test
    void testCreateShowWithDuplicateSeatsIsRejected() throws Exception {
        CreateShowRequest request = CreateShowRequest.builder()
                .name("duplicate-seats-show")
                .seats(List.of("A1", "A2", "A1")) // duplicate A1
                .pricePaise(15000L)
                .build();

        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetNonExistentShowReturns404() throws Exception {
        mockMvc.perform(get("/shows/99999"))
                .andExpect(status().isNotFound());
    }
}
