package com.tictactoe.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Username không được để trống")
        @Size(min = 3, max = 30, message = "Username phải từ 3 đến 30 ký tự")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                message = "Username chỉ gồm chữ cái, chữ số và dấu gạch dưới")
        String username,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 64, message = "Mật khẩu phải từ 8 đến 64 ký tự")
        String password,

        @Size(max = 50, message = "Tên hiển thị tối đa 50 ký tự")
        String displayName
) {
}