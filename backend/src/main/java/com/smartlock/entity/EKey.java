package com.smartlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ekey")
public class EKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lock_id", nullable = false)
    private Long lockId;

    @Column(name = "key_id", nullable = false, unique = true)
    private Integer keyId;

    @Column(name = "lock_data", columnDefinition = "TEXT")
    private String lockData;

    @Column(name = "key_name", length = 100)
    private String keyName;

    @Column(name = "key_type", nullable = false, length = 20)
    private String keyType;

    @Column(name = "start_date")
    private Long startDate = 0L;

    @Column(name = "end_date")
    private Long endDate = 0L;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "remote_enable")
    private Integer remoteEnable = 2;

    @Column(name = "status", length = 20)
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
