package com.smartlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "lock")
public class Lock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lock_id", nullable = false, unique = true)
    private Long lockId;

    @Column(name = "lock_name", length = 100)
    private String lockName;

    @Column(name = "lock_alias", length = 100)
    private String lockAlias;

    @Column(name = "lock_mac", length = 50)
    private String lockMac;

    @Column(name = "lock_data", columnDefinition = "TEXT")
    private String lockData;

    @Column(name = "key_id")
    private Integer keyId;

    @Column(name = "electric_quantity")
    private Integer electricQuantity = 0;

    @Column(name = "keyboard_pwd_version")
    private Integer keyboardPwdVersion = 4;

    @Column(name = "special_value")
    private Integer specialValue = 0;

    @Column(name = "lock_version", columnDefinition = "TEXT")
    private String lockVersion;

    @Column(name = "group_id")
    private Integer groupId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
