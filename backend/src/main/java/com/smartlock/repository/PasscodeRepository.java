package com.smartlock.repository;

import com.smartlock.entity.Passcode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasscodeRepository extends JpaRepository<Passcode, Long> {
    Optional<Passcode> findByKeyboardPwdId(Integer keyboardPwdId);
    List<Passcode> findByLockId(Long lockId);
    Page<Passcode> findByLockId(Long lockId, Pageable pageable);
    List<Passcode> findByUserIdAndLockId(Long userId, Long lockId);
    Optional<Passcode> findByLockIdAndKeyboardPwd(Long lockId, String keyboardPwd);
    void deleteByLockId(Long lockId);
}
