package com.taskmanager.api.common;

import com.taskmanager.api.TestAuth;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AWS では画面を https://xxxx.cloudfront.net で開き、CloudFront → nginx → Spring Boot は http で届く。
 * バックエンドからは「http のページから来た https のオリジン」に見えて別のオリジン扱いになるので、
 * APP_CORS_ALLOWED_ORIGINS(user_data.sh.tftpl)で CloudFront のオリジンを許可しておく必要がある
 */
@SpringBootTest(properties = "app.cors.allowed-origins=https://d111111abcdef8.cloudfront.net")
@AutoConfigureMockMvc
@Transactional
class CloudFrontOriginTest {

    private static final String HOST = "d111111abcdef8.cloudfront.net";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void requestsFromTheCloudFrontPageAreAccepted() throws Exception {
        MockHttpSession session = TestAuth.registerAndLogin(mockMvc, objectMapper);
        mockMvc.perform(post("http://" + HOST + "/api/boards").session(session)
                        .header("Origin", "https://" + HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "CloudFront から"))))
                .andExpect(status().isCreated());
    }

    @Test
    void anotherHttpsOriginIsStillRejected() throws Exception {
        MockHttpSession session = TestAuth.registerAndLogin(mockMvc, objectMapper);
        mockMvc.perform(post("http://" + HOST + "/api/boards").session(session)
                        .header("Origin", "https://evil.cloudfront.net")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "よそから"))))
                .andExpect(status().isForbidden());
    }
}
