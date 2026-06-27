package com.smartlock.controller;

import com.smartlock.dto.ApiResponse;
import com.smartlock.dto.LockAddRequest;
import com.smartlock.dto.PageResponse;
import com.smartlock.service.LockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/lock")
@RequiredArgsConstructor
public class LockController {

    private final LockService lockService;

    @GetMapping("/list")
    public ApiResponse<?> getLockList(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("Get lock list: userId={}, pageNo={}, pageSize={}", userId, pageNo, pageSize);
        if (pageSize <= 0 || pageSize > 100) pageSize = 20;
        PageResponse<Map<String, Object>> result = lockService.getLockList(userId, pageNo, pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> getLockDetail(
            @RequestParam Long lockId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("Get lock detail: userId={}, lockId={}", userId, lockId);
        Map<String, Object> lock = lockService.getLockDetail(userId, lockId);
        return ApiResponse.success(lock);
    }

    @PostMapping("/add")
    public ApiResponse<Map<String, Object>> addLock(
            @Valid @RequestBody LockAddRequest request,
            HttpServletRequest requestAttr) {
        Long userId = (Long) requestAttr.getAttribute("userId");
        log.info("Add lock: userId={}, lockName={}", userId, request.getLockName());
        Map<String, Object> result = lockService.addLock(userId, request);
        return ApiResponse.success(result);
    }

    @PostMapping("/delete")
    public ApiResponse<?> deleteLock(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long lockId = ((Number) body.get("lockId")).longValue();
        String password = (String) body.get("password");
        log.info("Delete lock request: userId={}, lockId={}, passwordLength={}", userId, lockId, password != null ? password.length() : 0);
        lockService.deleteLock(userId, lockId, password);
        return ApiResponse.success(null);
    }

    @GetMapping("/unlockData")
    public ApiResponse<Map<String, Object>> getUnlockData(
            @RequestParam Long lockId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("Get unlock data: userId={}, lockId={}", userId, lockId);
        Map<String, Object> data = lockService.getUnlockData(userId, lockId);
        return ApiResponse.success(data);
    }

    @PostMapping("/updateData")
    public ApiResponse<?> updateLockData(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long lockId = ((Number) body.get("lockId")).longValue();
        String lockData = (String) body.get("lockData");
        log.info("Update lock data: userId={}, lockId={}", userId, lockId);
        lockService.updateLockData(userId, lockId, lockData);
        return ApiResponse.success(null);
    }

    @PostMapping("/updateName")
    public ApiResponse<?> updateLockName(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long lockId = ((Number) body.get("lockId")).longValue();
        String lockName = (String) body.get("lockName");
        log.info("Update lock name: userId={}, lockId={}, lockName={}", userId, lockId, lockName);
        lockService.updateLockName(userId, lockId, lockName);
        return ApiResponse.success(null);
    }
}
