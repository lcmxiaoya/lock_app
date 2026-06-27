package com.smartlock.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        dropPartialUniqueIndex();
        cleanupDuplicateKeys();
        backfillPhoneAndLoginType();
        backfillEkeyUserType();
    }

    private void dropPartialUniqueIndex() {
        try {
            String db = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = ? AND table_name = 'ekey' AND index_name = 'idx_ekey_active_user_lock'",
                Integer.class, db);
            if (count != null && count > 0) {
                jdbcTemplate.execute("DROP INDEX idx_ekey_active_user_lock ON ekey");
                log.info("Dropped partial unique index idx_ekey_active_user_lock");
            } else {
                log.info("Index idx_ekey_active_user_lock does not exist, skipping");
            }
        } catch (Exception e) {
            log.warn("Failed to drop idx_ekey_active_user_lock (may not exist)", e);
        }
    }

    private void cleanupDuplicateKeys() {
        try {
            int cleaned = jdbcTemplate.update(
                "UPDATE ekey e " +
                "SET status = 'invalid' " +
                "WHERE e.key_type = 'common' " +
                "  AND e.status = 'active' " +
                "  AND e.id NOT IN (" +
                "    SELECT tmp.id FROM (" +
                "      SELECT MAX(e2.id) AS id FROM ekey e2 " +
                "      WHERE e2.key_type = 'common' " +
                "        AND e2.status = 'active' " +
                "      GROUP BY e2.user_id, e2.lock_id" +
                "    ) tmp" +
                "  )"
            );
            if (cleaned > 0) {
                log.warn("Cleaned up {} duplicate active common keys (kept only the latest per user+lock)", cleaned);
            } else {
                log.info("No duplicate active common keys found");
            }
        } catch (Exception e) {
            log.error("Failed to clean up duplicate keys", e);
        }
    }

    /**
     * 历史用户回填 phone / login_type，给微信一键登录的"按 phone 兜底合并"路径准备数据。
     * 幂等：phone 已有值的不动；login_type 已有值的不动。
     * MySQL REGEXP，PostgreSQL 不支持；当前主流程是 MySQL（pom.xml）。
     */
    private void backfillPhoneAndLoginType() {
        try {
            int phoneFilled = jdbcTemplate.update(
                "UPDATE `user` SET phone = username " +
                "WHERE phone IS NULL AND username REGEXP '^1[3-9][0-9]{9}$'"
            );
            int typeFilled = jdbcTemplate.update(
                "UPDATE `user` SET login_type = 'password' WHERE login_type IS NULL"
            );
            log.info("User backfill: phone={}, loginType={}", phoneFilled, typeFilled);
        } catch (Exception e) {
            log.warn("User backfill skipped: {}", e.getMessage());
        }
    }

    /**
     * 历史 ekey 回填 user_type（TTLock 官方角色值）。
     * owner/admin → 110301，common → 110302。幂等。
     */
    private void backfillEkeyUserType() {
        try {
            int ownerFilled = jdbcTemplate.update(
                "UPDATE ekey SET user_type = '110301' " +
                "WHERE user_type IS NULL AND key_type IN ('owner', 'admin')"
            );
            int commonFilled = jdbcTemplate.update(
                "UPDATE ekey SET user_type = '110302' " +
                "WHERE user_type IS NULL AND key_type = 'common'"
            );
            log.info("EKey backfill: admin={}, common={}", ownerFilled, commonFilled);
        } catch (Exception e) {
            log.warn("EKey backfill skipped: {}", e.getMessage());
        }
    }
}
