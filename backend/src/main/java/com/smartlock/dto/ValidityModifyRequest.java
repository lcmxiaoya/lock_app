package com.smartlock.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 修改 IC 卡 / 指纹有效期的请求体（recordId = 本地表主键 id） */
@Data
public class ValidityModifyRequest {
    @NotNull(message = "Record ID is required")
    private Long recordId;

    private Long startDate = 0L;
    private Long endDate = 0L;

    /** 仅指纹周期型用 */
    private String cyclicConfig;
}
