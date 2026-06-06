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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    /**
     * Add lock
     */
    @Transactional
    public Map<String, Object> addLock(Long userId, LockAddRequest request) {
        User user = userService.getUserById(userId);
        
        // Call TTLock API to initialize lock
        Map<String, Object> result = ttLockClient.initializeLock(
                user.getTtAccessToken(), 
                request.getLockData(),
                request.getLockAlias());
        
        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "Failed to initialize lock: " + result.get("errmsg"));
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
        boolean isOwner = lock.getUserId().equals(userId);
        map.put("keyType", isOwner ? "owner" : "common");
        return map;
    }

    /**
     * Get lock detail
     */
    public Map<String, Object> getLockDetail(Long userId, Long lockRecordId) {
        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        // Check permission
        boolean isOwner = lock.getUserId().equals(userId);
        if (!isOwner) {
            boolean hasKey = eKeyRepository.findByUserIdAndLockId(userId, lockRecordId)
                    .stream()
                    .anyMatch(k -> "active".equals(k.getStatus()));
            if (!hasKey) {
                throw new BusinessException(3003, "No permission to access this lock");
            }
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
        map.put("keyType", isOwner ? "owner" : "common");
        map.put("hasGateway", 0);
        
        return map;
    }

    /**
     * Delete lock
     */
    @Transactional
    public void deleteLock(Long userId, Long lockRecordId, String password) {
        log.info("Delete lock request: userId={}, lockRecordId={}", userId, lockRecordId);

        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> {
                    log.warn("Lock not found: lockRecordId={}", lockRecordId);
                    return new BusinessException(3001, "Lock not found");
                });

        // Check permission (only owner can delete)
        if (!lock.getUserId().equals(userId)) {
            log.warn("Permission denied: userId={} is not owner of lock={}", userId, lockRecordId);
            throw new BusinessException(3003, "Only lock owner can delete the lock");
        }

        // Verify password
        User user = userService.getUserById(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Password verification failed: userId={}", userId);
            throw new BusinessException(3003, "Invalid password");
        }

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

    /**
     * Get unlock data with fresh lockData from TTLock cloud.
     * Works for both owner and common key holder.
     * Updates local lock/ekey lockData after successful fetch.
     */
    @Transactional
    public Map<String, Object> getUnlockData(Long userId, Long lockRecordId) {
        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        boolean isOwner = lock.getUserId().equals(userId);
        if (!isOwner) {
            boolean hasKey = eKeyRepository.findByUserIdAndLockId(userId, lockRecordId)
                    .stream()
                    .anyMatch(k -> "active".equals(k.getStatus()));
            if (!hasKey) {
                throw new BusinessException(3003, "No permission to access this lock");
            }
        }

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
        map.put("keyType", isOwner ? "owner" : "common");

        return map;
    }

    /**
     * Update lock data
     */
    @Transactional
    public void updateLockData(Long userId, Long lockRecordId, String newLockData) {
        Lock lock = lockRepository.findById(lockRecordId)
                .orElseThrow(() -> new BusinessException(3001, "Lock not found"));

        // Check permission
        if (!lock.getUserId().equals(userId)) {
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
     * Get lock by TTLock ID
     */
    public Lock findByLockId(Long lockId) {
        return lockRepository.findByLockId(lockId).orElse(null);
    }
}
