package com.taskmanager.api.board;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createBoard(String name) throws Exception {
        String body = mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void createsAndListsBoards() throws Exception {
        createBoard("ボードA");

        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='ボードA')]").exists());
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renamesBoard() throws Exception {
        String id = createBoard("旧名");

        mockMvc.perform(put("/api/boards/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "新名"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名"));
    }

    @Test
    void deletesBoardUnlessLastOne() throws Exception {
        String first = createBoard("最初のボード");
        String second = createBoard("2つ目のボード");

        mockMvc.perform(delete("/api/boards/{id}", second))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/boards/{id}", first))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingUnknownBoardReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/boards/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedJsonBodyReturnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest());
    }
}
