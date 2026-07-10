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
    private final LockPermissionService permission;

    /**
     * Send key to another user
     */
    @Transactional
    public Map<String, Object> sendKey(Long userId, KeySendRequest request) {
        User sender = userService.getUserById(userId);

        // owner 和 admin 都可向下发普通钥匙
        Lock lock = lockRepository.findById(request.getLockId()).orElse(null);
        if (lock == null || !permission.canManage(userId, request.getLockId())) {
            throw new BusinessException(3003, "You don't have permission to send keys for this lock");
        }

        // Use the TTLock lockId for API calls
        Long ttLockId = lock.getLockId();

        // 接收者：本地账号存在则直接发；不存在则自动创建本地 + 通通锁账号，再发钥匙。
        // 这样锁主把钥匙发给未注册的手机号也能直接成功，对方首次打开 App 时就能看到这把钥匙。
        User receiver = userService.findByUsername(request.getReceiverUsername());
        if (receiver == null) {
            receiver = userService.autoProvisionReceiver(request.getReceiverUsername());
        }
        String ttReceiverUsername = receiver.getTtUsername();
        // 本地账号刚自动创建时，TTLock 云端尚无此用户，仍需 createUser=1 让 sendKey 帮忙建号。
        // 已存在账号置 0，避免覆盖云端已有属性。
        int createUser = 0;
        if (ttReceiverUsername == null || ttReceiverUsername.isEmpty()) {
            ttReceiverUsername = request.getReceiverUsername();
            createUser = 1;
        }
        Long targetUserId = receiver.getId();

        // Ensure only one active common key per user per lock
        // Invalidate existing active key before proceeding (TTLock also enforces this)
        eKeyRepository.findByUserIdAndLockIdAndKeyTypeAndStatus(targetUserId, request.getLockId(), "common", "active")
                .ifPresent(existingKey -> {
                    existingKey.setStatus("invalid");
                    eKeyRepository.save(existingKey);
                });

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
        eKey.setUserId(targetUserId); // 接收者已通过 autoProvisionReceiver 在本地落库，可直接绑定 userId
        eKey.setLockId(request.getLockId());
        eKey.setKeyId(keyId);
        eKey.setKeyName(request.getKeyName());
        eKey.setKeyType("common");
        eKey.setUserType("110302");   // TTLock 官方值：普通钥匙
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
     * 授权管理员：先调 sendKey 发普通钥匙拿 keyId，再调 /v3/key/authorize 升级为 admin。
     * 任一步失败由事务回滚（authorize 失败时主动调 deleteKey 把云端 ekey 也清掉）。
     * 仅锁拥有者可操作。
     */
    @Transactional
    public Map<String, Object> sendAdminKey(Long userId, KeySendRequest request) {
        User sender = userService.getUserById(userId);
        Lock lock = lockRepository.findById(request.getLockId())
                .orElseThrow(() -> new BusinessException(3001, "锁不存在"));

        // 显式拦截：sendKey 已放宽到 canManage，授权管理员必须额外校验 owner，
        // 否则 admin 也能调本接口继续向下授权。
        if (!permission.isOwner(userId, request.getLockId())) {
            throw new BusinessException(3003, "只有锁拥有者可以授权管理员");
        }

        // 接收者不能是自己（TTLock 也禁止 admin 给自己发钥匙）
        String recv = request.getReceiverUsername();
        if (recv != null
                && (recv.equals(sender.getUsername())
                    || (sender.getPhone() != null && recv.equals(sender.getPhone())))) {
            throw new BusinessException(4002, "不能授权给自己");
        }

        // Step 1: 复用 sendKey 主流程（同事务内，本地落库 + TTLock send）
        Map<String, Object> sendResp = sendKey(userId, request);
        Integer keyId = (Integer) sendResp.get("keyId");

        // Step 2: 升级管理员
        Map<String, Object> authResp = ttLockClient.authorizeKey(
                sender.getTtAccessToken(), lock.getLockId().intValue(), keyId);
        if (authResp.containsKey("errcode") && ((Number) authResp.get("errcode")).intValue() != 0) {
            // 已下发到云端的 ekey 主动清掉，避免云本地不一致；本地事务自动回滚
            try {
                ttLockClient.deleteKey(sender.getTtAccessToken(), keyId);
            } catch (Exception e) {
                log.warn("Rollback deleteKey failed after authorize error: {}", e.getMessage());
            }
            throw new BusinessException(6002, "授权管理员失败：" + authResp.get("errmsg"));
        }

        // Step 3: 升级成功后改本地 ekey 标记为 admin
        EKey eKey = eKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> new BusinessException(4001, "钥匙未找到"));
        eKey.setKeyType("admin");
        eKey.setUserType("110301");   // TTLock 官方值：管理员钥匙
        eKeyRepository.save(eKey);

        Map<String, Object> response = new HashMap<>();
        response.put("keyId", keyId);
        return response;
    }

    /**
     * 获取该锁下所有"管理员"钥匙（key_type='admin'），仅锁拥有者可查。
     */
    public List<Map<String, Object>> getAdminKeyList(Long userId, Long lockId) {
        if (!permission.isOwner(userId, lockId)) {
            throw new BusinessException(3003, "No permission to view admin keys for this lock");
        }
        return eKeyRepository.findByLockId(lockId).stream()
                .filter(eKey -> "admin".equals(eKey.getKeyType()))
                .map(this::toKeyMap)
                .collect(Collectors.toList());
    }

    /**
     * Get key list (non-paginated, for backward compatibility)
     */
    public List<Map<String, Object>> getKeyList(Long userId, Long lockId) {
        if (lockId != null) {
            if (!permission.canManage(userId, lockId)) {
                throw new BusinessException(3003, "No permission to view keys for this lock");
            }
            return eKeyRepository.findByLockId(lockId).stream()
                    .filter(eKey -> isCommonKey(eKey.getKeyType()))
                    .map(this::toKeyMap)
                    .collect(Collectors.toList());
        }
        return eKeyRepository.findByUserId(userId).stream()
                .filter(eKey -> isCommonKey(eKey.getKeyType()))
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
            if (!permission.canManage(userId, lockId)) {
                throw new BusinessException(3003, "No permission to view keys for this lock");
            }
            page = eKeyRepository.findByLockId(lockId, pageRequest);
        } else {
            page = eKeyRepository.findByUserId(userId, pageRequest);
        }

        List<Map<String, Object>> list = page.getContent().stream()
                .filter(eKey -> isCommonKey(eKey.getKeyType()))
                .map(this::toKeyMap)
                .collect(Collectors.toList());

        return PageResponse.of(list, page.getTotalElements(), pageNo, pageSize);
    }

    /** "钥匙管理"页只展示普通钥匙；owner 和 admin 在"授权管理员"页单独展示。 */
    private static boolean isCommonKey(String keyType) {
        return !"owner".equals(keyType) && !"admin".equals(keyType);
    }

    private Map<String, Object> toKeyMap(EKey eKey) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", eKey.getId());
        map.put("keyId", eKey.getKeyId());
        map.put("lockId", eKey.getLockId());
        map.put("lockData", eKey.getLockData());
        map.put("keyName", eKey.getKeyName());
        map.put("keyType", eKey.getKeyType());
        map.put("userType", eKey.getUserType());
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
     *
     * <p>三态权限：
     * <ul>
     *   <li>钥匙持有者：可删自己的（自我退出）</li>
     *   <li>锁拥有者：可删任何钥匙</li>
     *   <li>锁管理员：仅可删 common 钥匙；不能踢掉其他 admin（防止协管互相剔除）</li>
     * </ul>
     */
    @Transactional
    public void deleteKey(Long userId, Long keyRecordId) {
        EKey eKey = eKeyRepository.findById(keyRecordId)
                .orElseThrow(() -> new BusinessException(4001, "Key not found"));

        boolean isHolder = eKey.getUserId().equals(userId);
        boolean isOwner = permission.isOwner(userId, eKey.getLockId());
        boolean isManager = permission.canManage(userId, eKey.getLockId());
        boolean targetIsAdmin = "admin".equals(eKey.getKeyType());

        boolean ok = isHolder
                  || isOwner
                  || (isManager && !targetIsAdmin);
        if (!ok) {
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
