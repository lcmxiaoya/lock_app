package com.smartlock.service;

import com.smartlock.dto.LoginRequest;
import com.smartlock.dto.LoginResponse;
import com.smartlock.dto.RegisterRequest;
import com.smartlock.entity.User;
import com.smartlock.exception.BusinessException;
import com.smartlock.repository.UserRepository;
import com.smartlock.util.JwtUtil;
import com.smartlock.util.TTLockClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TTLockClient ttLockClient;

    @Value("${sms.code}")
    private String fixedCode;

    @Value("${ttlock.env-prefix:}")
    private String envPrefix;

    /**
     * Send verification code (fixed 666666)
     */
    public void sendCode(String username) {
        // In production, send actual SMS/email
        // For now, just log
        log.info("Verification code sent to: {}", username);
    }

    /**
     * Register new user
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // Check if username exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(2001, "Username already exists");
        }

        // Verify code
        if (!fixedCode.equals(request.getCode())) {
            throw new BusinessException(2002, "Invalid verification code");
        }

        // Create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname("User");
        
        // Save user first to get ID
        userRepository.save(user);

        // TTLock password = "2026#" + userId (max 4 digits)
        long idSuffix = user.getId() % 10000;
        String ttPassword = "2026#" + idSuffix;
        user.setTtPassword(ttPassword);
        
        // TTLock username = envPrefix + local system login account
        user.setTtUsername(envPrefix + user.getUsername());

        // Register TTLock user
        Map<String, Object> registerResult;
        try {
            registerResult = ttLockClient.registerUser(user.getTtUsername(), ttPassword);
        } catch (Exception e) {
            log.error("TTLock registration error: {}", e.getMessage(), e);
            throw new BusinessException(3001, "TTLock registration failed: " + e.getMessage());
        }
        if (registerResult.containsKey("errcode") && ((Number) registerResult.get("errcode")).intValue() != 0) {
            log.error("TTLock registration failed: {}", registerResult);
            throw new BusinessException(3001, "TTLock registration failed: " + registerResult.get("errmsg"));
        }
        // TTLock returns the actual username (may have prefix added)
        String actualTtUsername = (String) registerResult.get("username");
        if (actualTtUsername == null || actualTtUsername.isEmpty()) {
            actualTtUsername = user.getTtUsername();
        }
        user.setTtUsername(actualTtUsername);
        log.info("TTLock registration success: username={}", actualTtUsername);

        // Get TTLock token (use actual username returned by TTLock)
        Map<String, Object> tokenResult;
        try {
            tokenResult = ttLockClient.getToken(actualTtUsername, ttPassword);
        } catch (Exception e) {
            log.error("TTLock token error: {}", e.getMessage(), e);
            throw new BusinessException(3002, "TTLock token acquisition failed: " + e.getMessage());
        }
        if (!tokenResult.containsKey("access_token")) {
            log.error("TTLock token failed: {}", tokenResult);
            throw new BusinessException(3002, "TTLock token acquisition failed: " + tokenResult.get("errmsg"));
        }
        user.setTtAccessToken((String) tokenResult.get("access_token"));
        user.setTtRefreshToken((String) tokenResult.get("refresh_token"));
        user.setTtUid(((Number) tokenResult.get("uid")).intValue());
        long expiresIn = ((Number) tokenResult.get("expires_in")).longValue() * 1000;
        user.setTtTokenExpiresAt(System.currentTimeMillis() + expiresIn);
        log.info("TTLock token success: uid={}", user.getTtUid());

        userRepository.save(user);

        // Generate JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        return new LoginResponse(token, new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar()
        ));
    }

    /**
     * Login
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(2003, "Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(2003, "Invalid username or password");
        }

        // Check and refresh TTLock token if needed
        refreshTTLockTokenIfNeeded(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        return new LoginResponse(token, new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar()
        ));
    }

    /**
     * Get user info
     */
    public LoginResponse.UserInfo getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(1004, "User not found"));

        return new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar()
        );
    }

    /**
     * Get user by ID
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(1004, "User not found"));
    }

    /**
     * Find user by username
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * Refresh TTLock token if needed
     */
    private void refreshTTLockTokenIfNeeded(User user) {
        if (user.getTtTokenExpiresAt() == null || 
            System.currentTimeMillis() >= user.getTtTokenExpiresAt() - 24 * 60 * 60 * 1000L) {
            
            try {
                if (user.getTtRefreshToken() != null) {
                    Map<String, Object> result = ttLockClient.refreshToken(
                            user.getTtRefreshToken(), user.getTtUsername());
                    
                    if (result.containsKey("access_token")) {
                        user.setTtAccessToken((String) result.get("access_token"));
                        user.setTtRefreshToken((String) result.get("refresh_token"));
                        
                        long expiresIn = ((Number) result.get("expires_in")).longValue() * 1000;
                        user.setTtTokenExpiresAt(System.currentTimeMillis() + expiresIn);
                        
                        userRepository.save(user);
                        return;
                    }
                }
                
                // Fallback to password
                Map<String, Object> result = ttLockClient.getToken(
                        user.getTtUsername(), 
                        user.getTtPassword());
                
                if (result.containsKey("access_token")) {
                    user.setTtAccessToken((String) result.get("access_token"));
                    user.setTtRefreshToken((String) result.get("refresh_token"));
                    user.setTtUid(((Number) result.get("uid")).intValue());
                    
                    long expiresIn = ((Number) result.get("expires_in")).longValue() * 1000;
                    user.setTtTokenExpiresAt(System.currentTimeMillis() + expiresIn);
                    
                    userRepository.save(user);
                }
            } catch (Exception e) {
                log.error("Failed to refresh TTLock token: {}", e.getMessage());
            }
        }
    }
}
