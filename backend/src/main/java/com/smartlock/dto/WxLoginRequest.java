package com.smartlock.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {

    /** 小程序 wx.login() 颁发的临时 code，用于换 openid + session_key */
    @NotBlank(message = "code is required")
    private String code;

    /** getPhoneNumber 回调里的 code，用于换手机号 */
    @NotBlank(message = "phoneCode is required")
    private String phoneCode;
}
