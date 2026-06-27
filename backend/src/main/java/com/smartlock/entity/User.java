package com.smartlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(length = 100)
    private String nickname;

    @Column(length = 500)
    private String avatar;

    // 微信登录相关字段（与现有 username/password 并存，二者覆盖不同登录路径）
    @Column(length = 64, unique = true)
    private String openid;

    @Column(length = 64)
    private String unionid;

    @Column(length = 20)
    private String phone;

    /** "password"（账号密码注册）| "wechat"（微信一键登录静默注册） */
    @Column(name = "login_type", length = 20)
    private String loginType;

    // TTLock fields (internal, not exposed to API)
    @Column(name = "tt_username", length = 200)
    private String ttUsername;

    @Column(name = "tt_password", length = 100)
    private String ttPassword;

    @Column(name = "tt_uid")
    private Integer ttUid;

    @Column(name = "tt_access_token", length = 500)
    private String ttAccessToken;

    @Column(name = "tt_refresh_token", length = 500)
    private String ttRefreshToken;

    @Column(name = "tt_token_expires_at")
    private Long ttTokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
