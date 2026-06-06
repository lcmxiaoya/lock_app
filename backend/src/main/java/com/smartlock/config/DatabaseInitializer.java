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
    }

    private void dropPartialUniqueIndex() {
        try {
            jdbcTemplate.execute("DROP INDEX idx_ekey_active_user_lock ON ekey");
            log.info("Dropped partial unique index idx_ekey_active_user_lock");
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
}
