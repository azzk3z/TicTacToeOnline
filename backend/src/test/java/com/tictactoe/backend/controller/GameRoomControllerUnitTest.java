package com.tictactoe.backend.controller;

import com.tictactoe.backend.dto.room.RoomResponse;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.exception.GlobalExceptionHandler;
import com.tictactoe.backend.service.GameRoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameRoomControllerUnitTest {
    private static final long ACCOUNT_ID = 42L;
    private static final String CODE = "6HK9MQ2R";

    @Mock
    private GameRoomService rooms;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var account = new User("player", "player@example.test", "test-hash", "Player");
        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account, null, List.of()));
        SecurityContextHolder.setContext(context);

        mvc = MockMvcBuilders.standaloneSetup(new GameRoomController(rooms))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class CreateRoom {
        @Test
        void usesTheAuthenticatedAccountToCreateTheRoom() throws Exception {
            when(rooms.create(ACCOUNT_ID)).thenReturn(new RoomResponse(CODE, new RoomResponse.Player(ACCOUNT_ID, "Player"), null));

            mvc.perform(post("/api/rooms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(CODE))
                    .andExpect(jsonPath("$.host.id").value(ACCOUNT_ID))
                    .andExpect(jsonPath("$.guest").doesNotExist());
        }
    }

    @Nested
    class JoinRoom {
        @Test
        void normalizesLowercaseAndWhitespaceBeforeJoining() throws Exception {
            var host = new RoomResponse.Player(1L, "Host");
            var guest = new RoomResponse.Player(ACCOUNT_ID, "Player");
            when(rooms.join(ACCOUNT_ID, CODE)).thenReturn(new RoomResponse(CODE, host, guest));

            mvc.perform(post("/api/rooms/join").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":" 6hk9mq2r "}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.guest.displayName").value("Player"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"{}", "{\"code\":\"\"}", "{\"code\":\"abc\"}", "{\"code\":\"I0ABCD23\"}"})
        void rejectsMissingBlankOrMalformedCodesBeforeCallingTheService(String body) throws Exception {
            mvc.perform(post("/api/rooms/join").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.code").isString());
            verifyNoInteractions(rooms);
        }
    }
}
