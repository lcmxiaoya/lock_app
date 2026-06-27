package com.smartlock.service;

import com.smartlock.dto.FingerprintAddRequest;
import com.smartlock.dto.PageResponse;
import com.smartlock.dto.ValidityModifyRequest;
import com.smartlock.entity.Fingerprint;
import com.smartlock.entity.Lock;
import com.smartlock.entity.User;
import com.smartlock.exception.BusinessException;
import com.smartlock.repository.FingerprintRepository;
import com.smartlock.repository.LockRepository;
import com.smartlock.util.TTLockClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指纹管理。锁拥有者 + 管理员（canManage）均可操作。
 *
 * <p>支持限时（type=1）/ 永久（type=1, startDate=endDate=0）/ 周期（type=4 + cyclicConfig）。</p>
 *
 * <p>管理员给指纹设置的有效期不能超过管理员自身 ekey 的 endDate，
 * 由 {@link LockPermissionService#assertChildValidityWithinMine} 校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FingerprintService {

    private final FingerprintRepository fingerprintRepository;
    private final LockRepository lockRepository;
    private final TTLockClient ttLockClient;
    private final UserService userService;
    private final LockPermissionService permission;

    public PageResponse<Map<String, Object>> getList(Long userId, Long lockId, int pageNo, int pageSize) {
        if (!permission.canManage(userId, lockId)) {
            throw new BusinessException(3003, "无查看指纹的权限");
        }
        PageRequest pr = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Fingerprint> page = fingerprintRepository.findByLockId(lockId, pr);
        List<Map<String, Object>> list = page.getContent().stream()
                .map(this::toMap).collect(Collectors.toList());
        return PageResponse.of(list, page.getTotalElements(), pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> recordAdd(Long userId, FingerprintAddRequest request) {
        if (!permission.canManage(userId, request.getLockId())) {
            throw new BusinessException(3003, "无添加指纹的权限");
        }
        permission.assertChildValidityWithinMine(userId, request.getLockId(),
                request.getStartDate(), request.getEndDate());

        Lock lock = lockRepository.findById(request.getLockId())
                .orElseThrow(() -> new BusinessException(3001, "锁不存在"));
        User user = userService.getUserById(userId);

        int type = request.getFingerprintType() != null ? request.getFingerprintType() : 1;
        String cyclicJson = (type == 4) ? request.getCyclicConfig() : null;

        Map<String, Object> result = ttLockClient.addFingerprint(
                user.getTtAccessToken(), lock.getLockId().intValue(),
                request.getFingerprintNumber(), type,
                request.getFingerprintName(),
                request.getStartDate(), request.getEndDate(), cyclicJson);

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "添加指纹失败：" + result.get("errmsg"));
        }
        if (!result.containsKey("fingerprintId")) {
            throw new BusinessException(6001, "添加指纹失败：响应缺少 fingerprintId");
        }
        Integer fingerprintId = ((Number) result.get("fingerprintId")).intValue();

        Fingerprint fp = new Fingerprint();
        fp.setUserId(userId);
        fp.setLockId(request.getLockId());
        fp.setFingerprintId(fingerprintId);
        fp.setFingerprintNumber(request.getFingerprintNumber());
        fp.setFingerprintName(request.getFingerprintName());
        fp.setFingerprintType(type);
        fp.setCyclicConfig(cyclicJson);
        fp.setStartDate(request.getStartDate() != null ? request.getStartDate() : 0L);
        fp.setEndDate(request.getEndDate() != null ? request.getEndDate() : 0L);
        fingerprintRepository.save(fp);

        Map<String, Object> resp = new HashMap<>();
        resp.put("recordId", fp.getId());
        resp.put("fingerprintId", fingerprintId);
        return resp;
    }

    @Transactional
    public void delete(Long userId, Long recordId) {
        Fingerprint fp = fingerprintRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(5201, "指纹记录未找到"));
        if (!permission.canManage(userId, fp.getLockId())) {
            throw new BusinessException(3003, "无删除指纹的权限");
        }
        Lock lock = lockRepository.findById(fp.getLockId())
                .orElseThrow(() -> new BusinessException(3001, "锁不存在"));
        User user = userService.getUserById(userId);

        Map<String, Object> result = ttLockClient.deleteFingerprint(
                user.getTtAccessToken(), lock.getLockId().intValue(), fp.getFingerprintId());

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            log.warn("TTLock fingerprint delete failed (may already be deleted): {}", result.get("errmsg"));
        }
        fingerprintRepository.delete(fp);
    }

    @Transactional
    public void modifyValidity(Long userId, ValidityModifyRequest request) {
        Fingerprint fp = fingerprintRepository.findById(request.getRecordId())
                .orElseThrow(() -> new BusinessException(5201, "指纹记录未找到"));
        if (!permission.canManage(userId, fp.getLockId())) {
            throw new BusinessException(3003, "无修改指纹的权限");
        }
        permission.assertChildValidityWithinMine(userId, fp.getLockId(),
                request.getStartDate(), request.getEndDate());

        Lock lock = lockRepository.findById(fp.getLockId())
                .orElseThrow(() -> new BusinessException(3001, "锁不存在"));
        User user = userService.getUserById(userId);

        // 周期型可同时改 cyclicConfig；非周期型沿用原 type
        String cyclicJson = (fp.getFingerprintType() == 4)
                ? (request.getCyclicConfig() != null ? request.getCyclicConfig() : fp.getCyclicConfig())
                : null;

        Map<String, Object> result = ttLockClient.addFingerprint(
                user.getTtAccessToken(), lock.getLockId().intValue(),
                fp.getFingerprintNumber(), fp.getFingerprintType(),
                fp.getFingerprintName(),
                request.getStartDate(), request.getEndDate(), cyclicJson);

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "修改有效期失败：" + result.get("errmsg"));
        }

        fp.setStartDate(request.getStartDate() != null ? request.getStartDate() : 0L);
        fp.setEndDate(request.getEndDate() != null ? request.getEndDate() : 0L);
        if (cyclicJson != null) fp.setCyclicConfig(cyclicJson);
        fingerprintRepository.save(fp);
    }

    private Map<String, Object> toMap(Fingerprint fp) {
        Map<String, Object> map = new HashMap<>();
        map.put("recordId", fp.getId());
        map.put("fingerprintId", fp.getFingerprintId());
        map.put("lockId", fp.getLockId());
        map.put("fingerprintNumber", fp.getFingerprintNumber());
        map.put("fingerprintName", fp.getFingerprintName());
        map.put("fingerprintType", fp.getFingerprintType());
        map.put("cyclicConfig", fp.getCyclicConfig());
        map.put("startDate", fp.getStartDate());
        map.put("endDate", fp.getEndDate());
        map.put("status", fp.getStatus());
        map.put("createdAt", fp.getCreatedAt());
        return map;
    }
}
