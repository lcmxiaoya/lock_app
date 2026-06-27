package com.smartlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FingerprintAddRequest {
    @NotNull(message = "Lock ID is required")
    private Long lockId;

    /** 蓝牙端 addFingerprint 拿到的指纹编号 */
    @NotBlank(message = "Fingerprint number is required")
    private String fingerprintNumber;

    private String fingerprintName;

    /** 1=normal（限时/永久），4=cyclic（周期型） */
    @NotNull
    private Integer fingerprintType = 1;

    /** 0 = 永久 */
    private Long startDate = 0L;
    private Long endDate = 0L;

    /** 周期型 JSON 数组字符串：[{startTime,endTime,weekDay}]；非周期型传 null/空 */
    private String cyclicConfig;
}
