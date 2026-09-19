package com.taskmanager.api.board;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createBoard() throws Exception {
        String body = mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "インポート先"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void replacesColumnsAndTasksForBoard() throws Exception {
        String boardId = createBoard();
        // seed existing state that should be wiped out by import
        mockMvc.perform(post("/api/boards/{boardId}/columns", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "既存の列", "done", false))));

        Map<String, Object> payload = Map.of(
                "columns", List.of(
                        Map.of("id", "col-1", "name", "未着手", "done", false),
                        Map.of("id", "col-2", "name", "完了", "done", true)
                ),
                "tasks", List.of(
                        Map.of("columnId", "col-1", "title", "タスクA", "priority", "HIGH"),
                        Map.of("columnId", "col-2", "title", "タスクB", "checklist",
                                List.of(Map.of("text", "確認", "done", true)))
                )
        );

        mockMvc.perform(put("/api/boards/{boardId}/import", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/boards/{boardId}/columns", boardId))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("未着手"))
                .andExpect(jsonPath("$[1].name").value("完了"));

        mockMvc.perform(get("/api/boards/{boardId}/tasks", boardId))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsTaskReferencingUnknownColumn() throws Exception {
        String boardId = createBoard();
        Map<String, Object> payload = Map.of(
                "columns", List.of(Map.of("id", "col-1", "name", "列", "done", false)),
                "tasks", List.of(Map.of("columnId", "col-missing", "title", "タスク"))
        );

        mockMvc.perform(put("/api/boards/{boardId}/import", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importingForUnknownBoardFails() throws Exception {
        Map<String, Object> payload = Map.of(
                "columns", List.of(Map.of("id", "col-1", "name", "列", "done", false))
        );

        mockMvc.perform(put("/api/boards/{boardId}/import", "no-such-board")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }
}
