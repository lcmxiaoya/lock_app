package com.smartlock.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LockAddRequest {
    @NotBlank(message = "Lock data is required")
    private String lockData;

    private String lockName;
    private String lockAlias;
    private String lockMac;
}
