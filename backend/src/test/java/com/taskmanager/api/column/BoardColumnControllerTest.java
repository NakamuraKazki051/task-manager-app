package com.taskmanager.api.column;

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
class BoardColumnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createBoard() throws Exception {
        String body = mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "テストボード"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createColumn(String boardId, String name, boolean done) throws Exception {
        String body = mockMvc.perform(post("/api/boards/{boardId}/columns", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "done", done))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void creatingColumnForUnknownBoardFails() throws Exception {
        mockMvc.perform(post("/api/boards/{boardId}/columns", "no-such-board")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "列", "done", false))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsListsAndUpdatesColumn() throws Exception {
        String boardId = createBoard();
        String columnId = createColumn(boardId, "未着手", false);

        mockMvc.perform(get("/api/boards/{boardId}/columns", boardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("未着手"));

        mockMvc.perform(put("/api/columns/{id}", columnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "対応中", "done", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("対応中"))
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void cannotDeleteLastColumn() throws Exception {
        String boardId = createBoard();
        String columnId = createColumn(boardId, "唯一の列", false);

        mockMvc.perform(delete("/api/columns/{id}", columnId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotDeleteColumnWithTasks() throws Exception {
        String boardId = createBoard();
        String columnId = createColumn(boardId, "列1", false);
        createColumn(boardId, "列2", false);

        mockMvc.perform(post("/api/boards/{boardId}/tasks", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "タスク", "columnId", columnId))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/columns/{id}", columnId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movesColumnOrder() throws Exception {
        String boardId = createBoard();
        String first = createColumn(boardId, "1番目", false);
        String second = createColumn(boardId, "2番目", false);
        createColumn(boardId, "3番目", false);

        mockMvc.perform(patch("/api/columns/{id}/move", first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayOrder", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/boards/{boardId}/columns", boardId))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[2].id").value(first));
    }
}
