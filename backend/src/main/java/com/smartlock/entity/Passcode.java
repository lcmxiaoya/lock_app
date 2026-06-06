package com.smartlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "passcode")
public class Passcode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lock_id", nullable = false)
    private Long lockId;

    @Column(name = "keyboard_pwd_id")
    private Integer keyboardPwdId;

    @Column(name = "keyboard_pwd", length = 20)
    private String keyboardPwd;

    @Column(name = "keyboard_pwd_name", length = 100)
    private String keyboardPwdName;

    @Column(name = "pwd_type", nullable = false)
    private Integer pwdType;

    @Column(name = "is_custom")
    private Integer isCustom = 0;

    @Column(name = "start_date")
    private Long startDate = 0L;

    @Column(name = "end_date")
    private Long endDate = 0L;

    @Column(name = "status")
    private Integer status = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
