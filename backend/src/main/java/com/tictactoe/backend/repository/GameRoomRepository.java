package com.tictactoe.backend.repository;

import com.tictactoe.backend.entity.GameRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
    @Query("""
            select room from GameRoom room join fetch room.host left join fetch room.guest
            where room.host.id = :userId or room.guest.id = :userId
            """)
    Optional<GameRoom> findForMember(@Param("userId") Long userId);

    @Query("select room.id from GameRoom room where room.host.id = :userId or room.guest.id = :userId")
    Optional<Long> findIdForMember(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GameRoom> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GameRoom> findLockedByCode(String code);
}
