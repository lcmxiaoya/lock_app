package com.smartlock.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PasswordGenerateRequest {
    @NotNull(message = "Lock ID is required")
    private Long lockId;

    @NotNull(message = "Password type is required")
    private Integer pwdType;

    private String pwdName;
    private Long startDate;
    private Long endDate;
}
