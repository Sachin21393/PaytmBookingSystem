package com.paytm.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.project.dto.CreateShowRequest;
import com.paytm.project.dto.ReservationResponse;
import com.paytm.project.dto.ReserveSeatRequest;
import com.paytm.project.dto.ShowResponse;
import com.paytm.project.entity.User;
import com.paytm.project.repository.UserRepository;
import com.paytm.project.security.JwtTokenService;
import com.paytm.project.service.ShowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.enabled=true"
})
@AutoConfigureMockMvc
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShowService showService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User testUser;
    private String jwtToken;
    private Long showId;

    @BeforeEach
    void setUp() {
        String username = "user_" + UUID.randomUUID().toString().substring(0, 8);
        testUser = userRepository.save(User.builder()
                .username(username)
                .password("password123")
                .email(username + "@paytm.com")
                .role("ROLE_USER")
                .build());

        jwtToken = jwtTokenService.generateToken(username);

        ShowResponse show = showService.createShow(CreateShowRequest.builder()
                .name("Show-" + UUID.randomUUID().toString().substring(0, 8))
                .seats(List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8"))
                .pricePaise(10000L)
                .perUserLimit(4)
                .build());
        showId = show.getId();
    }

    @Test
    void testSuccessfulReservation() throws Exception {
        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A1", "A2"))
                .build();

        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservation_id").isNumber())
                .andExpect(jsonPath("$.show_id").value(showId))
                .andExpect(jsonPath("$.total_amount_paise").value(20000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.seats").isArray());
    }

    @Test
    void testIdempotencyReplayReturnsIdenticalResponse() throws Exception {
        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A3"))
                .build();

        String idempotencyKey = UUID.randomUUID().toString();

        // First call
        MvcResult firstResult = mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ReservationResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(),
                ReservationResponse.class
        );

        // Second call with identical key & body
        MvcResult secondResult = mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ReservationResponse secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(),
                ReservationResponse.class
        );

        assertThat(secondResponse.getReservationId()).isEqualTo(firstResponse.getReservationId());
        assertThat(secondResponse.getSeats()).containsExactlyElementsOf(firstResponse.getSeats());
    }

    @Test
    void testIdempotencyReusedWithDifferentPayloadFails() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        ReserveSeatRequest request1 = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A4"))
                .build();

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Same idempotency key with DIFFERENT seat
        ReserveSeatRequest request2 = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A5"))
                .build();

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict());
    }

    @Test
    void testPerUserLimitEnforced() throws Exception {
        // Attempting to reserve 5 seats when limit is 4
        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A1", "A2", "A3", "A4", "A5"))
                .build();

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("USER_LIMIT_EXCEEDED"));
    }

    @Test
    void testReservationWithoutIdempotencyKeyIsRejected400() throws Exception {
        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A1"))
                .build();

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void testReservationWithIdempotencyKeyInBodySucceeds() throws Exception {
        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A7"))
                .idempotencyKey("BODY-KEY-" + UUID.randomUUID())
                .build();

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.amount_paise").value(10000));
    }

    @Test
    void testCancelReservationByOwnerAndRebook() throws Exception {
        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A8"))
                .idempotencyKey("CANCEL-TEST-" + UUID.randomUUID())
                .build();

        String resJson = mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ReservationResponse response = objectMapper.readValue(resJson, ReservationResponse.class);
        Long reservationId = response.getReservationId();

        // 1. Cancel reservation
        mockMvc.perform(post("/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 2. Re-book the same seat by another user
        String otherUser = "other_user_" + UUID.randomUUID().toString().substring(0, 6);
        userRepository.save(User.builder().username(otherUser).password("pass").build());
        String otherToken = jwtTokenService.generateToken(otherUser);

        ReserveSeatRequest rebookRequest = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A8"))
                .idempotencyKey("REBOOK-KEY-" + UUID.randomUUID())
                .build();

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rebookRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void testCancelReservationByNonOwnerIsForbidden() throws Exception {
        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A5"))
                .idempotencyKey("OWNER-TEST-" + UUID.randomUUID())
                .build();

        String resJson = mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ReservationResponse response = objectMapper.readValue(resJson, ReservationResponse.class);
        Long reservationId = response.getReservationId();

        // Another user attempts to cancel
        String attacker = "attacker_" + UUID.randomUUID().toString().substring(0, 6);
        userRepository.save(User.builder().username(attacker).password("pass").build());
        String attackerToken = jwtTokenService.generateToken(attacker);

        mockMvc.perform(post("/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void testHotSeatConcurrencyStormOneWinnerAllOthersConflict() throws Exception {
        int totalRequests = 20000;
        int workerThreads = 100; // Mirrors real web server concurrency
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Pre-create distinct users for the storm
        int distinctUsers = 100;
        List<String> userTokens = new ArrayList<>(distinctUsers);
        for (int i = 0; i < distinctUsers; i++) {
            String uName = "storm_user_" + i + "_" + UUID.randomUUID().toString().substring(0, 6);
            userRepository.save(User.builder().username(uName).password("pass").build());
            userTokens.add(jwtTokenService.generateToken(uName));
        }

        ReserveSeatRequest request = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A6")) // All 20,000 requests compete for hot seat A6
                .build();
        String jsonPayload = objectMapper.writeValueAsString(request);

        for (int i = 0; i < totalRequests; i++) {
            final String token = userTokens.get(i % distinctUsers);
            executor.submit(() -> {
                try {
                    mockMvc.perform(post("/shows/" + showId + "/reserve")
                                    .header("Authorization", "Bearer " + token)
                                    .header("Idempotency-Key", "HOT-STORM-" + UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonPayload))
                            .andDo(resultAction -> {
                                int status = resultAction.getResponse().getStatus();
                                if (status == 201) {
                                    successCount.incrementAndGet();
                                } else if (status == 409) {
                                    conflictCount.incrementAndGet();
                                } else {
                                    errorCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all 20,000 requests to be processed
        latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Core Invariants: Exactly ONE 201, remaining 19,999 Conflict, ZERO 5xx errors!
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(totalRequests - 1);
        assertThat(errorCount.get()).isEqualTo(0);
    }

    @Test
    void testRealisticMixedWorkload20kRequests() throws Exception {
        // 1. Create a dedicated show with 20 seats (A1..A10, B1..B10)
        List<String> allSeatNames = new ArrayList<>();
        for (char row = 'A'; row <= 'B'; row++) {
            for (int num = 1; num <= 10; num++) {
                allSeatNames.add("" + row + num);
            }
        }
        ShowResponse testShow = showService.createShow(CreateShowRequest.builder()
                .name("BigShow-" + UUID.randomUUID().toString().substring(0, 8))
                .seats(allSeatNames)
                .pricePaise(10000L)
                .perUserLimit(4)
                .build());
        Long bigShowId = testShow.getId();

        // 2. Pre-create 100 distinct users with JWT tokens
        int distinctUsers = 100;
        List<String> userTokens = new ArrayList<>(distinctUsers);
        for (int i = 0; i < distinctUsers; i++) {
            String uName = "mixed_user_" + i + "_" + UUID.randomUUID().toString().substring(0, 6);
            userRepository.save(User.builder().username(uName).password("pass").build());
            userTokens.add(jwtTokenService.generateToken(uName));
        }

        // Establish an idempotent base reservation for replay testing
        String idempotentKey = "IDEMP-BASE-" + UUID.randomUUID();
        String idempotentToken = userTokens.get(0);
        ReserveSeatRequest idempotentValidRequest = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A1"))
                .build();
        String idempotentValidJson = objectMapper.writeValueAsString(idempotentValidRequest);

        // Seed the initial reservation
        mockMvc.perform(post("/shows/" + bigShowId + "/reserve")
                        .header("Authorization", "Bearer " + idempotentToken)
                        .header("Idempotency-Key", idempotentKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idempotentValidJson))
                .andExpect(status().isCreated());

        // Payload variants
        ReserveSeatRequest idempotentAlteredRequest = ReserveSeatRequest.builder()
                .seatNumbers(List.of("B5")) // Different seat for same key -> 409 Conflict (Karan's requirement)
                .build();
        String idempotentAlteredJson = objectMapper.writeValueAsString(idempotentAlteredRequest);

        ReserveSeatRequest limitExceedRequest = ReserveSeatRequest.builder()
                .seatNumbers(List.of("B1", "B2", "B3", "B4", "B5")) // 5 seats (> 4) -> 400
                .build();
        String limitExceedJson = objectMapper.writeValueAsString(limitExceedRequest);

        ReserveSeatRequest hotSeatRequest = ReserveSeatRequest.builder()
                .seatNumbers(List.of("A2")) // Hot seat storm -> 1 winner, rest 409
                .build();
        String hotSeatJson = objectMapper.writeValueAsString(hotSeatRequest);

        int totalRequests = 20000;
        int workerThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);       // 200 or 201
        AtomicInteger conflictCount = new AtomicInteger(0);      // 409 Conflict (Hot-seat losers + tampered keys)
        AtomicInteger badRequestCount = new AtomicInteger(0);    // 400 Bad Request
        AtomicInteger serverErrorCount = new AtomicInteger(0);   // 5xx Server Errors

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index < 4000) {
                        // Category 1: 4,000 Idempotent Replays (Same Key, Same Payload -> 200/201 cached)
                        mockMvc.perform(post("/shows/" + bigShowId + "/reserve")
                                        .header("Authorization", "Bearer " + idempotentToken)
                                        .header("Idempotency-Key", idempotentKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(idempotentValidJson))
                                .andDo(res -> countStatus(res.getResponse().getStatus(), successCount, conflictCount, badRequestCount, serverErrorCount));

                    } else if (index < 6500) {
                        // Category 2: 2,500 Tampered Idempotent Requests (Same Key, Different Payload -> 409 Conflict)
                        mockMvc.perform(post("/shows/" + bigShowId + "/reserve")
                                        .header("Authorization", "Bearer " + idempotentToken)
                                        .header("Idempotency-Key", idempotentKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(idempotentAlteredJson))
                                .andDo(res -> countStatus(res.getResponse().getStatus(), successCount, conflictCount, badRequestCount, serverErrorCount));

                    } else if (index < 9500) {
                        // Category 3: 3,000 Limit Exceeding Requests (Trying to book 5 seats -> 400)
                        String token = userTokens.get(index % distinctUsers);
                        mockMvc.perform(post("/shows/" + bigShowId + "/reserve")
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", "LIMIT-KEY-" + UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(limitExceedJson))
                                .andDo(res -> countStatus(res.getResponse().getStatus(), successCount, conflictCount, badRequestCount, serverErrorCount));

                    } else {
                        // Category 4: 10,500 Hot-Seat Storm Requests (Fighting for A2 -> 1 winner 201, rest 409)
                        String token = userTokens.get(index % distinctUsers);
                        mockMvc.perform(post("/shows/" + bigShowId + "/reserve")
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", "HOT2-KEY-" + UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(hotSeatJson))
                                .andDo(res -> countStatus(res.getResponse().getStatus(), successCount, conflictCount, badRequestCount, serverErrorCount));
                    }
                } catch (Exception e) {
                    serverErrorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Core Invariants across all 20,000 mixed requests:
        // 1. Exactly 4,001 successes (4,000 cached idempotent replays + 1 hot seat winner)
        assertThat(successCount.get()).isEqualTo(4001);

        // 2. Exactly 3,000 bad request errors (per-user limit exceeded)
        assertThat(badRequestCount.get()).isEqualTo(3000);

        // 3. Exactly 12,999 conflict errors (10,499 hot seat losers + 2,500 tampered idempotency keys)
        assertThat(conflictCount.get()).isEqualTo(12999);

        // 4. Invariant: ZERO server errors (5xx)
        assertThat(serverErrorCount.get()).isEqualTo(0);

        // 5. Reconciliation Invariant: available + confirmed == total seats (20)
        ShowResponse finalShow = showService.getShow(bigShowId);
        assertThat(finalShow.getAvailableCount() + finalShow.getConfirmedCount())
                .isEqualTo(finalShow.getTotalSeats().longValue());
    }

    private void countStatus(int status, AtomicInteger success, AtomicInteger conflict, AtomicInteger badReq, AtomicInteger err) {
        if (status == 200 || status == 201) success.incrementAndGet();
        else if (status == 409) conflict.incrementAndGet();
        else if (status == 400) badReq.incrementAndGet();
        else err.incrementAndGet();
    }
}
