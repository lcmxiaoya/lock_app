package com.smartlock.controller;

import com.smartlock.dto.ApiResponse;
import com.smartlock.dto.RecordUploadRequest;
import com.smartlock.entity.EKey;
import com.smartlock.entity.User;
import com.smartlock.repository.EKeyRepository;
import com.smartlock.repository.UserRepository;
import com.smartlock.service.RecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/record")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;
    private final EKeyRepository eKeyRepository;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ApiResponse<?> uploadRecord(
            @Valid @RequestBody RecordUploadRequest request,
            HttpServletRequest requestAttr) {
        Long userId = (Long) requestAttr.getAttribute("userId");
        recordService.uploadRecord(userId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/sync")
    public ApiResponse<?> syncOperationLog(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long lockId = ((Number) body.get("lockId")).longValue();
        String logJson = (String) body.get("log");
        recordService.syncOperationLog(userId, lockId, logJson);
        return ApiResponse.success(null);
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getRecordList(
            @RequestParam Long lockId,
            @RequestParam(required = false) Long keyUserId,
            @RequestParam(required = false) String keyboardPwd,
            @RequestParam(required = false) Integer keyId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Long operatorUid = null;
        if (keyId != null) {
            Optional<EKey> ekeyOpt = eKeyRepository.findByKeyId(keyId);
            if (ekeyOpt.isPresent()) {
                Long ekeyUserId = ekeyOpt.get().getUserId();
                Optional<User> userOpt = userRepository.findById(ekeyUserId);
                if (userOpt.isPresent() && userOpt.get().getTtUid() != null) {
                    operatorUid = userOpt.get().getTtUid().longValue();
                }
            }
        }

        Map<String, Object> records = recordService.getRecordList(userId, lockId, keyUserId, keyboardPwd, operatorUid, pageNo, pageSize);
        return ApiResponse.success(records);
    }
}
