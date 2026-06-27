package com.smartlock.repository;

import com.smartlock.entity.Fingerprint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FingerprintRepository extends JpaRepository<Fingerprint, Long> {
    Optional<Fingerprint> findByFingerprintId(Integer fingerprintId);
    List<Fingerprint> findByLockId(Long lockId);
    Page<Fingerprint> findByLockId(Long lockId, Pageable pageable);
    Optional<Fingerprint> findByLockIdAndFingerprintNumber(Long lockId, String fingerprintNumber);
}
