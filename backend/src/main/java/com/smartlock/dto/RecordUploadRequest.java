package com.smartlock.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecordUploadRequest {
    @NotNull(message = "Lock ID is required")
    private Long lockId;

    private String action;

    private Long recordTime;
}
