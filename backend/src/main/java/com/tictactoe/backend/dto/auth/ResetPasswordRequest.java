package com.tictactoe.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu liên kết đặt lại mật khẩu")
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "Liên kết đặt lại mật khẩu không hợp lệ")
        String token,

        @NotBlank(message = "Nhập mật khẩu mới")
        @Size(min = 8, max = 64, message = "Mật khẩu phải từ 8 đến 64 ký tự")
        String password
) {
}
