package com.smartlock.repository;

import com.smartlock.entity.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LockRepository extends JpaRepository<Lock, Long> {
    Optional<Lock> findByLockId(Long lockId);
    List<Lock> findByUserId(Long userId);
    
    @Query("SELECT l FROM Lock l WHERE l.id IN " +
           "(SELECT e.lockId FROM EKey e WHERE e.userId = :userId AND e.keyType = 'common') " +
           "OR l.userId = :userId")
    List<Lock> findByUserIdOrSharedWith(@Param("userId") Long userId);

    @Query(value = "SELECT l FROM Lock l WHERE l.id IN " +
           "(SELECT e.lockId FROM EKey e WHERE e.userId = :userId AND e.keyType = 'common') " +
           "OR l.userId = :userId",
           countQuery = "SELECT COUNT(l) FROM Lock l WHERE l.id IN " +
           "(SELECT e.lockId FROM EKey e WHERE e.userId = :userId AND e.keyType = 'common') " +
           "OR l.userId = :userId")
    Page<Lock> findByUserIdOrSharedWith(@Param("userId") Long userId, Pageable pageable);
}
