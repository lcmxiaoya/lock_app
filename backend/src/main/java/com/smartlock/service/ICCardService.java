package com.smartlock.service;

import com.smartlock.dto.ICCardAddRequest;
import com.smartlock.dto.PageResponse;
import com.smartlock.dto.ValidityModifyRequest;
import com.smartlock.entity.ICCard;
import com.smartlock.entity.Lock;
import com.smartlock.entity.User;
import com.smartlock.exception.BusinessException;
import com.smartlock.repository.ICCardRepository;
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
 * IC 卡管理。锁拥有者 + 管理员（canManage）均可操作。
 *
 * <p>"添加"流程：小程序蓝牙调插件 addICCard 等用户在锁上刷卡，拿到 cardNumber 后调
 * /api/icCard/add；后端调云端 add 拿到 cardId 落本地表。</p>
 *
 * <p>管理员给自己发出去的 IC 卡设置的有效期不能超过管理员自身 ekey 的 endDate，
 * 由 {@link LockPermissionService#assertChildValidityWithinMine} 校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ICCardService {

    private final ICCardRepository icCardRepository;
    private final LockRepository lockRepository;
    private final TTLockClient ttLockClient;
    private final UserService userService;
    private final LockPermissionService permission;

    public PageResponse<Map<String, Object>> getList(Long userId, Long lockId, int pageNo, int pageSize) {
        if (!permission.canManage(userId, lockId)) {
            throw new BusinessException(3003, "无查看 IC 卡的权限");
        }
        PageRequest pr = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ICCard> page = icCardRepository.findByLockId(lockId, pr);
        List<Map<String, Object>> list = page.getContent().stream()
                .map(this::toMap).collect(Collectors.toList());
        return PageResponse.of(list, page.getTotalElements(), pageNo, pageSize);
    }

    @Transactional
    public Map<String, Object> recordAdd(Long userId, ICCardAddRequest request) {
        if (!permission.canManage(userId, request.getLockId())) {
            throw new BusinessException(3003, "无添加 IC 卡的权限");
        }
        // admin 操作时校验有效期不超出自身 ekey
        permission.assertChildValidityWithinMine(userId, request.getLockId(),
                request.getStartDate(), request.getEndDate());

        Lock lock = lockRepository.findById(request.getLockId())
                .orElseThrow(() -> new BusinessException(3001, "锁不存在"));
        User user = userService.getUserById(userId);

        Map<String, Object> result = ttLockClient.addICCard(
                user.getTtAccessToken(), lock.getLockId().intValue(),
                request.getCardNumber(), request.getCardName(),
                request.getStartDate(), request.getEndDate());

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "添加 IC 卡失败：" + result.get("errmsg"));
        }
        if (!result.containsKey("cardId")) {
            throw new BusinessException(6001, "添加 IC 卡失败：响应缺少 cardId");
        }
        Integer cardId = ((Number) result.get("cardId")).intValue();

        ICCard card = new ICCard();
        card.setUserId(userId);
        card.setLockId(request.getLockId());
        card.setCardId(cardId);
        card.setCardNumber(request.getCardNumber());
        card.setCardName(request.getCardName());
        card.setStartDate(request.getStartDate() != null ? request.getStartDate() : 0L);
        card.setEndDate(request.getEndDate() != null ? request.getEndDate() : 0L);
        icCardRepository.save(card);

        Map<String, Object> resp = new HashMap<>();
        resp.put("recordId", card.getId());
        resp.put("cardId", cardId);
        return resp;
    }

    @Transactional
    public void delete(Long userId, Long recordId) {
        ICCard card = icCardRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(5101, "IC 卡记录未找到"));
        if (!permission.canManage(userId, card.getLockId())) {
            throw new BusinessException(3003, "无删除 IC 卡的权限");
        }
        Lock lock = lockRepository.findById(card.getLockId())
                .orElseThrow(() -> new BusinessException(3001, "锁不存在"));
        User user = userService.getUserById(userId);

        Map<String, Object> result = ttLockClient.deleteICCard(
                user.getTtAccessToken(), lock.getLockId().intValue(), card.getCardId());

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            // 云端删除失败仅记录 warn，本地继续删除（与 PasswordService.deletePassword 一致）
            log.warn("TTLock IC card delete failed (may already be deleted): {}", result.get("errmsg"));
        }
        icCardRepository.delete(card);
    }

    @Transactional
    public void modifyValidity(Long userId, ValidityModifyRequest request) {
        ICCard card = icCardRepository.findById(request.getRecordId())
                .orElseThrow(() -> new BusinessException(5101, "IC 卡记录未找到"));
        if (!permission.canManage(userId, card.getLockId())) {
            throw new BusinessException(3003, "无修改 IC 卡的权限");
        }
        // admin 修改自己发出的卡的有效期时同样受自身 ekey 约束
        permission.assertChildValidityWithinMine(userId, card.getLockId(),
                request.getStartDate(), request.getEndDate());

        Lock lock = lockRepository.findById(card.getLockId())
                .orElseThrow(() -> new BusinessException(3001, "锁不存在"));
        User user = userService.getUserById(userId);

        // 蓝牙端已经先调 modifyICCardValidityPeriod 改了锁内值；
        // 云端没有单独 modify 接口，重新 add 同 cardNumber 即"覆盖"
        Map<String, Object> result = ttLockClient.addICCard(
                user.getTtAccessToken(), lock.getLockId().intValue(),
                card.getCardNumber(), card.getCardName(),
                request.getStartDate(), request.getEndDate());

        if (result.containsKey("errcode") && ((Number) result.get("errcode")).intValue() != 0) {
            throw new BusinessException(6001, "修改有效期失败：" + result.get("errmsg"));
        }

        card.setStartDate(request.getStartDate() != null ? request.getStartDate() : 0L);
        card.setEndDate(request.getEndDate() != null ? request.getEndDate() : 0L);
        icCardRepository.save(card);
    }

    private Map<String, Object> toMap(ICCard card) {
        Map<String, Object> map = new HashMap<>();
        map.put("recordId", card.getId());
        map.put("cardId", card.getCardId());
        map.put("lockId", card.getLockId());
        map.put("cardNumber", card.getCardNumber());
        map.put("cardName", card.getCardName());
        map.put("startDate", card.getStartDate());
        map.put("endDate", card.getEndDate());
        map.put("status", card.getStatus());
        map.put("createdAt", card.getCreatedAt());
        return map;
    }
}
