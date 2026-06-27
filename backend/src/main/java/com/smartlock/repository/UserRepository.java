package com.smartlock.repository;

import com.smartlock.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByOpenid(String openid);
    Optional<User> findByPhone(String phone);
    boolean existsByUsername(String username);
}
