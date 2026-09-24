package com.taskmanager.api.board;

import com.taskmanager.api.TestAuth;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 複数タブ・二重ログインで初期ボード作成が同時に走っても「マイボード」が1つしか作られないことの確認。
 * 各リクエストが実際にコミットされる必要があるため、他のテストと違い @Transactional を付けない。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardEnsureDefaultConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BoardRepository boardRepository;

    @Test
    void concurrentEnsureDefaultCreatesOnlyOneBoard() throws Exception {
        MockHttpSession session = TestAuth.registerAndLogin(mockMvc, objectMapper);
        String userId = (String) session.getAttribute("userId");

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/boards/ensure-default").session(session))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(200, result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, boardRepository.findByUserId(userId).size());
        String body = mockMvc.perform(get("/api/boards").session(session))
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, objectMapper.readTree(body).size());
    }
}
