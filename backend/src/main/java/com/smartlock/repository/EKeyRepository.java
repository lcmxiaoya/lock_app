package com.smartlock.repository;

import com.smartlock.entity.EKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EKeyRepository extends JpaRepository<EKey, Long> {
    Optional<EKey> findByKeyId(Integer keyId);
    List<EKey> findByUserId(Long userId);
    Page<EKey> findByUserId(Long userId, Pageable pageable);
    List<EKey> findByLockId(Long lockId);
    Page<EKey> findByLockId(Long lockId, Pageable pageable);
    List<EKey> findByUserIdAndLockId(Long userId, Long lockId);
    Optional<EKey> findByUserIdAndLockIdAndKeyTypeAndStatus(Long userId, Long lockId, String keyType, String status);
    
    @Query("SELECT e FROM EKey e WHERE e.lockId = :lockId AND e.userId IN " +
           "(SELECT l.userId FROM Lock l WHERE l.id = :lockId)")
    List<EKey> findKeysByLockId(@Param("lockId") Long lockId);
}
