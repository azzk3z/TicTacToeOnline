package com.tictactoe.backend.dto.room;

import com.tictactoe.backend.entity.GameRoom;
import com.tictactoe.backend.entity.User;

public record RoomResponse(String code, Player host, Player guest) {
    public static RoomResponse from(GameRoom room) {
        User guest = room.getGuest();
        return new RoomResponse(room.getCode(), Player.from(room.getHost()), guest == null ? null : Player.from(guest));
    }

    public record Player(Long id, String displayName) {
        private static Player from(User user) {
            String name = user.getDisplayName();
            return new Player(user.getId(), name == null || name.isBlank() ? user.getUsername() : name);
        }
    }
}
