package com.smartlock.repository;

import com.smartlock.entity.ICCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ICCardRepository extends JpaRepository<ICCard, Long> {
    Optional<ICCard> findByCardId(Integer cardId);
    List<ICCard> findByLockId(Long lockId);
    Page<ICCard> findByLockId(Long lockId, Pageable pageable);
    Optional<ICCard> findByLockIdAndCardNumber(Long lockId, String cardNumber);
}
