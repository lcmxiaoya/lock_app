package com.smartlock.repository;

import com.smartlock.entity.Record;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {
    Page<Record> findByLockIdOrderByRecordTimeDesc(Long lockId, Pageable pageable);
    Page<Record> findByUserIdOrderByRecordTimeDesc(Long userId, Pageable pageable);
    List<Record> findByLockIdAndRecordTimeBetween(Long lockId, Long startTime, Long endTime);
    Page<Record> findByLockIdAndUserIdOrderByRecordTimeDesc(Long lockId, Long userId, Pageable pageable);
    List<Record> findByLockIdAndRecordTypeAndRecordTime(Long lockId, Integer recordType, Long recordTime);
    java.util.Optional<Record> findByLockIdAndActionAndRecordTime(Long lockId, String action, Long recordTime);
    Page<Record> findByLockIdAndKeyboardPwdOrderByRecordTimeDesc(Long lockId, String keyboardPwd, Pageable pageable);
    Page<Record> findByLockIdAndUidOrderByRecordTimeDesc(Long lockId, Long uid, Pageable pageable);
}
