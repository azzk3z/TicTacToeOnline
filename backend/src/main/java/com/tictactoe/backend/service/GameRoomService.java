package com.tictactoe.backend.service;

import com.tictactoe.backend.dto.room.RoomResponse;
import com.tictactoe.backend.entity.GameRoom;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.repository.GameRoomRepository;
import com.tictactoe.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Optional;

@Service
public class GameRoomService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final GameRoomRepository rooms;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();

    public GameRoomService(GameRoomRepository rooms, UserRepository users) {
        this.rooms = rooms;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<RoomResponse> current(Long userId) {
        return rooms.findForMember(userId).map(RoomResponse::from);
    }

    @Transactional
    public RoomResponse create(Long userId) {
        User host = lockUser(userId);
        GameRoom room = rooms.findForMember(userId)
                .orElseGet(() -> rooms.save(new GameRoom(generateCode(), host)));
        return RoomResponse.from(room);
    }

    @Transactional
    public RoomResponse join(Long userId, String code) {
        User guest = lockUser(userId);
        GameRoom room = rooms.findLockedByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng với mã này"));
        if (room.hasMember(userId)) {
            return RoomResponse.from(room);
        }
        if (rooms.findIdForMember(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hãy rời phòng hiện tại trước khi tham gia phòng khác");
        }
        if (room.getGuest() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng đã đủ hai người chơi");
        }
        room.setGuest(guest);
        return RoomResponse.from(room);
    }

    @Transactional
    public void leave(Long userId) {
        lockUser(userId);
        rooms.findIdForMember(userId).flatMap(rooms::findLockedById).ifPresent(room -> {
            if (room.isHost(userId)) {
                rooms.delete(room);
            } else {
                room.setGuest(null);
            }
        });
    }

    private User lockUser(Long userId) {
        // Serialize each account's room changes before taking any room lock.
        return users.findLockedById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hiệu lực"));
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(GameRoom.CODE_LENGTH);
        for (int digit = 0; digit < GameRoom.CODE_LENGTH; digit++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
