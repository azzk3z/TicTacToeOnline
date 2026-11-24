package com.tictactoe.backend.repository;

import com.tictactoe.backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByUsername(String username);

    List<User> findTop2ByUsernameIgnoreCaseOrderByIdAsc(String username);

    Optional<User> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByPasswordResetTokenHash(String tokenHash);
}
