package com.tictactoe.backend.controller;

import com.tictactoe.backend.dto.room.JoinRoomRequest;
import com.tictactoe.backend.dto.room.RoomResponse;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.service.GameRoomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class GameRoomController {
    private final GameRoomService rooms;

    public GameRoomController(GameRoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping("/current")
    public ResponseEntity<RoomResponse> current(@AuthenticationPrincipal User user) {
        return rooms.current(user.getId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public RoomResponse create(@AuthenticationPrincipal User user) {
        return rooms.create(user.getId());
    }

    @PostMapping("/join")
    public RoomResponse join(@AuthenticationPrincipal User user, @Valid @RequestBody JoinRoomRequest request) {
        return rooms.join(user.getId(), request.code());
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal User user) {
        rooms.leave(user.getId());
        return ResponseEntity.noContent().build();
    }
}
