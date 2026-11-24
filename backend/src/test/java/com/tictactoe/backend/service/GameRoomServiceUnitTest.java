package com.tictactoe.backend.service;

import com.tictactoe.backend.entity.GameRoom;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.repository.GameRoomRepository;
import com.tictactoe.backend.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRoomServiceUnitTest {
    private static final String CODE = "6HK9MQ2R";
    private static final long HOST_ID = 11L;
    private static final long GUEST_ID = 22L;

    @Mock
    private GameRoomRepository rooms;
    @Mock
    private UserRepository users;

    @Nested
    class CreateRoom {
        @Test
        void savesAnEmptyRoomWithAValidCodeAndTheAuthenticatedHost() {
            User host = player(HOST_ID, "host");
            when(users.findLockedById(HOST_ID)).thenReturn(Optional.of(host));
            when(rooms.save(any(GameRoom.class))).thenAnswer(call -> call.getArgument(0));

            var result = service().create(HOST_ID);

            var saved = ArgumentCaptor.forClass(GameRoom.class);
            verify(rooms).save(saved.capture());
            assertThat(saved.getValue().getHost()).isSameAs(host);
            assertThat(result.code()).matches("[A-HJ-NP-Z2-9]{8}").isEqualTo(saved.getValue().getCode());
            assertThat(result.host().id()).isEqualTo(HOST_ID);
            assertThat(result.guest()).isNull();
        }

        @Test
        void returnsTheExistingRoomIfTheAccountAlreadyHasASeat() {
            User guest = player(GUEST_ID, "guest");
            GameRoom existing = waitingRoom();
            existing.setGuest(guest);
            when(users.findLockedById(GUEST_ID)).thenReturn(Optional.of(guest));
            when(rooms.findForMember(GUEST_ID)).thenReturn(Optional.of(existing));

            var result = service().create(GUEST_ID);

            assertThat(result.code()).isEqualTo(CODE);
            assertThat(result.guest().id()).isEqualTo(GUEST_ID);
            verify(rooms, never()).save(any());
        }

        @Test
        void rejectsAnAccountThatNoLongerExists() {
            var error = assertThrows(ResponseStatusException.class, () -> service().create(HOST_ID));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(users).findLockedById(HOST_ID);
            verifyNoInteractions(rooms);
        }

        @Test
        void reportsAPersistenceFailureInsteadOfReturningAnUnsavedRoom() {
            when(users.findLockedById(HOST_ID)).thenReturn(Optional.of(player(HOST_ID, "host")));
            var persistenceFailure = new DataIntegrityViolationException("Test constraint violation");
            when(rooms.save(any(GameRoom.class))).thenThrow(persistenceFailure);

            assertThat(assertThrows(DataIntegrityViolationException.class, () -> service().create(HOST_ID)))
                    .isSameAs(persistenceFailure);
        }
    }

    @Nested
    class JoinRoom {
        @Test
        void assignsTheOpenGuestSeatAfterLockingTheAccountAndRoom() {
            User guest = player(GUEST_ID, "guest");
            GameRoom room = waitingRoom();
            when(users.findLockedById(GUEST_ID)).thenReturn(Optional.of(guest));
            when(rooms.findLockedByCode(CODE)).thenReturn(Optional.of(room));

            var result = service().join(GUEST_ID, CODE);

            var locks = inOrder(users, rooms);
            locks.verify(users).findLockedById(GUEST_ID);
            locks.verify(rooms).findLockedByCode(CODE);
            assertThat(room.getGuest()).isSameAs(guest);
            assertThat(result.code()).isEqualTo(CODE);
            assertThat(result.host().id()).isEqualTo(HOST_ID);
            assertThat(result.guest().id()).isEqualTo(GUEST_ID);
        }

        @Test
        void allowsAnExistingGuestToRetryTheSameJoin() {
            User guest = player(GUEST_ID, "guest");
            GameRoom room = waitingRoom();
            room.setGuest(guest);
            when(users.findLockedById(GUEST_ID)).thenReturn(Optional.of(guest));
            when(rooms.findLockedByCode(CODE)).thenReturn(Optional.of(room));

            assertThat(service().join(GUEST_ID, CODE).guest().id()).isEqualTo(GUEST_ID);
            verify(rooms, never()).findIdForMember(GUEST_ID);
        }

        @Test
        void rejectsACodeWithNoMatchingRoom() {
            when(users.findLockedById(GUEST_ID)).thenReturn(Optional.of(player(GUEST_ID, "guest")));

            var error = assertThrows(ResponseStatusException.class, () -> service().join(GUEST_ID, CODE));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(rooms).findLockedByCode(CODE);
        }

        @Test
        void rejectsAFullRoomWithoutReplacingItsGuest() {
            User currentGuest = player(33L, "current_guest");
            GameRoom room = waitingRoom();
            room.setGuest(currentGuest);
            when(users.findLockedById(GUEST_ID)).thenReturn(Optional.of(player(GUEST_ID, "guest")));
            when(rooms.findLockedByCode(CODE)).thenReturn(Optional.of(room));

            var error = assertThrows(ResponseStatusException.class, () -> service().join(GUEST_ID, CODE));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(error.getReason()).contains("đủ hai người");
            assertThat(room.getGuest()).isSameAs(currentGuest);
        }

        @Test
        void rejectsAnAccountInAnotherRoomEvenIfTheTargetHasAnOpenSeat() {
            GameRoom room = waitingRoom();
            when(users.findLockedById(GUEST_ID)).thenReturn(Optional.of(player(GUEST_ID, "guest")));
            when(rooms.findLockedByCode(CODE)).thenReturn(Optional.of(room));
            when(rooms.findIdForMember(GUEST_ID)).thenReturn(Optional.of(5L));

            var error = assertThrows(ResponseStatusException.class, () -> service().join(GUEST_ID, CODE));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(error.getReason()).contains("rời phòng hiện tại");
            assertThat(room.getGuest()).isNull();
        }

        @Test
        void rejectsAnAccountThatNoLongerExistsBeforeLookingUpTheRoom() {
            var error = assertThrows(ResponseStatusException.class, () -> service().join(GUEST_ID, CODE));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(rooms);
        }
    }

    private GameRoomService service() {
        return new GameRoomService(rooms, users);
    }

    private static GameRoom waitingRoom() {
        return new GameRoom(CODE, player(HOST_ID, "host"));
    }

    private static User player(long id, String username) {
        var player = new User(username, username + "@example.test", "test-hash", username);
        ReflectionTestUtils.setField(player, "id", id);
        return player;
    }
}
