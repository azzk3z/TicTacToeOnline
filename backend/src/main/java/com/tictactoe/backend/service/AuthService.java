package com.tictactoe.backend.service;

import com.tictactoe.backend.dto.auth.ForgotPasswordRequest;
import com.tictactoe.backend.dto.auth.LoginRequest;
import com.tictactoe.backend.dto.auth.RegisterRequest;
import com.tictactoe.backend.dto.auth.ResetPasswordRequest;
import com.tictactoe.backend.dto.user.UserResponse;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.exception.AuthException;
import com.tictactoe.backend.exception.DuplicateResourceException;
import com.tictactoe.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final Duration RESET_REQUEST_COOLDOWN = Duration.ofMinutes(1);
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMail passwordResetMail;
    private final ApplicationEventPublisher events;
    private final String dummyPasswordHash;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PasswordResetMail passwordResetMail, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetMail = passwordResetMail;
        this.events = events;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException(
                    "username",
                    "Username đã được sử dụng"
            );
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(
                    "email",
                    "Email đã được sử dụng"
            );
        }
        String displayName = (request.displayName() == null || request.displayName().isBlank())
                ? username
                : request.displayName().trim();

        User user = new User(
                username,
                email,
                encodePassword(request.password()),
                displayName
        );

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest request) {
        User user = findLoginUser(request.identifier().trim()).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        if (!fitsBcrypt(request.password()) || !passwordEncoder.matches(request.password(), hash) || user == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Tên đăng nhập, email hoặc mật khẩu chưa đúng");
        }
        return user;
    }

    private Optional<User> findLoginUser(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(identifier);
        }
        return userRepository.findByUsername(identifier).or(() -> {
            var matches = userRepository.findTop2ByUsernameIgnoreCaseOrderByIdAsc(identifier);
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
        });
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        if (!passwordResetMail.isConfigured()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "Chức năng gửi email đặt lại mật khẩu chưa được cấu hình");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findLockedByEmail(email).orElse(null);
        Instant now = Instant.now();
        Instant cooldownCutoff = now.plus(RESET_TOKEN_LIFETIME).minus(RESET_REQUEST_COOLDOWN);
        if (user == null || user.hasResetTokenExpiringAfter(cooldownCutoff)) {
            return;
        }

        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        user.setPasswordResetToken(digest(token), now.plus(RESET_TOKEN_LIFETIME));
        events.publishEvent(new PasswordResetMail.Request(user.getEmail(), token));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = digest(request.token());
        User user = userRepository.findByPasswordResetTokenHash(tokenHash).orElse(null);
        if (user == null || !user.canResetPassword(tokenHash, Instant.now())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu đã hết hạn hoặc đã được dùng", "token");
        }
        user.setPasswordHash(encodePassword(request.password()));
    }

    private String encodePassword(String password) {
        if (!fitsBcrypt(password)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Mật khẩu vượt quá 72 byte UTF-8", "password");
        }
        return passwordEncoder.encode(password);
    }

    private static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    private static String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
