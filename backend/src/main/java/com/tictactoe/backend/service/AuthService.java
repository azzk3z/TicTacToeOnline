package com.tictactoe.backend.service;

import com.tictactoe.backend.dto.auth.RegisterRequest;
import com.tictactoe.backend.dto.user.UserResponse;
import com.tictactoe.backend.entity.User;
import com.tictactoe.backend.exception.DuplicateResourceException;
import com.tictactoe.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
                passwordEncoder.encode(request.password()),
                displayName
        );

        return UserResponse.from(userRepository.save(user));
    }
}