package com.taskmanager.api.auth;

import com.taskmanager.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public static final String SESSION_KEY = "userId";

    public String require(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String userId = session != null ? (String) session.getAttribute(SESSION_KEY) : null;
        if (userId == null) {
            throw ApiException.unauthorized("ログインが必要です");
        }
        return userId;
    }
}
