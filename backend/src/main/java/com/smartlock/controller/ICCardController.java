package com.smartlock.controller;

import com.smartlock.dto.ApiResponse;
import com.smartlock.dto.ICCardAddRequest;
import com.smartlock.dto.PageResponse;
import com.smartlock.dto.ValidityModifyRequest;
import com.smartlock.service.ICCardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/icCard")
@RequiredArgsConstructor
public class ICCardController {

    private final ICCardService icCardService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<Map<String, Object>>> getList(
            @RequestParam Long lockId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (pageSize <= 0 || pageSize > 100) pageSize = 20;
        return ApiResponse.success(icCardService.getList(userId, lockId, pageNo, pageSize));
    }

    @PostMapping("/add")
    public ApiResponse<Map<String, Object>> add(
            @Valid @RequestBody ICCardAddRequest body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.success(icCardService.recordAdd(userId, body));
    }

    @PostMapping("/delete")
    public ApiResponse<?> delete(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long recordId = ((Number) body.get("recordId")).longValue();
        icCardService.delete(userId, recordId);
        return ApiResponse.success(null);
    }

    @PostMapping("/modifyValidity")
    public ApiResponse<?> modifyValidity(
            @Valid @RequestBody ValidityModifyRequest body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        icCardService.modifyValidity(userId, body);
        return ApiResponse.success(null);
    }
}
