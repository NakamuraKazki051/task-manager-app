package com.taskmanager.api.user;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    @Test
    void registersUserWithoutExposingPassword() throws Exception {
        register("user@example.com", "correcthorse")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void normalizesEmailCase() throws Exception {
        register("Mixed.Case@Example.com", "correcthorse")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("mixed.case@example.com"));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        register("dup@example.com", "correcthorse")
                .andExpect(status().isCreated());

        register("dup@example.com", "anotherpassword")
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        register("not-an-email", "correcthorse")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        register("shortpw@example.com", "short")
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonBodyReturnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest());
    }
}
