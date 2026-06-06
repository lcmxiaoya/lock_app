package com.smartlock.controller;

import com.smartlock.dto.ApiResponse;
import com.smartlock.dto.PageResponse;
import com.smartlock.dto.PasswordCustomAddRequest;
import com.smartlock.dto.PasswordGenerateRequest;
import com.smartlock.dto.PasswordResetRequest;
import com.smartlock.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pwd")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<Map<String, Object>>> getPasswordList(
            @RequestParam Long lockId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        PageResponse<Map<String, Object>> result = passwordService.getPasswordList(userId, lockId, pageNo, pageSize);
        return ApiResponse.success(result);
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generatePassword(
            @Valid @RequestBody PasswordGenerateRequest request,
            HttpServletRequest requestAttr) {
        Long userId = (Long) requestAttr.getAttribute("userId");
        Map<String, Object> result = passwordService.generatePassword(userId, request);
        return ApiResponse.success(result);
    }

    @PostMapping("/custom/add")
    public ApiResponse<Map<String, Object>> addCustomPassword(
            @Valid @RequestBody PasswordCustomAddRequest request,
            HttpServletRequest requestAttr) {
        Long userId = (Long) requestAttr.getAttribute("userId");
        Map<String, Object> result = passwordService.addCustomPassword(userId, request);
        return ApiResponse.success(result);
    }

    @PostMapping("/delete")
    public ApiResponse<?> deletePassword(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long lockId = ((Number) body.get("lockId")).longValue();
        Long pwdId = ((Number) body.get("pwdId")).longValue();
        passwordService.deletePassword(userId, lockId, pwdId);
        return ApiResponse.success(null);
    }

    @PostMapping("/reset")
    public ApiResponse<?> resetPasswords(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest requestAttr) {
        Long userId = (Long) requestAttr.getAttribute("userId");
        passwordService.resetPasswords(userId, request.getLockId(), request);
        return ApiResponse.success(null);
    }

    @PostMapping("/change")
    public ApiResponse<Map<String, Object>> changePassword(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long lockId = ((Number) body.get("lockId")).longValue();
        Long pwdId = ((Number) body.get("pwdId")).longValue();
        String newPwd = (String) body.get("newKeyboardPwd");
        String pwdName = (String) body.get("keyboardPwdName");
        Long startDate = body.get("startDate") != null ? ((Number) body.get("startDate")).longValue() : null;
        Long endDate = body.get("endDate") != null ? ((Number) body.get("endDate")).longValue() : null;
        Map<String, Object> result = passwordService.changePassword(userId, lockId, pwdId, newPwd, pwdName, startDate, endDate);
        return ApiResponse.success(result);
    }
}
