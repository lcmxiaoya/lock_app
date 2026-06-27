package com.smartlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ic_card")
public class ICCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 本地 Lock.id（不是 TTLock 的 lockId） */
    @Column(name = "lock_id", nullable = false)
    private Long lockId;

    /** TTLock 云端 cardId */
    @Column(name = "card_id", nullable = false, unique = true)
    private Integer cardId;

    @Column(name = "card_number", nullable = false, length = 50)
    private String cardNumber;

    @Column(name = "card_name", length = 100)
    private String cardName;

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
