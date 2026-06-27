package com.smartlock.service;

import com.smartlock.dto.PageResponse;
import com.smartlock.dto.PasswordCustomAddRequest;
import com.smartlock.dto.PasswordGenerateRequest;
import com.smartlock.dto.PasswordResetRequest;
import com.smartlock.entity.EKey;
import com.smartlock.entity.Lock;
import com.smartlock.entity.Passcode;
import com.smartlock.entity.User;
import com.smartlock.exception.BusinessException;
import com.smartlock.repository.EKeyRepository;
import com.smartlock.repository.LockRepository;
import com.smartlock.repository.PasscodeRepository;
import com.smartlock.repository.UserRepository;
import com.smartlock.util.TTLockClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final PasscodeRepository passcodeRepository;
    private final LockRepository lockRepository;
    private final EKeyRepository eKeyRepository;
    private final UserRepository userRepository;
    private final TTLockClient ttLockClient;
    private final UserService userService;
    private final LockService lockService;
    private final LockPermissionService permission;

    private Lock findLockById(Long localLockId) {
        return lockRepository.findById(localLockId)
                .orElseThrow(() -> new BusinessException(3003, "Lock not found"));
    }

    private void checkLockAccess(Long userId, Long localLockId) {
        if (!permission.canAccess(userId, localLockId)) {
            throw new BusinessException(3003, "No permission to access this lock");
        }
    }

    /** owner 与 admin 都可管理本锁的密码（生成 / 自定义 / 删除 / 重置 / 修改） */
    private void requireManage(Long userId, Long localLockId) {
        if (!permission.canManage(userId, localLockId)) {
            throw new BusinessException(4003, "Only lock owner or admin can perform this operation");
        }
    }

    @Transactional
    public Map<String, Object> generatePassword(Long userId, PasswordGenerateRequest request) {
        requireManage(userId, request.getLockId());
        User user = userService.getUserById(userId);
        Lock lock = findLockById(request.getLockId());

        long now = System.currentTimeMillis();
        Long startDate = request.getStartDate();
        Long endDate = request.getEndDate();

        // TTLock API requires startDate > 0 for all password types
        if (startDate == null || startDate <= 0) {
            startDate = now;
        }
        // Default endDate for single-use and delete types
        if (endDate == null || endDate <= 0) {
            if (request.getPwdType() == 1 || request.getPwdType() == 4) {
                endDate = now + 6 * 60 * 60 * 1000L;
            } else {
                endDate = 0L;
            }
        }

        Map<String, Object> result = ttLockClient.getRandomPassword(
                getValidAccessToken(user),
                lock.getLockId().intValue(),
                lock.getKeyboardPwdVersion(),
                request.getPwdType(),
                request.getPwdName(),
                startDate,
                endDate);

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "Failed to generate password: " + result.get("errmsg"));
        }

        String keyboardPwd = (String) result.get("keyboardPwd");
        Integer keyboardPwdId = ((Number) result.get("keyboardPwdId")).intValue();

        Passcode passcode = new Passcode();
        passcode.setUserId(userId);
        passcode.setLockId(request.getLockId());
        passcode.setKeyboardPwdId(keyboardPwdId);
        passcode.setKeyboardPwd(keyboardPwd);
        passcode.setKeyboardPwdName(request.getPwdName());
        passcode.setPwdType(request.getPwdType());
        passcode.setIsCustom(0);
        passcode.setStartDate(startDate);
        passcode.setEndDate(endDate);
        passcode.setStatus(1);
        passcodeRepository.save(passcode);

        Map<String, Object> response = new HashMap<>();
        response.put("keyboardPwd", keyboardPwd);
        response.put("keyboardPwdId", keyboardPwdId);
        response.put("pwdId", passcode.getId());

        return response;
    }

    @Transactional
    public Map<String, Object> addCustomPassword(Long userId, PasswordCustomAddRequest request) {
        requireManage(userId, request.getLockId());
        Lock lock = findLockById(request.getLockId());

        Integer keyboardPwdId = request.getKeyboardPwdId();

        if (keyboardPwdId == null) {
            User user = userService.getUserById(userId);
            Map<String, Object> result = ttLockClient.addCustomPassword(
                    getValidAccessToken(user),
                    lock.getLockId().intValue(),
                    request.getKeyboardPwd(),
                    request.getPwdName(),
                    request.getStartDate(),
                    request.getEndDate());

            if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
                throw new BusinessException(6001, "Failed to add custom password: " + result.get("errmsg"));
            }

            keyboardPwdId = ((Number) result.get("keyboardPwdId")).intValue();
        }

        Passcode passcode = new Passcode();
        passcode.setUserId(userId);
        passcode.setLockId(request.getLockId());
        passcode.setKeyboardPwdId(keyboardPwdId);
        passcode.setKeyboardPwd(request.getKeyboardPwd());
        passcode.setKeyboardPwdName(request.getPwdName());
        passcode.setPwdType(request.getPwdType() != null ? request.getPwdType() : 2);
        passcode.setIsCustom(1);
        passcode.setStartDate(request.getStartDate());
        passcode.setEndDate(request.getEndDate());
        passcode.setStatus(1);
        passcodeRepository.save(passcode);

        Map<String, Object> response = new HashMap<>();
        response.put("keyboardPwdId", keyboardPwdId);
        response.put("pwdId", passcode.getId());

        return response;
    }

    public PageResponse<Map<String, Object>> getPasswordList(Long userId, Long localLockId, int pageNo, int pageSize) {
        requireManage(userId, localLockId);

        Page<Passcode> page = passcodeRepository.findByLockId(localLockId, PageRequest.of(pageNo - 1, pageSize));
        long now = System.currentTimeMillis();

        List<Map<String, Object>> list = page.getContent().stream().map(passcode -> {
            Map<String, Object> map = new HashMap<>();
            map.put("pwdId", passcode.getId());
            map.put("keyboardPwdId", passcode.getKeyboardPwdId());
            map.put("keyboardPwd", passcode.getKeyboardPwd());
            map.put("keyboardPwdName", passcode.getKeyboardPwdName());
            map.put("pwdType", passcode.getPwdType());
            map.put("pwdTypeName", getPwdTypeName(passcode.getPwdType()));
            map.put("isCustom", passcode.getIsCustom());
            map.put("startDate", passcode.getStartDate());
            map.put("endDate", passcode.getEndDate());
            map.put("createdAt", passcode.getCreatedAt());

            User sender = userService.getUserById(passcode.getUserId());
            map.put("senderName", sender != null ? sender.getUsername() : "");

            if (passcode.getEndDate() != 0 && passcode.getEndDate() < now && passcode.getPwdType() != 2) {
                map.put("status", 2);
            } else {
                map.put("status", passcode.getStatus());
            }

            return map;
        }).collect(Collectors.toList());

        return PageResponse.of(list, page.getTotalElements(), pageNo, pageSize);
    }

    @Transactional
    public void deletePassword(Long userId, Long localLockId, Long pwdId) {
        requireManage(userId, localLockId);
        Lock lock = findLockById(localLockId);

        Passcode passcode = passcodeRepository.findById(pwdId)
                .orElseThrow(() -> new BusinessException(5001, "Password not found"));

        if (!passcode.getLockId().equals(localLockId)) {
            throw new BusinessException(5001, "Password does not belong to this lock");
        }

        User user = userService.getUserById(userId);

        Map<String, Object> result = ttLockClient.deletePassword(
                getValidAccessToken(user),
                lock.getLockId().intValue(),
                passcode.getKeyboardPwdId());

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            log.warn("TTLock password delete failed (may already be deleted): {}", result.get("errmsg"));
        }

        passcodeRepository.delete(passcode);
    }

    @Transactional
    public void resetPasswords(Long userId, Long localLockId, PasswordResetRequest request) {
        requireManage(userId, localLockId);
        User user = userService.getUserById(userId);
        Lock lock = findLockById(localLockId);

        Map<String, Object> result = ttLockClient.resetKeyboardPassword(
                getValidAccessToken(user),
                lock.getLockId().intValue(),
                request.getPwdInfo(),
                request.getTimestamp());

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "Failed to reset passwords: " + result.get("errmsg"));
        }

        lockService.updateLockData(userId, request.getLockId(), request.getNewLockData());

        passcodeRepository.deleteByLockId(localLockId);
    }

    public Map<String, Object> changePassword(Long userId, Long localLockId, Long pwdId,
                                              String newKeyboardPwd, String keyboardPwdName,
                                              Long startDate, Long endDate) {
        requireManage(userId, localLockId);
        User user = userService.getUserById(userId);
        Lock lock = findLockById(localLockId);

        Passcode passcode = passcodeRepository.findById(pwdId)
                .orElseThrow(() -> new BusinessException(5001, "Password not found"));

        Map<String, Object> result = ttLockClient.changePassword(
                getValidAccessToken(user),
                lock.getLockId().intValue(),
                passcode.getKeyboardPwdId(),
                keyboardPwdName,
                newKeyboardPwd,
                startDate,
                endDate);

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "Failed to change password: " + result.get("errmsg"));
        }

        if (newKeyboardPwd != null) {
            passcode.setKeyboardPwd(newKeyboardPwd);
        }
        if (keyboardPwdName != null) {
            passcode.setKeyboardPwdName(keyboardPwdName);
        }
        if (startDate != null) {
            passcode.setStartDate(startDate);
        }
        if (endDate != null) {
            passcode.setEndDate(endDate);
        }
        passcodeRepository.save(passcode);

        Map<String, Object> response = new HashMap<>();
        response.put("pwdId", passcode.getId());
        response.put("keyboardPwdId", passcode.getKeyboardPwdId());

        return response;
    }

    private String getValidAccessToken(User user) {
        if (user.getTtTokenExpiresAt() == null ||
                System.currentTimeMillis() >= user.getTtTokenExpiresAt() - 24 * 60 * 60 * 1000L) {
            try {
                if (user.getTtRefreshToken() != null) {
                    Map<String, Object> result = ttLockClient.refreshToken(
                            user.getTtRefreshToken(), user.getTtUsername());
                    if (result.containsKey("access_token")) {
                        user.setTtAccessToken((String) result.get("access_token"));
                        user.setTtRefreshToken((String) result.get("refresh_token"));
                        long expiresIn = ((Number) result.get("expires_in")).longValue() * 1000;
                        user.setTtTokenExpiresAt(System.currentTimeMillis() + expiresIn);
                        userRepository.save(user);
                        return user.getTtAccessToken();
                    }
                }
                Map<String, Object> result = ttLockClient.getToken(
                        user.getTtUsername(), user.getTtPassword());
                if (result.containsKey("access_token")) {
                    user.setTtAccessToken((String) result.get("access_token"));
                    user.setTtRefreshToken((String) result.get("refresh_token"));
                    long expiresIn = ((Number) result.get("expires_in")).longValue() * 1000;
                    user.setTtTokenExpiresAt(System.currentTimeMillis() + expiresIn);
                    userRepository.save(user);
                }
            } catch (Exception e) {
                log.error("Failed to refresh TTLock token: {}", e.getMessage());
            }
        }
        return user.getTtAccessToken();
    }

    private String getPwdTypeName(int pwdType) {
        return switch (pwdType) {
            case 1 -> "\u5355\u6b21";
            case 2 -> "\u6c38\u4e45";
            case 3 -> "\u9650\u65f6";
            case 4 -> "\u5220\u9664";
            case 5 -> "\u5468\u672b";
            case 6 -> "\u6bcf\u5929";
            case 7 -> "\u5de5\u4f5c\u65e5";
            case 8, 9, 10, 11, 12, 13, 14 -> "\u6bcf\u5468" + getDayName(pwdType - 7);
            default -> "\u672a\u77e5";
        };
    }

    private String getDayName(int day) {
        return switch (day) {
            case 1 -> "\u4e00";
            case 2 -> "\u4e8c";
            case 3 -> "\u4e09";
            case 4 -> "\u56db";
            case 5 -> "\u4e94";
            case 6 -> "\u516d";
            case 7 -> "\u65e5";
            default -> "";
        };
    }
}
