package com.tictactoe.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Nhập tên đăng nhập hoặc email")
        @Size(max = 100, message = "Tên đăng nhập hoặc email tối đa 100 ký tự")
        String identifier,

        @NotBlank(message = "Nhập mật khẩu")
        @Size(max = 64, message = "Mật khẩu tối đa 64 ký tự")
        String password
) {
}
