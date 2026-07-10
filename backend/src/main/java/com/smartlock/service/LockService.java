package com.smartlock.service;

import com.smartlock.dto.LockAddRequest;
import com.smartlock.dto.PageResponse;
import com.smartlock.entity.EKey;
import com.smartlock.entity.Lock;
import com.smartlock.entity.User;
import com.smartlock.exception.BusinessException;
import com.smartlock.repository.EKeyRepository;
import com.smartlock.repository.LockRepository;
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
public class LockService {

    private final LockRepository lockRepository;
    private final EKeyRepository eKeyRepository;
    private final TTLockClient ttLockClient;
    private final UserService userService;
    private final LockPermissionService permission;

    /**
     * Add lock
     */
    @Transactional
    public Map<String, Object> addLock(Long userId, LockAddRequest request) {
        User user = userService.getUserById(userId);

        // Pre-check: 同一把物理锁不能被绑定两次。直接走 lockMac，比依赖云端 errcode 更友好，
        // 也避免初始化 lockData 已下发但本地报错的尴尬情况。
        if (request.getLockMac() != null && !request.getLockMac().isEmpty()) {
            lockRepository.findByLockMac(request.getLockMac()).ifPresent(existing -> {
                throw new BusinessException(3002, "该智能锁已被绑定，请先在原账号中删除或重置门锁");
            });
        }

        // Call TTLock API to initialize lock
        Map<String, Object> result = ttLockClient.initializeLock(
                user.getTtAccessToken(),
                request.getLockData(),
                request.getLockAlias());

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            int errcode = ((Number) result.get("errcode")).intValue();
            String errmsg = String.valueOf(result.get("errmsg"));
            // -3007 = lock already initialized by another account（云端兜底）
            if (errcode == -3007) {
                throw new BusinessException(3002, "该智能锁已被绑定，请先在原账号中删除或重置门锁");
            }
            throw new BusinessException(6001, "添加锁失败: " + errmsg);
        }

        Long lockId = ((Number) result.get("lockId")).longValue();
        Integer keyId = ((Number) result.get("keyId")).intValue();

        // Save lock record
        Lock lock = new Lock();
        lock.setUserId(userId);
        lock.setLockId(lockId);
        lock.setLockName(request.getLockName());
        lock.setLockAlias(request.getLockAlias());
        lock.setLockMac(request.getLockMac());
        lock.setLockData(request.getLockData());
        lock.setKeyId(keyId);
        lockRepository.save(lock);

        // Save admin ekey record
        EKey adminKey = new EKey();
        adminKey.setUserId(userId);
        adminKey.setLockId(lock.getId());
        adminKey.setKeyId(keyId);
        adminKey.setLockData(request.getLockData());
        adminKey.setKeyName("Admin Key");
        adminKey.setKeyType("owner");
        adminKey.setStartDate(0L);
        adminKey.setEndDate(0L);
        eKeyRepository.save(adminKey);

        Map<String, Object> response = new HashMap<>();
        response.put("lockId", lockId);
        response.put("keyId", keyId);
        response.put("lockRecordId", lock.getId());
        
        return response;
    }

    /**
     * Get lock list (owned + shared)
     */
    public List<Map<String, Object>> getLockList(Long userId) {
        List<Lock> locks = lockRepository.findByUserIdOrSharedWith(userId);
        return locks.stream().map(lock -> toLockMap(lock, userId)).collect(Collectors.toList());
    }

    /**
     * Get lock list with pagination
     */
    public PageResponse<Map<String, Object>> getLockList(Long userId, int pageNo, int pageSize) {
        Page<Lock> page = lockRepository.findByUserIdOrSharedWith(userId, PageRequest.of(pageNo - 1, pageSize));
        List<Map<String, Object>> list = page.getContent().stream()
                .map(lock -> toLockMap(lock, userId)).collect(Collectors.toList());
        return PageResponse.of(list, page.getTotalElements(), pageNo, pageSize);
    }

    private Map<String, Object> toLockMap(Lock lock, Long userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", lock.getId());
        map.put("lockId", lock.getLockId());
        map.put("lockName", lock.getLockName());
        map.put("lockAlias", lock.getLockAlias());
        map.put("lockMac", lock.getLockMac());
        map.put("lockData", lock.getLockData());
        map.put("electricQuantity", lock.getElectricQuantity());
        map.put("hasGateway", 0);
        map.put("keyType", permission.roleOf(userId, lock.getId()));
        return map;
    }

    /**
     * Get lock detail
     */
    public Map<String, Object> getLockDetail(Long userId, Long lockRecordId) {
        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        if (!permission.canAccess(userId, lockRecordId)) {
            throw new BusinessException(3003, "No permission to access this lock");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("id", lock.getId());
        map.put("lockId", lock.getLockId());
        map.put("lockName", lock.getLockName());
        map.put("lockAlias", lock.getLockAlias());
        map.put("lockMac", lock.getLockMac());
        map.put("lockData", lock.getLockData());
        map.put("keyId", lock.getKeyId());
        map.put("electricQuantity", lock.getElectricQuantity());
        map.put("keyboardPwdVersion", lock.getKeyboardPwdVersion());
        map.put("specialValue", lock.getSpecialValue());
        map.put("lockVersion", lock.getLockVersion());
        map.put("keyType", permission.roleOf(userId, lockRecordId));
        map.put("hasGateway", 0);
        map.put("groupId", lock.getGroupId());
        map.put("createdAt", lock.getCreatedAt());
        map.put("updatedAt", lock.getUpdatedAt());

        // 当前用户对这把锁的 ekey（0=永久），供小程序"基本信息"页展示。
        // 一个用户对同一把锁可能存在多把 ekey（admin + common），按 keyType 优先级取第一把。
        eKeyRepository.findByUserIdAndLockId(userId, lockRecordId).stream()
                .filter(k -> "active".equals(k.getStatus()))
                .min(Comparator.comparingInt(k ->
                        "owner".equals(k.getKeyType()) ? 0 :
                        "admin".equals(k.getKeyType()) ? 1 : 2))
                .ifPresent(k -> {
                    map.put("ekeyStartDate", k.getStartDate());
                    map.put("ekeyEndDate", k.getEndDate());
                    // 本地表主键，供小程序"退出管理"时直接传 keyApi.delete
                    map.put("ekeyRecordId", k.getId());
                    map.put("ekeyType", k.getKeyType());
                });

        return map;
    }

    /**
     * Delete lock
     */
    @Transactional
    public void deleteLock(Long userId, Long lockRecordId, String confirmText) {
        log.info("Delete lock request: userId={}, lockRecordId={}", userId, lockRecordId);

        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> {
                    log.warn("Lock not found: lockRecordId={}", lockRecordId);
                    return new BusinessException(3001, "Lock not found");
                });

        // Check permission (only owner can delete the entire lock)
        if (!permission.isOwner(userId, lockRecordId)) {
            log.warn("Permission denied: userId={} is not owner of lock={}", userId, lockRecordId);
            throw new BusinessException(3003, "Only lock owner can delete the lock");
        }

        // Confirm by lock identifier instead of account password so WeChat-phone-login users can delete too.
        String expectedConfirmText = firstNonBlank(lock.getLockAlias(), lock.getLockName(), lock.getLockMac(), String.valueOf(lock.getLockId()));
        if (confirmText == null || !confirmText.trim().equals(expectedConfirmText)) {
            log.warn("Lock delete confirmation failed: userId={}, lockRecordId={}", userId, lockRecordId);
            throw new BusinessException(3003, "锁编号确认不正确");
        }

        User user = userService.getUserById(userId);

        // Step 1: Delete lock from TTLock cloud
        try {
            Map<String, Object> result = ttLockClient.deleteLock(
                    user.getTtAccessToken(), 
                    lock.getLockId().intValue());
            
            if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
                log.warn("TTLock cloud delete failed: {}", result.get("errmsg"));
            } else {
                log.info("TTLock cloud delete success: lockId={}", lock.getLockId());
            }
        } catch (Exception e) {
            log.error("TTLock cloud delete error: {}", e.getMessage());
        }

        // Step 2: Delete local records
        List<EKey> keys = eKeyRepository.findByLockId(lockRecordId);
        log.info("Deleting {} ekeys for lock={}", keys.size(), lockRecordId);
        eKeyRepository.deleteAll(keys);

        // Step 3: Delete lock record
        lockRepository.delete(lock);
        log.info("Lock deleted successfully: lockRecordId={}, lockId={}", lockRecordId, lock.getLockId());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Get unlock data with fresh lockData from TTLock cloud.
     * Works for both owner and common key holder.
     * Updates local lock/ekey lockData after successful fetch.
     */
    @Transactional
    public Map<String, Object> getUnlockData(Long userId, Long lockRecordId) {
        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        if (!permission.canAccess(userId, lockRecordId)) {
            throw new BusinessException(3003, "No permission to access this lock");
        }
        boolean isOwner = permission.isOwner(userId, lockRecordId);

        User user = userService.getUserById(userId);

        Map<String, Object> result = ttLockClient.getKeyDetail(
                user.getTtAccessToken(),
                lock.getLockId().intValue());

        String freshLockData = null;
        if (!result.containsKey("errcode") && result.get("lockData") != null) {
            freshLockData = (String) result.get("lockData");
        }

        if (freshLockData == null) {
            freshLockData = lock.getLockData();
        } else {
            if (isOwner) {
                lock.setLockData(freshLockData);
                lockRepository.save(lock);
            } else {
                List<EKey> keys = eKeyRepository.findByUserIdAndLockId(userId, lockRecordId);
                for (EKey key : keys) {
                    key.setLockData(freshLockData);
                    eKeyRepository.save(key);
                }
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("lockData", freshLockData);
        map.put("lockId", lock.getLockId());
        map.put("lockMac", lock.getLockMac());
        map.put("electricQuantity", lock.getElectricQuantity());
        map.put("keyType", permission.roleOf(userId, lockRecordId));

        return map;
    }

    /**
     * Update lock data
     */
    @Transactional
    public void updateLockData(Long userId, Long lockRecordId, String newLockData) {
        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        // owner 与 admin 都可同步 lockData（重置键盘密码后必须能写回云端）
        if (!permission.canManage(userId, lockRecordId)) {
            throw new BusinessException(3003, "No permission to update this lock");
        }

        User user = userService.getUserById(userId);

        // Call TTLock API
        Map<String, Object> result = ttLockClient.updateLockData(
                user.getTtAccessToken(),
                lock.getLockId().intValue(),
                newLockData);

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "Failed to update lock data: " + result.get("errmsg"));
        }

        // Update local data
        lock.setLockData(newLockData);
        lockRepository.save(lock);
    }

    /**
     * 重命名锁：同步更新本地 lockName/lockAlias 与 TTLock 云端 alias。
     * owner 与 admin 都可以改（canManage）。
     */
    @Transactional
    public void updateLockName(Long userId, Long lockRecordId, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new BusinessException(3004, "锁名称不能为空");
        }
        String trimmed = newName.trim();
        if (trimmed.length() > 100) {
            throw new BusinessException(3004, "锁名称不能超过 100 字符");
        }

        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        if (!permission.canManage(userId, lockRecordId)) {
            throw new BusinessException(3003, "No permission to rename this lock");
        }

        User user = userService.getUserById(userId);

        // 同步到 TTLock 云端
        Map<String, Object> result = ttLockClient.renameLock(
                user.getTtAccessToken(), lock.getLockId().intValue(), trimmed);
        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "重命名失败: " + result.get("errmsg"));
        }

        // 本地同步：lockName 与 lockAlias 保持一致（与 addLock 时的策略对齐）
        lock.setLockName(trimmed);
        lock.setLockAlias(trimmed);
        lockRepository.save(lock);
    }

    /**
     * Get lock by TTLock ID
     */
    public Lock findByLockId(Long lockId) {
        return lockRepository.findByLockId(lockId).orElse(null);
    }
}
