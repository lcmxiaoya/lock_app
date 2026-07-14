package com.smartlock.service;

import com.smartlock.dto.LoginRequest;
import com.smartlock.dto.LoginResponse;
import com.smartlock.dto.RegisterRequest;
import com.smartlock.dto.WxLoginRequest;
import com.smartlock.entity.User;
import com.smartlock.exception.BusinessException;
import com.smartlock.repository.UserRepository;
import com.smartlock.util.JwtUtil;
import com.smartlock.util.TTLockClient;
import com.smartlock.util.WxClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TTLockClient ttLockClient;
    private final WxClient wxClient;

    @Value("${sms.code}")
    private String fixedCode;

    @Value("${ttlock.env-prefix:}")
    private String envPrefix;

    @Value("${app.show-debug-credentials:false}")
    private boolean showDebugCredentials;

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
        user.setLoginType("password");
        // 如果 username 是手机号格式，顺便填进 phone 字段，便于后续微信一键登录合并
        if (request.getUsername().matches("^1[3-9]\\d{9}$")) {
            user.setPhone(request.getUsername());
        }

        // Save user first to get ID
        userRepository.save(user);

        bootstrapTTLockUser(user);

        // Generate JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        return new LoginResponse(token, toUserInfo(user));
    }

    /**
     * 在本地 user 已落库（拿到 id）后，给该 user 生成 TTLock 用户名/密码、
     * 调 TTLock 注册接口、获取 access_token，并把全部 tt_* 字段保存。
     *
     * <p>失败时抛 BusinessException 让外层事务回滚，避免落下没有 TTLock 账户的孤儿用户。</p>
     */
    private void bootstrapTTLockUser(User user) {
        long idSuffix = user.getId() % 10000;
        String ttPassword = "2026#" + idSuffix;
        user.setTtPassword(ttPassword);
        user.setTtUsername( user.getUsername());

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
        String actualTtUsername = (String) registerResult.get("username");
        if (actualTtUsername == null || actualTtUsername.isEmpty()) {
            actualTtUsername = user.getTtUsername();
        }
        user.setTtUsername(actualTtUsername);
        log.info("TTLock registration success: username={}", actualTtUsername);

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
    }

    /**
     * 微信一键登录主入口。
     *
     * <p>逻辑：
     * <ol>
     *   <li>用前端传来的 code 调 jscode2session，拿 openid + unionid</li>
     *   <li>用前端传来的 phoneCode 调 phonenumber.getPhoneNumber，拿 phone</li>
     *   <li>按 phone → username 两级查找现有用户：
     *     <ul>
     *       <li>命中：补绑缺失的 openid/unionid/phone 字段</li>
     *       <li>未命中：silentRegister 静默建号（含 TTLock 注册）</li>
     *     </ul>
     *   </li>
     *   <li>颁发 JWT 返回</li>
     * </ol>
     *
     * <p><b>业务模型：手机号 = 身份（不是 openid）</b>。
     * 一个微信号可以绑多个手机号，每个手机号都是一个独立的通通锁账号（独立 ttUsername）。
     * 所以查找路径不能以 openid 为主键，否则同微信多号码会撞到同一 user。
     * openid 只作为"该手机号曾用哪个微信登录过"的记录字段，可以被多个 user 共享（已移除 unique 约束）。</p>
     *
     * <p>不需要校验短信验证码 —— 微信侧的 wx.login 与 getPhoneNumber 已经分别用 code 凭证保证身份，
     * 手机号是用户微信账号实名绑定的，比 SMS 自填更可信。</p>
     */
    @Transactional
    public LoginResponse wxLogin(WxLoginRequest req) {
        WxClient.WxSession session = wxClient.jscode2session(req.getCode());
        String phone = wxClient.getPhoneNumber(req.getPhoneCode());

        // 两级查找：phone → username（老用户 username 是手机号但 phone 字段还没回填）
        // 注意：不能用 openid 查找，因为一个微信可绑多个号码，每个号码是独立账号。
        User user = userRepository.findByPhone(phone)
                .or(() -> userRepository.findByUsername(phone))
                .orElse(null);

        if (user == null) {
            user = silentRegister(phone, session.getOpenid(), session.getUnionid());
        } else {
            boolean dirty = false;
            if (user.getOpenid() == null) {
                user.setOpenid(session.getOpenid());
                dirty = true;
            }
            if (user.getUnionid() == null && session.getUnionid() != null) {
                user.setUnionid(session.getUnionid());
                dirty = true;
            }
            if (user.getPhone() == null) {
                user.setPhone(phone);
                dirty = true;
            }
            if (dirty) {
                userRepository.save(user);
                log.info("Wx login: bound openid/phone to existing user id={}", user.getId());
            }
            refreshTTLockTokenIfNeeded(user);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, toUserInfo(user));
    }

    /**
     * 静默建号：复用 bootstrapTTLockUser，跳过短信验证码与密码输入。
     * 必须在 @Transactional 内运行 —— TTLock 注册失败时整体回滚，避免落下孤儿 openid。
     */
    private User silentRegister(String phone, String openid, String unionid) {
        // 极端情况下并发同 phone 进入：靠 username unique 约束兜底，第二期再加冲突重试
        User user = new User();
        user.setUsername(phone);     // 与现有 register 一致：username = phone
        user.setPhone(phone);
        user.setOpenid(openid);
        user.setUnionid(unionid);
        user.setLoginType("wechat");
        user.setNickname("User");
        // 随机 64 字节密码 + bcrypt，让账号密码登录路径打不进来
        String randomPwd = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        user.setPassword(passwordEncoder.encode(randomPwd));
        userRepository.save(user);

        bootstrapTTLockUser(user);
        log.info("Wx silent register: id={}, phone={}", user.getId(), phone);
        return user;
    }

    private LoginResponse.UserInfo toUserInfo(User user) {
        return new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar()
        );
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

        return new LoginResponse(token, toUserInfo(user));
    }

    /**
     * Get user info
     */
    public LoginResponse.UserInfo getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(1004, "User not found"));

        return toUserInfo(user);
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
     * 用于"发送钥匙 / 授权管理员"场景：若目标手机号/邮箱账号不存在，
     * 自动创建本地账号 + 同步的通通锁账号，并把 ekey.user_id 直接挂到新账号。
     *
     * <p>关键点：bootstrapTTLockUser 需要 user.id（用于生成 ttPassword），
     * 而 ttUsername 又要带 envPrefix 与 username 一致。所以必须先 save() 拿到 id，
     * 再注册 TTLock；最后在同一个事务里把 access_token 一并写回。</p>
     */
    @Transactional
    public User autoProvisionReceiver(String receiverUsername) {
        User existing = findByUsername(receiverUsername);
        if (existing != null) {
            return existing;
        }

        if (userRepository.existsByUsername(receiverUsername)) {
            // 并发兜底：两条 sendKey 几乎同时跑，第二条 unique 约束先于 findByUsername 命中。
            return findByUsername(receiverUsername);
        }

        log.info("Auto-provisioning receiver account: {}", receiverUsername);

        User user = new User();
        user.setUsername(receiverUsername);
        user.setNickname("User");
        user.setLoginType("password");
        // 与 register 行为保持一致：如果是手机号格式，phone 字段也填上，
        // 之后该用户首次微信一键登录时能直接命中。
        if (receiverUsername.matches("^1[3-9]\\d{9}$")) {
            user.setPhone(receiverUsername);
        }
        // 锁主不会去登录这个被自动创建的账号，密码用 64 字节随机串 + bcrypt 兜住。
        String randomPwd = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        user.setPassword(passwordEncoder.encode(randomPwd));
        userRepository.save(user);

        bootstrapTTLockUser(user);
        log.info("Auto-provisioned receiver: id={}, username={}, ttUsername={}",
                user.getId(), user.getUsername(), user.getTtUsername());
        return user;
    }

    /**
     * Refresh TTLock token if needed
     */
    /**
     * 返回当前账号的 TTLock 内部凭据。仅在配置 {@code app.show-debug-credentials=true} 时可用。
     * 生产环境必须保持关闭。
     */
    public Map<String, Object> getDebugTTLockCredentials(Long userId) {
        if (!showDebugCredentials) {
            throw new BusinessException(4031, "调试模式未开启");
        }
        User user = getUserById(userId);
        Map<String, Object> map = new HashMap<>();
        map.put("ttUsername", user.getTtUsername());
        map.put("ttPassword", user.getTtPassword());
        map.put("ttUid", user.getTtUid());
        return map;
    }

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
