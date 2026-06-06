package com.smartlock.service;

import com.smartlock.dto.KeySendRequest;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyService {

    private final EKeyRepository eKeyRepository;
    private final LockRepository lockRepository;
    private final TTLockClient ttLockClient;
    private final UserService userService;
    private final LockService lockService;

    /**
     * Send key to another user
     */
    @Transactional
    public Map<String, Object> sendKey(Long userId, KeySendRequest request) {
        User sender = userService.getUserById(userId);
        
        // Check if sender has permission: must be the lock owner
        Lock lock = lockRepository.findById(request.getLockId()).orElse(null);
        if (lock == null || !lock.getUserId().equals(userId)) {
            throw new BusinessException(3003, "You don't have permission to send keys for this lock");
        }

        // Use the TTLock lockId for API calls
        Long ttLockId = lock.getLockId();

        // Check if receiver exists
        User receiver = userService.findByUsername(request.getReceiverUsername());
        String ttReceiverUsername;
        int createUser = 0;

        Long targetUserId = receiver != null ? receiver.getId() : userId;

        // Ensure only one active common key per user per lock
        // Invalidate existing active key before proceeding (TTLock also enforces this)
        eKeyRepository.findByUserIdAndLockIdAndKeyTypeAndStatus(targetUserId, request.getLockId(), "common", "active")
                .ifPresent(existingKey -> {
                    existingKey.setStatus("invalid");
                    eKeyRepository.save(existingKey);
                });

        if (receiver != null) {
            ttReceiverUsername = receiver.getTtUsername();
        } else {
            // Use phone/email as TTLock username
            ttReceiverUsername = request.getReceiverUsername();
            createUser = 1;
        }

        // Call TTLock API
        Map<String, Object> result = ttLockClient.sendKey(
                sender.getTtAccessToken(),
                ttLockId.intValue(),
                ttReceiverUsername,
                request.getKeyName(),
                request.getStartDate(),
                request.getEndDate(),
                request.getRemarks(),
                request.getRemoteEnable(),
                createUser);

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "Failed to send key: " + result.get("errmsg"));
        }

        Integer keyId = ((Number) result.get("keyId")).intValue();

        // Save key record
        EKey eKey = new EKey();
        eKey.setUserId(targetUserId); // Will be updated when receiver registers
        eKey.setLockId(request.getLockId());
        eKey.setKeyId(keyId);
        eKey.setKeyName(request.getKeyName());
        eKey.setKeyType("common");
        eKey.setStartDate(request.getStartDate());
        eKey.setEndDate(request.getEndDate());
        eKey.setRemarks(request.getRemarks());
        eKey.setRemoteEnable(request.getRemoteEnable());
        eKeyRepository.save(eKey);

        Map<String, Object> response = new HashMap<>();
        response.put("keyId", keyId);
        
        return response;
    }

    /**
     * Get key list (non-paginated, for backward compatibility)
     */
    public List<Map<String, Object>> getKeyList(Long userId, Long lockId) {
        if (lockId != null) {
            Lock lock = lockRepository.findById(lockId).orElse(null);
            if (lock == null || !lock.getUserId().equals(userId)) {
                throw new BusinessException(3003, "No permission to view keys for this lock");
            }
            return eKeyRepository.findByLockId(lockId).stream()
                    .filter(eKey -> !"owner".equals(eKey.getKeyType()))
                    .map(this::toKeyMap)
                    .collect(Collectors.toList());
        }
        return eKeyRepository.findByUserId(userId).stream()
                .filter(eKey -> !"owner".equals(eKey.getKeyType()))
                .map(this::toKeyMap)
                .collect(Collectors.toList());
    }

    /**
     * Get key list with pagination
     */
    public PageResponse<Map<String, Object>> getKeyList(Long userId, Long lockId, int pageNo, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<EKey> page;

        if (lockId != null) {
            Lock lock = lockRepository.findById(lockId).orElse(null);
            if (lock == null || !lock.getUserId().equals(userId)) {
                throw new BusinessException(3003, "No permission to view keys for this lock");
            }
            page = eKeyRepository.findByLockId(lockId, pageRequest);
        } else {
            page = eKeyRepository.findByUserId(userId, pageRequest);
        }

        List<Map<String, Object>> list = page.getContent().stream()
                .filter(eKey -> !"owner".equals(eKey.getKeyType()))
                .map(this::toKeyMap)
                .collect(Collectors.toList());

        return PageResponse.of(list, page.getTotalElements(), pageNo, pageSize);
    }

    private Map<String, Object> toKeyMap(EKey eKey) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", eKey.getId());
        map.put("keyId", eKey.getKeyId());
        map.put("lockId", eKey.getLockId());
        map.put("lockData", eKey.getLockData());
        map.put("keyName", eKey.getKeyName());
        map.put("keyType", eKey.getKeyType());
        map.put("startDate", eKey.getStartDate());
        map.put("endDate", eKey.getEndDate());
        map.put("remarks", eKey.getRemarks());
        map.put("remoteEnable", eKey.getRemoteEnable());
        map.put("status", eKey.getStatus());
        map.put("createdAt", eKey.getCreatedAt());
        map.put("userId", eKey.getUserId());

        User receiver = userService.getUserById(eKey.getUserId());
        map.put("receiverUsername", receiver != null ? receiver.getUsername() : "");

        Lock lock = lockRepository.findById(eKey.getLockId()).orElse(null);
        if (lock != null) {
            map.put("lockName", lock.getLockName());
            map.put("lockAlias", lock.getLockAlias());
            map.put("lockMac", lock.getLockMac());
            map.put("electricQuantity", lock.getElectricQuantity());
            User sender = userService.getUserById(lock.getUserId());
            map.put("senderUsername", sender != null ? sender.getUsername() : "");
        } else {
            map.put("senderUsername", "");
        }

        return map;
    }

    /**
     * Get key detail with real lockData from TTLock cloud
     */
    public Map<String, Object> getKeyDetail(Long userId, Long keyRecordId) {
        EKey eKey = eKeyRepository.findById(keyRecordId)
                .orElseThrow(() -> new BusinessException(4001, "Key not found"));

        // Check permission
        if (!eKey.getUserId().equals(userId)) {
            throw new BusinessException(4003, "No permission to access this key");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("id", eKey.getId());
        map.put("keyId", eKey.getKeyId());
        map.put("lockId", eKey.getLockId());
        map.put("keyName", eKey.getKeyName());
        map.put("keyType", eKey.getKeyType());
        map.put("startDate", eKey.getStartDate());
        map.put("endDate", eKey.getEndDate());
        map.put("remarks", eKey.getRemarks());
        map.put("remoteEnable", eKey.getRemoteEnable());
        map.put("createdAt", eKey.getCreatedAt());

        // Get receiver username
        try {
            User receiver = userService.getUserById(eKey.getUserId());
            map.put("receiverUsername", receiver != null ? receiver.getUsername() : "");
        } catch (Exception e) {
            map.put("receiverUsername", "");
        }

        // Check expiration status
        long now = System.currentTimeMillis();
        if (eKey.getEndDate() != 0 && eKey.getEndDate() < now) {
            map.put("status", "expired");
        } else {
            map.put("status", eKey.getStatus());
        }

        // Get lock info (eKey.lockId is the local Lock.id)
        Lock lock = lockRepository.findById(eKey.getLockId()).orElse(null);
        if (lock != null) {
            map.put("lockName", lock.getLockName());
            map.put("lockAlias", lock.getLockAlias());
            map.put("lockMac", lock.getLockMac());
            map.put("electricQuantity", lock.getElectricQuantity());

            // Get sender (lock owner) username
            try {
                User sender = userService.getUserById(lock.getUserId());
                map.put("senderUsername", sender != null ? sender.getUsername() : "");
            } catch (Exception e) {
                map.put("senderUsername", "");
            }

            // Fetch real lockData from TTLock for this key
            User user = userService.getUserById(userId);
            try {
                Map<String, Object> ttResult = ttLockClient.getKeyDetail(
                        user.getTtAccessToken(),
                        lock.getLockId().intValue());
                if (ttResult.containsKey("lockData")) {
                    map.put("lockData", ttResult.get("lockData"));
                } else {
                    map.put("lockData", eKey.getLockData());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch key lockData from TTLock, using local: {}", e.getMessage());
                map.put("lockData", eKey.getLockData());
            }
        } else {
            map.put("lockData", eKey.getLockData());
        }

        return map;
    }

    /**
     * Delete key
     */
    @Transactional
    public void deleteKey(Long userId, Long keyRecordId) {
        EKey eKey = eKeyRepository.findById(keyRecordId)
                .orElseThrow(() -> new BusinessException(4001, "Key not found"));

        // Check permission: key holder or lock owner can delete
        boolean isKeyHolder = eKey.getUserId().equals(userId);
        boolean isLockOwner = false;
        Lock lock = lockRepository.findById(eKey.getLockId()).orElse(null);
        if (lock != null && lock.getUserId().equals(userId)) {
            isLockOwner = true;
        }
        if (!isKeyHolder && !isLockOwner) {
            throw new BusinessException(4003, "No permission to delete this key");
        }

        User user = userService.getUserById(userId);
        
        // Call TTLock API
        Map<String, Object> result = ttLockClient.deleteKey(
                user.getTtAccessToken(), 
                eKey.getKeyId());
        
        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            log.warn("TTLock key delete failed: {}", result.get("errmsg"));
            // Continue with local delete even if TTLock fails
        }

        // Delete local record
        eKeyRepository.delete(eKey);
    }
}
