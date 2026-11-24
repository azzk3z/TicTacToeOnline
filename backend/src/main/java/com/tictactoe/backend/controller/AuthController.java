package com.tictactoe.backend.controller;

import com.tictactoe.backend.config.JwtSessions;
import com.tictactoe.backend.dto.auth.ForgotPasswordRequest;
import com.tictactoe.backend.dto.auth.LoginRequest;
import com.tictactoe.backend.dto.auth.RegisterRequest;
import com.tictactoe.backend.dto.auth.ResetPasswordRequest;
import com.tictactoe.backend.dto.user.UserResponse;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.service.AuthService;
import com.tictactoe.backend.service.GameRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtSessions sessions;
    private final GameRoomService rooms;

    public AuthController(AuthService authService, JwtSessions sessions, GameRoomService rooms) {
        this.authService = authService;
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessions.start(user).toString())
                .body(UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        if (user != null) {
            rooms.leave(user.getId());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessions.clear().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessions.clear().toString())
                .build();
    }
}
