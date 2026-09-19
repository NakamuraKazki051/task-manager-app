package com.taskmanager.api.auth;

import com.taskmanager.api.auth.AuthDtos.LoginRequest;
import com.taskmanager.api.common.ApiException;
import com.taskmanager.api.user.User;
import com.taskmanager.api.user.UserDtos.UserResponse;
import com.taskmanager.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SESSION_USER_ID = "userId";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("メールアドレスまたはパスワードが正しくありません"));
        httpRequest.getSession(true).setAttribute(SESSION_USER_ID, user.getId());
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/me")
    public UserResponse me(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String userId = session != null ? (String) session.getAttribute(SESSION_USER_ID) : null;
        if (userId == null) {
            throw ApiException.unauthorized("ログインしていません");
        }
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
    }
}
