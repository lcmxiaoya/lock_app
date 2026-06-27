package com.smartlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "fingerprint")
public class Fingerprint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 本地 Lock.id */
    @Column(name = "lock_id", nullable = false)
    private Long lockId;

    /** TTLock 云端 fingerprintId */
    @Column(name = "fingerprint_id", nullable = false, unique = true)
    private Integer fingerprintId;

    @Column(name = "fingerprint_number", nullable = false, length = 50)
    private String fingerprintNumber;

    @Column(name = "fingerprint_name", length = 100)
    private String fingerprintName;

    /** 1=normal（限时/永久）, 4=cyclic（周期型） */
    @Column(name = "fingerprint_type", nullable = false)
    private Integer fingerprintType = 1;

    /** 周期型 JSON 数组：[{startTime,endTime,weekDay}]，分钟数 */
    @Column(name = "cyclic_config", length = 500)
    private String cyclicConfig;

    @Column(name = "start_date")
    private Long startDate = 0L;

    @Column(name = "end_date")
    private Long endDate = 0L;

    @Column(name = "status", length = 20)
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
