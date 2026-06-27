package com.smartlock.controller;

import com.smartlock.dto.ApiResponse;
import com.smartlock.dto.FingerprintAddRequest;
import com.smartlock.dto.PageResponse;
import com.smartlock.dto.ValidityModifyRequest;
import com.smartlock.service.FingerprintService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fingerprint")
@RequiredArgsConstructor
public class FingerprintController {

    private final FingerprintService fingerprintService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<Map<String, Object>>> getList(
            @RequestParam Long lockId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (pageSize <= 0 || pageSize > 100) pageSize = 20;
        return ApiResponse.success(fingerprintService.getList(userId, lockId, pageNo, pageSize));
    }

    @PostMapping("/add")
    public ApiResponse<Map<String, Object>> add(
            @Valid @RequestBody FingerprintAddRequest body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.success(fingerprintService.recordAdd(userId, body));
    }

    @PostMapping("/delete")
    public ApiResponse<?> delete(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long recordId = ((Number) body.get("recordId")).longValue();
        fingerprintService.delete(userId, recordId);
        return ApiResponse.success(null);
    }

    @PostMapping("/modifyValidity")
    public ApiResponse<?> modifyValidity(
            @Valid @RequestBody ValidityModifyRequest body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        fingerprintService.modifyValidity(userId, body);
        return ApiResponse.success(null);
    }
}
