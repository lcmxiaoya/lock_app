package com.smartlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KeySendRequest {
    @NotBlank(message = "Receiver username is required")
    private String receiverUsername;

    @NotNull(message = "Lock ID is required")
    private Long lockId;

    @NotBlank(message = "Key name is required")
    private String keyName;

    @NotNull(message = "Start date is required")
    private Long startDate;

    @NotNull(message = "End date is required")
    private Long endDate;

    private String remarks;
    private Integer remoteEnable = 2;
}
