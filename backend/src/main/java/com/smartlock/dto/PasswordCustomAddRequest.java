package com.smartlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PasswordCustomAddRequest {
    @NotNull(message = "Lock ID is required")
    private Long lockId;

    @NotBlank(message = "Password is required")
    @Pattern(regexp = "^\\d{6,9}$", message = "Password must be 6-9 digits")
    private String keyboardPwd;

    private Integer keyboardPwdId;

    private String pwdName;

    private Integer pwdType;

    private Long startDate;

    private Long endDate;
}
