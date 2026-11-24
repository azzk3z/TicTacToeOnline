package com.tictactoe.backend.service;

import com.tictactoe.backend.dto.auth.ForgotPasswordRequest;
import com.tictactoe.backend.dto.auth.LoginRequest;
import com.tictactoe.backend.dto.auth.RegisterRequest;
import com.tictactoe.backend.dto.auth.ResetPasswordRequest;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.exception.AuthException;
import com.tictactoe.backend.exception.DuplicateResourceException;
import com.tictactoe.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {
    private static final String USERNAME = "player_one";
    private static final String EMAIL = "player@example.test";
    private static final String PASSWORD = "test-password";
    private static final String PASSWORD_HASH = "test-password-hash";
    private static final String DUMMY_HASH = "dummy-password-hash";
    private static final String TOKEN = "t".repeat(43);

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordResetMail mail;
    @Mock
    private ApplicationEventPublisher events;

    private AuthService auth;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        auth = new AuthService(users, passwordEncoder, mail, events);
    }

    @Nested
    class Registration {
        @Test
        void normalizesEmailAndSavesOnlyTheEncodedPassword() {
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
            when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            var result = auth.register(new RegisterRequest(USERNAME, " PLAYER@example.test ", PASSWORD, " Player One "));

            var saved = ArgumentCaptor.forClass(User.class);
            verify(users).save(saved.capture());
            verify(users).existsByEmailIgnoreCase(EMAIL);
            assertThat(saved.getValue().getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(result.username()).isEqualTo(USERNAME);
            assertThat(result.email()).isEqualTo(EMAIL);
            assertThat(result.displayName()).isEqualTo("Player One");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void usesUsernameWhenDisplayNameIsMissing(String displayName) {
            when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            assertThat(auth.register(new RegisterRequest(USERNAME, EMAIL, PASSWORD, displayName)).displayName())
                    .isEqualTo(USERNAME);
        }

        @Test
        void rejectsAnExistingUsername() {
            when(users.existsByUsernameIgnoreCase(USERNAME)).thenReturn(true);

            var error = assertThrows(DuplicateResourceException.class,
                    () -> auth.register(new RegisterRequest(USERNAME, EMAIL, PASSWORD, null)));

            assertThat(error.getField()).isEqualTo("username");
            verify(users, never()).save(any());
        }

        @Test
        void rejectsAnExistingEmailAfterNormalization() {
            when(users.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

            var error = assertThrows(DuplicateResourceException.class,
                    () -> auth.register(new RegisterRequest(USERNAME, "PLAYER@example.test", PASSWORD, null)));

            assertThat(error.getField()).isEqualTo("email");
            verify(users, never()).save(any());
        }

        @Test
        void rejectsAPasswordLongerThan72Utf8Bytes() {
            String oversized = "á".repeat(37);

            var error = assertThrows(AuthException.class,
                    () -> auth.register(new RegisterRequest(USERNAME, EMAIL, oversized, null)));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.getField()).isEqualTo("password");
            verify(passwordEncoder, never()).encode(oversized);
            verify(users, never()).save(any());
        }
    }

    @Nested
    class Login {
        @Test
        void acceptsTheExactUsernameWithSurroundingSpaces() {
            User account = account();
            when(users.findByUsername(USERNAME)).thenReturn(Optional.of(account));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

            assertThat(auth.login(new LoginRequest(" " + USERNAME + " ", PASSWORD))).isSameAs(account);
        }

        @Test
        void acceptsAUniqueCaseInsensitiveUsernameMatch() {
            User account = account();
            when(users.findTop2ByUsernameIgnoreCaseOrderByIdAsc("PLAYER_ONE")).thenReturn(List.of(account));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

            assertThat(auth.login(new LoginRequest("PLAYER_ONE", PASSWORD))).isSameAs(account);
            verify(users).findByUsername("PLAYER_ONE");
        }

        @Test
        void acceptsAnEmailAndUsesTheCaseInsensitiveLookup() {
            User account = account();
            when(users.findFirstByEmailIgnoreCaseOrderByIdAsc("PLAYER@example.test")).thenReturn(Optional.of(account));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

            assertThat(auth.login(new LoginRequest(" PLAYER@example.test ", PASSWORD))).isSameAs(account);
        }

        @Test
        void rejectsAnIncorrectPassword() {
            when(users.findByUsername(USERNAME)).thenReturn(Optional.of(account()));

            var error = assertThrows(AuthException.class, () -> auth.login(new LoginRequest(USERNAME, "incorrect")));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(passwordEncoder).matches("incorrect", PASSWORD_HASH);
        }

        @Test
        void rejectsAnUnknownAccountEvenIfTheDummyHashMatches() {
            when(passwordEncoder.matches(PASSWORD, DUMMY_HASH)).thenReturn(true);

            var error = assertThrows(AuthException.class, () -> auth.login(new LoginRequest(EMAIL, PASSWORD)));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
        }

        @Test
        void rejectsAnAmbiguousCaseInsensitiveUsername() {
            User duplicate = new User("Player_One", "other@example.test", PASSWORD_HASH, "Other");
            when(users.findTop2ByUsernameIgnoreCaseOrderByIdAsc("PLAYER_ONE"))
                    .thenReturn(List.of(account(), duplicate));

            var error = assertThrows(AuthException.class, () -> auth.login(new LoginRequest("PLAYER_ONE", PASSWORD)));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
            verify(passwordEncoder, never()).matches(PASSWORD, PASSWORD_HASH);
        }

        @Test
        void rejectsAnOversizedUtf8PasswordBeforeComparingHashes() {
            when(users.findByUsername(USERNAME)).thenReturn(Optional.of(account()));

            var error = assertThrows(AuthException.class,
                    () -> auth.login(new LoginRequest(USERNAME, "á".repeat(37))));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    @Nested
    class ForgotPassword {
        @Test
        void storesTheHashedTokenAndQueuesMailWithTheRawToken() {
            User account = account();
            when(mail.isConfigured()).thenReturn(true);
            when(users.findLockedByEmail(EMAIL)).thenReturn(Optional.of(account));

            Instant before = Instant.now();
            auth.forgotPassword(new ForgotPasswordRequest(" PLAYER@example.test "));
            Instant after = Instant.now();

            var event = ArgumentCaptor.forClass(PasswordResetMail.Request.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().email()).isEqualTo(EMAIL);
            String rawToken = event.getValue().token();
            assertThat(rawToken).matches("[A-Za-z0-9_-]{43}");
            String hash = tokenHash(rawToken);
            assertThat(account.canResetPassword(hash, before.plus(AuthService.RESET_TOKEN_LIFETIME).minusSeconds(1))).isTrue();
            assertThat(account.canResetPassword(hash, after.plus(AuthService.RESET_TOKEN_LIFETIME))).isFalse();
            assertThat(account.canResetPassword(rawToken, after)).isFalse();
        }

        @Test
        void acceptsAnUnknownEmailWithoutQueuingMail() {
            when(mail.isConfigured()).thenReturn(true);

            auth.forgotPassword(new ForgotPasswordRequest(EMAIL));

            verify(users).findLockedByEmail(EMAIL);
            verifyNoInteractions(events);
        }

        @Test
        void keepsTheExistingTokenDuringTheCooldown() {
            User account = account();
            String hash = tokenHash(TOKEN);
            account.setPasswordResetToken(hash, Instant.now().plus(AuthService.RESET_TOKEN_LIFETIME));
            when(mail.isConfigured()).thenReturn(true);
            when(users.findLockedByEmail(EMAIL)).thenReturn(Optional.of(account));

            auth.forgotPassword(new ForgotPasswordRequest(EMAIL));

            verifyNoInteractions(events);
            assertThat(account.canResetPassword(hash, Instant.now())).isTrue();
        }

        @Test
        void reportsUnavailableEmailWithoutLookingUpTheAccount() {
            var error = assertThrows(AuthException.class, () -> auth.forgotPassword(new ForgotPasswordRequest(EMAIL)));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verifyNoInteractions(users, events);
        }
    }

    @Nested
    class ResetPassword {
        @Test
        void changesTheHashAndInvalidatesExistingCredentials() {
            User account = accountWithResetToken(Instant.now().plusSeconds(600));
            when(passwordEncoder.encode("new-password")).thenReturn("new-password-hash");

            auth.resetPassword(new ResetPasswordRequest(TOKEN, "new-password"));

            assertThat(account.getPasswordHash()).isEqualTo("new-password-hash");
            assertThat(account.getCredentialsVersion()).isEqualTo(1);
            assertThat(account.hasResetTokenExpiringAfter(Instant.EPOCH)).isFalse();
        }

        @Test
        void rejectsReuseAfterThePasswordChanges() {
            User account = accountWithResetToken(Instant.now().plusSeconds(600));
            when(passwordEncoder.encode("new-password")).thenReturn("new-password-hash");
            auth.resetPassword(new ResetPasswordRequest(TOKEN, "new-password"));

            var error = assertThrows(AuthException.class,
                    () -> auth.resetPassword(new ResetPasswordRequest(TOKEN, "different-password")));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.getField()).isEqualTo("token");
            assertThat(account.getPasswordHash()).isEqualTo("new-password-hash");
        }

        @Test
        void rejectsAnExpiredTokenWithoutChangingTheAccount() {
            User account = accountWithResetToken(Instant.now().minusSeconds(600));

            var error = assertThrows(AuthException.class,
                    () -> auth.resetPassword(new ResetPasswordRequest(TOKEN, PASSWORD)));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.getField()).isEqualTo("token");
            assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(account.getCredentialsVersion()).isZero();
        }

        @Test
        void rejectsAnUnknownToken() {
            var error = assertThrows(AuthException.class,
                    () -> auth.resetPassword(new ResetPasswordRequest(TOKEN, PASSWORD)));

            assertThat(error.getField()).isEqualTo("token");
            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            verify(users).findByPasswordResetTokenHash(tokenHash(TOKEN));
        }

        @Test
        void rejectsAnOversizedUtf8PasswordAndPreservesAValidToken() {
            User account = accountWithResetToken(Instant.now().plusSeconds(600));
            String oversized = "á".repeat(37);

            var error = assertThrows(AuthException.class,
                    () -> auth.resetPassword(new ResetPasswordRequest(TOKEN, oversized)));

            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.getField()).isEqualTo("password");
            assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(account.canResetPassword(tokenHash(TOKEN), Instant.now())).isTrue();
            verify(passwordEncoder, never()).encode(oversized);
        }

        private User accountWithResetToken(Instant expiresAt) {
            User account = account();
            String hash = tokenHash(TOKEN);
            account.setPasswordResetToken(hash, expiresAt);
            when(users.findByPasswordResetTokenHash(hash)).thenReturn(Optional.of(account));
            return account;
        }
    }

    private static User account() {
        return new User(USERNAME, EMAIL, PASSWORD_HASH, "Player One");
    }

    private static String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
