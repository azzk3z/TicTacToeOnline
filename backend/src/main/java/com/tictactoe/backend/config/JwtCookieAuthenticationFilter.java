package com.tictactoe.backend.config;

import com.tictactoe.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;

final class JwtCookieAuthenticationFilter extends OncePerRequestFilter {
    private final JwtSessions sessions;
    private final UserRepository users;

    JwtCookieAuthenticationFilter(JwtSessions sessions, UserRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cookie = WebUtils.getCookie(request, JwtSessions.COOKIE_NAME);
        if (cookie != null) {
            authenticate(cookie.getValue());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        long userId;
        Long version;
        try {
            Claims claims = sessions.parse(token);
            userId = Long.parseLong(claims.getSubject());
            version = claims.get("version", Long.class);
        } catch (JwtException | IllegalArgumentException ex) {
            return;
        }

        if (version == null) {
            return;
        }
        users.findById(userId)
                .filter(user -> user.getCredentialsVersion() == version)
                .ifPresent(user -> {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
                    SecurityContextHolder.setContext(context);
                });
    }
}
