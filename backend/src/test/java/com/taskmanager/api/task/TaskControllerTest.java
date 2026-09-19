package com.taskmanager.api.task;

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
class TaskControllerTest {

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

    private String createColumn(String boardId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/boards/{boardId}/columns", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "done", false))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createTask(String boardId, String columnId, Map<String, Object> extra) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>(extra);
        payload.put("columnId", columnId);
        String body = mockMvc.perform(post("/api/boards/{boardId}/tasks", boardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void creatingTaskWithColumnFromAnotherBoardFails() throws Exception {
        String boardA = createBoard();
        String boardB = createBoard();
        String columnOfB = createColumn(boardB, "列");

        mockMvc.perform(post("/api/boards/{boardId}/tasks", boardA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "タスク", "columnId", columnOfB))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersByCategoryPriorityAndQuery() throws Exception {
        String boardId = createBoard();
        String columnId = createColumn(boardId, "列");
        createTask(boardId, columnId, Map.of("title", "資料作成", "priority", "HIGH", "categories", List.of("仕事")));
        createTask(boardId, columnId, Map.of("title", "買い物", "priority", "LOW", "categories", List.of("プライベート")));

        mockMvc.perform(get("/api/boards/{boardId}/tasks", boardId).param("category", "仕事"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("資料作成"));

        mockMvc.perform(get("/api/boards/{boardId}/tasks", boardId).param("priority", "LOW"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("買い物"));

        mockMvc.perform(get("/api/boards/{boardId}/tasks", boardId).param("q", "資料"))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/boards/{boardId}/tasks", boardId).param("sort", "priority"))
                .andExpect(jsonPath("$[0].title").value("資料作成"));
    }

    @Test
    void movingTaskRenumbersSourceAndTargetColumns() throws Exception {
        String boardId = createBoard();
        String colA = createColumn(boardId, "A");
        String colB = createColumn(boardId, "B");
        String a1 = createTask(boardId, colA, Map.of("title", "A1"));
        String a2 = createTask(boardId, colA, Map.of("title", "A2"));
        createTask(boardId, colB, Map.of("title", "B1"));

        // move a1 to column B at index 0
        mockMvc.perform(patch("/api/tasks/{id}/move", a1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("columnId", colB, "displayOrder", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columnId").value(colB))
                .andExpect(jsonPath("$.displayOrder").value(0));

        mockMvc.perform(get("/api/boards/{boardId}/tasks", boardId).param("sort", "manual"))
                .andExpect(status().isOk());

        // remaining task in column A should have been renumbered to 0
        mockMvc.perform(get("/api/tasks/{id}", a2))
                .andExpect(jsonPath("$.displayOrder").value(0));
    }

    @Test
    void movingTaskToColumnFromAnotherBoardFails() throws Exception {
        String boardA = createBoard();
        String boardB = createBoard();
        String colA = createColumn(boardA, "A");
        String colB = createColumn(boardB, "B");
        String task = createTask(boardA, colA, Map.of("title", "タスク"));

        mockMvc.perform(patch("/api/tasks/{id}/move", task)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("columnId", colB, "displayOrder", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesAndDeletesTask() throws Exception {
        String boardId = createBoard();
        String columnId = createColumn(boardId, "列");
        String taskId = createTask(boardId, columnId, Map.of("title", "元タイトル"));

        mockMvc.perform(put("/api/tasks/{id}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "更新後タイトル",
                                "columnId", columnId,
                                "checklist", List.of(Map.of("text", "項目1", "done", false))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("更新後タイトル"))
                .andExpect(jsonPath("$.checklist[0].text").value("項目1"));

        mockMvc.perform(delete("/api/tasks/{id}", taskId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isNotFound());
    }
}
