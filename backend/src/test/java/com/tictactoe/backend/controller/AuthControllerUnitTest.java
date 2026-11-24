package com.tictactoe.backend.controller;

import com.tictactoe.backend.config.JwtSessions;
import com.tictactoe.backend.dto.auth.ForgotPasswordRequest;
import com.tictactoe.backend.dto.auth.LoginRequest;
import com.tictactoe.backend.dto.auth.RegisterRequest;
import com.tictactoe.backend.dto.auth.ResetPasswordRequest;
import com.tictactoe.backend.dto.user.UserResponse;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.exception.AuthException;
import com.tictactoe.backend.exception.DuplicateResourceException;
import com.tictactoe.backend.exception.GlobalExceptionHandler;
import com.tictactoe.backend.service.AuthService;
import com.tictactoe.backend.service.GameRoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {
    private static final String TOKEN = "t".repeat(43);
    private static final String LOGIN_JSON = """
            {"identifier":"player","password":"password123"}
            """;
    private static final String REGISTER_JSON = """
            {"username":"player","email":"player@example.test","password":"password123","displayName":"Player"}
            """;
    private static final String RESET_JSON = """
            {"token":"%s","password":"new-password"}
            """.formatted(TOKEN);
    private static final JwtSessions SESSIONS = new JwtSessions("", 600_000, false);

    @Mock
    private AuthService auth;
    @Mock
    private GameRoomService rooms;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, SESSIONS, rooms))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Registration {
        @Test
        void returnsCreatedWithThePublicAccountDetails() throws Exception {
            var details = new UserResponse(7L, "player", "player@example.test", "Player", Instant.EPOCH);
            when(auth.register(new RegisterRequest("player", "player@example.test", "password123", "Player")))
                    .thenReturn(details);

            mvc.perform(authPost("register", REGISTER_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.username").value("player"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        void rejectsAnInvalidUsernameEmailAndPasswordBeforeCallingTheService() throws Exception {
            mvc.perform(authPost("register", """
                            {"username":"bad name","email":"invalid","password":"short"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.username").isString())
                    .andExpect(jsonPath("$.fieldErrors.email").isString())
                    .andExpect(jsonPath("$.fieldErrors.password").isString());
            verifyNoInteractions(auth);
        }

        @Test
        void returnsAConflictWithTheDuplicateField() throws Exception {
            when(auth.register(any(RegisterRequest.class)))
                    .thenThrow(new DuplicateResourceException("email", "Email đã được sử dụng"));

            mvc.perform(authPost("register", REGISTER_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.fieldErrors.email").value("Email đã được sử dụng"));
        }
    }

    @Nested
    class Login {
        @Test
        void issuesAnHttpOnlySessionCookieOnSuccessfulLogin() throws Exception {
            when(auth.login(new LoginRequest("player", "password123"))).thenReturn(account());

            mvc.perform(authPost("login", LOGIN_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("player"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("tictactoe_session=ey")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
        }

        @Test
        void returnsUnauthorizedWithoutSettingACookieForWrongCredentials() throws Exception {
            when(auth.login(any(LoginRequest.class)))
                    .thenThrow(new AuthException(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập chưa đúng"));

            mvc.perform(authPost("login", LOGIN_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Thông tin đăng nhập chưa đúng"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        @Test
        void rejectsMissingCredentialsBeforeCallingTheService() throws Exception {
            mvc.perform(authPost("login", "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.identifier").isString())
                    .andExpect(jsonPath("$.fieldErrors.password").isString());
            verifyNoInteractions(auth);
        }
    }

    @Nested
    class Logout {
        @Test
        void leavesTheRoomAndExpiresTheCookieForAnAuthenticatedAccount() throws Exception {
            authenticate(account());

            mvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().value("tictactoe_session", ""))
                    .andExpect(cookie().maxAge("tictactoe_session", 0))
                    .andExpect(cookie().path("tictactoe_session", "/api"))
                    .andExpect(cookie().httpOnly("tictactoe_session", true));

            verify(rooms).leave(7L);
        }

        @Test
        void expiresTheCookieWithoutTouchingRoomsWhenThereIsNoPrincipal() throws Exception {
            mvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("tictactoe_session=;")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            verifyNoInteractions(rooms);
        }

        @Test
        void reportsFailureWhenTheRoomServiceCannotReleaseTheAccount() throws Exception {
            authenticate(account());
            doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hiệu lực"))
                    .when(rooms).leave(7L);

            mvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Nested
    class ForgotPassword {
        @Test
        void returnsAcceptedWithoutReturningAccountDetails() throws Exception {
            mvc.perform(authPost("forgot-password", """
                            {"email":"player@example.test"}
                            """))
                    .andExpect(status().isAccepted())
                    .andExpect(content().string(""));
            verify(auth).forgotPassword(new ForgotPasswordRequest("player@example.test"));
        }

        @Test
        void rejectsAnInvalidEmailBeforeCallingTheService() throws Exception {
            mvc.perform(authPost("forgot-password", """
                            {"email":"not-an-email"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").isString());
            verifyNoInteractions(auth);
        }
    }

    @Nested
    class ResetPassword {
        @Test
        void returnsNoContentAndExpiresTheOldCookieAfterReset() throws Exception {
            mvc.perform(authPost("reset-password", RESET_JSON))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            verify(auth).resetPassword(new ResetPasswordRequest(TOKEN, "new-password"));
        }

        @Test
        void rejectsAMalformedTokenAndShortPasswordBeforeCallingTheService() throws Exception {
            mvc.perform(authPost("reset-password", """
                            {"token":"invalid","password":"short"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.token").isString())
                    .andExpect(jsonPath("$.fieldErrors.password").isString());
            verifyNoInteractions(auth);
        }

        @Test
        void returnsTheExpiredTokenFieldErrorWithoutClearingTheCookie() throws Exception {
            doThrow(new AuthException(HttpStatus.BAD_REQUEST, "Liên kết đã hết hạn", "token"))
                    .when(auth).resetPassword(any(ResetPasswordRequest.class));

            mvc.perform(authPost("reset-password", RESET_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.token").value("Liên kết đã hết hạn"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    private static User account() {
        User user = new User("player", "player@example.test", "test-hash", "Player");
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    private static void authenticate(User user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private static MockHttpServletRequestBuilder authPost(String path, String body) {
        return post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
