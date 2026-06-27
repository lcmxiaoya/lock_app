package com.smartlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ICCardAddRequest {
    @NotNull(message = "Lock ID is required")
    private Long lockId;

    /** 蓝牙端 addICCard 拿到的物理卡号 */
    @NotBlank(message = "Card number is required")
    private String cardNumber;

    private String cardName;

    /** 0 = 永久 */
    private Long startDate = 0L;
    private Long endDate = 0L;
}
