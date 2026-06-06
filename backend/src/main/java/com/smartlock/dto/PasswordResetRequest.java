package com.smartlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotNull(message = "Lock ID is required")
    private Long lockId;

    @NotBlank(message = "New lock data is required")
    private String newLockData;

    @NotBlank(message = "Password info is required")
    private String pwdInfo;

    @NotNull(message = "Timestamp is required")
    private Long timestamp;
}
