package com.smartlock.controller;

import com.smartlock.dto.ApiResponse;
import com.smartlock.dto.KeySendRequest;
import com.smartlock.dto.PageResponse;
import com.smartlock.service.KeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/key")
@RequiredArgsConstructor
public class KeyController {

    private final KeyService keyService;

    @GetMapping("/list")
    public ApiResponse<?> getKeyList(
            @RequestParam(required = false) Long lockId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (pageSize <= 0 || pageSize > 100) pageSize = 20;
        PageResponse<Map<String, Object>> result = keyService.getKeyList(userId, lockId, pageNo, pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> getKeyDetail(
            @RequestParam Long keyId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> key = keyService.getKeyDetail(userId, keyId);
        return ApiResponse.success(key);
    }

    @PostMapping("/send")
    public ApiResponse<Map<String, Object>> sendKey(
            @Valid @RequestBody KeySendRequest request,
            HttpServletRequest requestAttr) {
        Long userId = (Long) requestAttr.getAttribute("userId");
        Map<String, Object> result = keyService.sendKey(userId, request);
        return ApiResponse.success(result);
    }

    /** 授权管理员：等价于"先 send 再 authorize"两步组合，由后端在同一事务内完成 */
    @PostMapping("/sendAdmin")
    public ApiResponse<Map<String, Object>> sendAdminKey(
            @Valid @RequestBody KeySendRequest request,
            HttpServletRequest requestAttr) {
        Long userId = (Long) requestAttr.getAttribute("userId");
        Map<String, Object> result = keyService.sendAdminKey(userId, request);
        return ApiResponse.success(result);
    }

    /** 获取该锁所有"管理员"钥匙；仅锁拥有者可查 */
    @GetMapping("/adminList")
    public ApiResponse<List<Map<String, Object>>> getAdminKeyList(
            @RequestParam Long lockId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> result = keyService.getAdminKeyList(userId, lockId);
        return ApiResponse.success(result);
    }

    @PostMapping("/delete")
    public ApiResponse<?> deleteKey(
            @RequestBody Map<String, Long> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long keyId = body.get("keyId");
        keyService.deleteKey(userId, keyId);
        return ApiResponse.success(null);
    }
}
