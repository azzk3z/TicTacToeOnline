package com.tictactoe.backend.config;

import com.tictactoe.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtSessions {
    static final String COOKIE_NAME = "tictactoe_session";

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final Duration lifetime;
    private final boolean secure;

    public JwtSessions(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs,
                       @Value("${app.auth.cookie-secure}") boolean secure) {
        this.signingKey = secret.isBlank()
                ? Jwts.SIG.HS256.key().build()
                : Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.parser = Jwts.parser().verifyWith(signingKey).build();
        this.lifetime = Duration.ofMillis(expirationMs);
        this.secure = secure;
    }

    public ResponseCookie start(User user) {
        Instant now = Instant.now();
        String jwt = Jwts.builder()
                .subject(user.getId().toString())
                .claim("version", user.getCredentialsVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(signingKey)
                .compact();
        return cookie(jwt, lifetime);
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    Claims parse(String jwt) {
        return parser.parseSignedClaims(jwt).getPayload();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }
}
