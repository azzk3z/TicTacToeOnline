package com.tictactoe.backend.dto.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

public record JoinRoomRequest(
        @NotBlank(message = "Hãy nhập mã phòng")
        @Pattern(regexp = "[A-HJ-NP-Z2-9]{8}", message = "Mã phòng phải có 8 ký tự hợp lệ")
        String code
) {
    public JoinRoomRequest {
        if (code != null) {
            code = code.strip().toUpperCase(Locale.ROOT);
        }
    }
}
