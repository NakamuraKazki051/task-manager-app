package com.taskmanager.api.board;

import com.taskmanager.api.TestAuth;
import com.taskmanager.api.column.BoardColumnRepository;
import com.taskmanager.api.task.TaskRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private BoardColumnRepository columnRepository;

    private MockHttpSession session;

    @BeforeEach
    void logIn() throws Exception {
        session = TestAuth.registerAndLogin(mockMvc, objectMapper);
    }

    private String createBoard(String name) throws Exception {
        String body = mockMvc.perform(post("/api/boards").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsAndListsBoards() throws Exception {
        createBoard("ボードA");

        mockMvc.perform(get("/api/boards").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='ボードA')]").exists());
    }

    @Test
    void usersOnlySeeOwnBoards() throws Exception {
        createBoard("自分のボード");
        MockHttpSession otherSession = TestAuth.registerAndLogin(mockMvc, objectMapper);

        mockMvc.perform(get("/api/boards").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='自分のボード')]").doesNotExist());
    }

    @Test
    void cannotRenameOrDeleteAnotherUsersBoard() throws Exception {
        String boardId = createBoard("他人のボード");
        MockHttpSession otherSession = TestAuth.registerAndLogin(mockMvc, objectMapper);

        mockMvc.perform(put("/api/boards/{id}", boardId).session(otherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "乗っ取り"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/boards/{id}", boardId).session(otherSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/boards").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renamesBoard() throws Exception {
        String id = createBoard("旧名");

        mockMvc.perform(put("/api/boards/{id}", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "新名"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名"));
    }

    @Test
    void deletesBoardUnlessLastOne() throws Exception {
        String first = createBoard("最初のボード");
        String second = createBoard("2つ目のボード");

        mockMvc.perform(delete("/api/boards/{id}", second).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/boards/{id}", first).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingBoardAlsoDeletesItsColumnsAndTasks() throws Exception {
        createBoard("残すボード");
        String boardId = createBoard("消すボード");
        String columnBody = mockMvc.perform(post("/api/boards/{boardId}/columns", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "列", "done", false))))
                .andReturn().getResponse().getContentAsString();
        String columnId = objectMapper.readTree(columnBody).get("id").asText();
        mockMvc.perform(post("/api/boards/{boardId}/tasks", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "タスク", "columnId", columnId))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/boards/{id}", boardId).session(session))
                .andExpect(status().isNoContent());

        assertEquals(0, taskRepository.findByBoardId(boardId).size());
        assertEquals(0, columnRepository.countByBoardId(boardId));
    }

    @Test
    void deletingUnknownBoardReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/boards/{id}", "does-not-exist").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedJsonBodyReturnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/boards").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest());
    }
}
