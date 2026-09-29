package com.taskmanager.api.common;

import com.taskmanager.api.TestAuth;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 長すぎる値・欠けた値・存在しないURLなどで、500(サーバーエラー)ではなく原因が分かる4xxを返すことを確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession session;

    @BeforeEach
    void logIn() throws Exception {
        session = TestAuth.registerAndLogin(mockMvc, objectMapper);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String createBoard() throws Exception {
        String body = mockMvc.perform(post("/api/boards").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "ボード"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createColumn(String boardId) throws Exception {
        String body = mockMvc.perform(post("/api/boards/{boardId}/columns", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "未着手", "done", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void tooLongBoardNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/boards").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "あ".repeat(300)))))
                .andExpect(status().isBadRequest());
        String boardId = createBoard();
        mockMvc.perform(put("/api/boards/{id}", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "あ".repeat(300)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongColumnNameIsRejectedOnCreateAndRename() throws Exception {
        String boardId = createBoard();
        mockMvc.perform(post("/api/boards/{boardId}/columns", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "あ".repeat(300), "done", false))))
                .andExpect(status().isBadRequest());
        String columnId = createColumn(boardId);
        mockMvc.perform(put("/api/columns/{id}", columnId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "あ".repeat(300)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongCategoryOrChecklistTextIsRejected() throws Exception {
        String boardId = createBoard();
        String columnId = createColumn(boardId);
        mockMvc.perform(post("/api/boards/{boardId}/tasks", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "t", "columnId", columnId,
                                "categories", List.of("あ".repeat(300))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/boards/{boardId}/tasks", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "t", "columnId", columnId,
                                "checklist", List.of(Map.of("text", "あ".repeat(300), "done", false))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importValidatesEachColumnAndTask() throws Exception {
        String boardId = createBoard();
        Map<String, Object> taskWithoutTitle = new HashMap<>();
        taskWithoutTitle.put("columnId", "c1");
        mockMvc.perform(put("/api/boards/{boardId}/import", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "columns", List.of(Map.of("id", "c1", "name", "列", "done", false)),
                                "tasks", List.of(taskWithoutTitle)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/boards/{boardId}/import", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "columns", List.of(Map.of("id", "c1", "name", "列", "done", false)),
                                "tasks", List.of(Map.of("columnId", "c1", "title", "あ".repeat(300)))))))
                .andExpect(status().isBadRequest());
        Map<String, Object> columnWithoutName = new HashMap<>();
        columnWithoutName.put("id", "c1");
        mockMvc.perform(put("/api/boards/{boardId}/import", boardId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("columns", List.of(columnWithoutName)))))
                .andExpect(status().isBadRequest());
        // 途中で失敗しても元の列は消えていない
        mockMvc.perform(get("/api/boards/{boardId}/columns", boardId).session(session))
                .andExpect(status().isOk());
    }

    @Test
    void passwordLongerThan72BytesIsRejectedNotServerError() throws Exception {
        // 全角文字は1文字3バイトなので、30文字でも72バイトを超える
        String longPassword = "あ".repeat(30);
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "long-pw@example.com", "password", longPassword))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "long-pw@example.com", "password", longPassword))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/me").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", longPassword))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongEmailIsRejected() throws Exception {
        // @Email は @の前64文字・ドメイン255文字までを許すので、それぞれ範囲内のまま合計でDBの255文字を超えさせる
        String email = "a".repeat(64) + "@" + String.join(".", java.util.Collections.nCopies(4, "b".repeat(60))) + ".com";
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "correcthorsebattery"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidQueryParameterIsBadRequest() throws Exception {
        String boardId = createBoard();
        mockMvc.perform(get("/api/boards/{boardId}/tasks", boardId).session(session).param("priority", "URGENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPathIsNotFoundAndWrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/some-id/move").session(session))
                .andExpect(status().isMethodNotAllowed());
    }
}
