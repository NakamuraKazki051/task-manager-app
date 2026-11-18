package com.taskmanager.api.auth;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 同じメールアドレスへのログインは5分間に10回まで(パスワードの総当たり対策) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimiter limiter;

    @AfterEach
    void resetLimiter() {
        limiter.reset();
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    @Test
    void tooManyAttemptsAreRejectedEvenWithTheRightPassword() throws Exception {
        register("brute@example.com", "correcthorse");
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            login("brute@example.com", "wrong-" + i).andExpect(status().isUnauthorized());
        }
        login("brute@example.com", "correcthorse")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("ログインの試行回数が多すぎます。5分ほど待ってからもう一度お試しください"));
    }

    @Test
    void theLimitIsPerEmailAndCaseInsensitive() throws Exception {
        register("other@example.com", "correcthorse");
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            login(i % 2 == 0 ? "Victim@Example.com" : "victim@example.com", "wrong").andExpect(status().isUnauthorized());
        }
        login("victim@example.com", "wrong").andExpect(status().isTooManyRequests());
        // ほかのメールアドレスには影響しない
        login("other@example.com", "correcthorse").andExpect(status().isOk());
    }

    @Test
    void loginWorksAgainAfterTheWindow() throws Exception {
        register("wait@example.com", "correcthorse");
        Instant start = Instant.parse("2026-11-18T00:00:00Z");
        limiter.setClock(Clock.fixed(start, ZoneOffset.UTC));
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            login("wait@example.com", "wrong").andExpect(status().isUnauthorized());
        }
        login("wait@example.com", "correcthorse").andExpect(status().isTooManyRequests());

        limiter.setClock(Clock.fixed(start.plus(LoginRateLimiter.WINDOW).plusSeconds(1), ZoneOffset.UTC));
        login("wait@example.com", "correcthorse").andExpect(status().isOk());
    }
}
