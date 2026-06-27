package com.smartlock.service;

import com.smartlock.entity.EKey;
import com.smartlock.exception.BusinessException;
import com.smartlock.repository.EKeyRepository;
import com.smartlock.repository.LockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 单一职责：判定某个用户对某把锁的角色与权限。
 *
 * <p>三种角色：
 * <ul>
 *   <li>owner — 锁初始化的人（{@code Lock.userId == userId}）</li>
 *   <li>admin — 该锁有 active 的 admin ekey（{@code key_type='admin'}），由 owner 通过授权管理员产生</li>
 *   <li>common — 该锁有 active 的 common ekey；只能开锁，不能管理</li>
 * </ul>
 *
 * <p>调用方只通过本类的 {@link #isOwner}、{@link #canManage}、{@link #canAccess}、
 * {@link #roleOf} 四个方法判定权限，不要再裸写 {@code lock.getUserId().equals(userId)}。
 */
@Service
@RequiredArgsConstructor
public class LockPermissionService {

    private final LockRepository lockRepository;
    private final EKeyRepository eKeyRepository;

    /** 仅锁拥有者：解绑整把锁、授权管理员等 owner 特权使用 */
    public boolean isOwner(Long userId, Long localLockId) {
        return lockRepository.findById(localLockId)
                .map(l -> l.getUserId().equals(userId))
                .orElse(false);
    }

    /** owner 或本锁的 active admin ekey 持有者：发普通钥匙、改密码、同步 lockData 等"协管"动作 */
    public boolean canManage(Long userId, Long localLockId) {
        if (isOwner(userId, localLockId)) return true;
        return eKeyRepository.findByUserIdAndLockId(userId, localLockId).stream()
                .anyMatch(k -> "admin".equals(k.getKeyType())
                            && "active".equals(k.getStatus()));
    }

    /** owner 或本锁任意有效 ekey 持有者（admin / common 都算）：看锁详情、获取 lockData 开锁 */
    public boolean canAccess(Long userId, Long localLockId) {
        if (isOwner(userId, localLockId)) return true;
        return eKeyRepository.findByUserIdAndLockId(userId, localLockId).stream()
                .anyMatch(k -> "active".equals(k.getStatus()));
    }

    /** 给前端用的角色标记：owner / admin / common（admin 优先于 common） */
    public String roleOf(Long userId, Long localLockId) {
        if (isOwner(userId, localLockId)) return "owner";
        boolean hasAdminKey = false;
        boolean hasCommonKey = false;
        for (EKey k : eKeyRepository.findByUserIdAndLockId(userId, localLockId)) {
            if (!"active".equals(k.getStatus())) continue;
            if ("admin".equals(k.getKeyType())) {
                hasAdminKey = true;
            } else if ("common".equals(k.getKeyType())) {
                hasCommonKey = true;
            }
        }
        if (hasAdminKey) return "admin";
        if (hasCommonKey) return "common";
        return "common";
    }

    /**
     * 返回当前用户对该锁"最强"的 ekey（owner 优先于 admin 优先于 common）。
     * 用于子资源（IC 卡/指纹/密码）有效期校验：管理员只能在自己有权限的
     * 时间窗内给其他人/物发权限。
     *
     * <p>owner 不会被这个方法返回（其 ekey 不参与 startDate/endDate 校验，
     * 走 canManage 的宽松策略即可）。</p>
     */
    public Optional<EKey> strongestActiveKey(Long userId, Long localLockId) {
        return eKeyRepository.findByUserIdAndLockId(userId, localLockId).stream()
                .filter(k -> "active".equals(k.getStatus()))
                .min(java.util.Comparator.comparingInt(k ->
                        "admin".equals(k.getKeyType()) ? 0 :
                        "common".equals(k.getKeyType()) ? 1 : 2));
    }

    /**
     * 子资源（IC 卡/指纹/密码等）的有效期必须落在调用方 ekey 的有效区间内：
     * <ul>
     *   <li>owner 不校验（时间无限制）</li>
     *   <li>admin：request.startDate >= admin.startDate（若 admin 有起始时间），
     *       request.endDate <= admin.endDate（若 admin 有截止时间）；
     *       admin endDate=0 表示永久，则子资源也允许永久</li>
     * </ul>
     *
     * @return 校验通过返回 emptyOptional；不通过抛出 BusinessException
     */
    public void assertChildValidityWithinMine(Long userId, Long localLockId,
                                              Long requestStart, Long requestEnd) {
        if (isOwner(userId, localLockId)) {
            return; // 拥有者无限制
        }
        Optional<EKey> mine = strongestActiveKey(userId, localLockId);
        if (mine.isEmpty()) {
            throw new BusinessException(3003, "无该锁的钥匙");
        }
        EKey myKey = mine.get();
        long myStart = myKey.getStartDate() == null ? 0L : myKey.getStartDate();
        long myEnd = myKey.getEndDate() == null ? 0L : myKey.getEndDate();

        long reqStart = requestStart == null ? 0L : requestStart;
        long reqEnd = requestEnd == null ? 0L : requestEnd;

        // 永久（0）允许，但前提是 admin 自己也是永久
        if (reqEnd == 0 && myEnd != 0) {
            throw new BusinessException(3003, "您的钥匙有截止时间，不能设置永久有效");
        }

        if (myStart != 0 && reqStart != 0 && reqStart < myStart) {
            throw new BusinessException(3003, "生效时间不能早于您自身的生效时间");
        }
        if (myEnd != 0) {
            if (reqEnd == 0 || reqEnd > myEnd) {
                throw new BusinessException(3003,
                        "失效时间不能晚于您自身的失效时间（" + formatTs(myEnd) + "）");
            }
        }
    }

    private static String formatTs(long ts) {
        if (ts <= 0) return "永久";
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return java.time.LocalDateTime
                .ofInstant(java.time.Instant.ofEpochMilli(ts),
                        java.time.ZoneId.of("Asia/Shanghai"))
                .format(fmt);
    }
}
