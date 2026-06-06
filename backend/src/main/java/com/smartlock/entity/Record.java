package com.smartlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "record")
public class Record {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lock_id", nullable = false)
    private Long lockId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "record_type")
    private Integer recordType;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "record_time")
    private Long recordTime;

    @Column(name = "keyboard_pwd", length = 50)
    private String keyboardPwd;

    @Column(name = "password_name", length = 200)
    private String passwordName;

    @Column(name = "fail_reason", length = 100)
    private String failReason;

    @Column(name = "uid")
    private Long uid;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
