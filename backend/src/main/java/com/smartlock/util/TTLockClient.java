package com.smartlock.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import org.springframework.util.DigestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TTLockClient {

    @Value("${ttlock.client-id}")
    private String clientId;

    @Value("${ttlock.client-secret}")
    private String clientSecret;

    @Value("${ttlock.api-base-url}")
    private String apiBaseUrl;

    @Value("${ttlock.env-prefix:}")
    private String envPrefix;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate TTLock username from local username.
     * The local username is used directly; TTLock server adds its own prefix.
     */
    public String generateUsername(String localUsername) {
        return localUsername;
    }

    /**
     * Register a new TTLock user
     */
    public Map<String, Object> registerUser(String username, String password) {
        String url = apiBaseUrl + "/v3/user/register";
        
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        log.info("TTLock register username={}, password={}", username, md5Password);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("clientSecret", clientSecret);
        params.add("username", username);
        params.add("password", md5Password);
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Get OAuth2 token
     */
    public Map<String, Object> getToken(String username, String password) {
        String url = apiBaseUrl + "/oauth2/token";
        
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        log.info("TTLock getToken username={}, password={}", username, md5Password);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("username", username);
        params.add("password", md5Password);

        return doPost(url, params);
    }

    /**
     * Refresh OAuth2 token
     */
    public Map<String, Object> refreshToken(String refreshToken, String username) {
        String url = apiBaseUrl + "/oauth2/token";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", refreshToken);
        params.add("username", username);

        return doPost(url, params);
    }

    /**
     * Initialize lock
     */
    public Map<String, Object> initializeLock(String accessToken, String lockData, String lockAlias) {
        String url = apiBaseUrl + "/v3/lock/initialize";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockData", lockData);
        if (lockAlias != null) {
            params.add("lockAlias", lockAlias);
        }
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Get lock list
     */
    public Map<String, Object> getLockList(String accessToken, int pageNo, int pageSize) {
        String url = apiBaseUrl + "/v3/lock/list";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("pageNo", String.valueOf(pageNo));
        params.add("pageSize", String.valueOf(pageSize));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    /**
     * Get lock detail
     */
    public Map<String, Object> getLockDetail(String accessToken, int lockId) {
        String url = apiBaseUrl + "/v3/lock/detail";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    /**
     * Delete lock from TTLock cloud
     */
    public Map<String, Object> deleteLock(String accessToken, int lockId) {
        String url = apiBaseUrl + "/v3/lock/delete";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Update lock data
     */
    public Map<String, Object> updateLockData(String accessToken, int lockId, String lockData) {
        String url = apiBaseUrl + "/v3/lock/updateLockData";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("lockData", lockData);
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * 重命名锁（同步到 TTLock 云端 lockAlias）。
     * /v3/lock/rename
     */
    public Map<String, Object> renameLock(String accessToken, int lockId, String lockAlias) {
        String url = apiBaseUrl + "/v3/lock/rename";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("lockAlias", lockAlias);
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Send key
     */
    public Map<String, Object> sendKey(String accessToken, int lockId, String receiverUsername,
                                       String keyName, long startDate, long endDate,
                                       String remarks, Integer remoteEnable, Integer createUser) {
        String url = apiBaseUrl + "/v3/key/send";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("receiverUsername", receiverUsername);
        params.add("keyName", keyName);
        params.add("startDate", String.valueOf(startDate));
        params.add("endDate", String.valueOf(endDate));
        if (remarks != null) {
            params.add("remarks", remarks);
        }
        if (remoteEnable != null) {
            params.add("remoteEnable", String.valueOf(remoteEnable));
        }
        if (createUser != null) {
            params.add("createUser", String.valueOf(createUser));
        }
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Delete key
     */
    public Map<String, Object> deleteKey(String accessToken, int keyId) {
        String url = apiBaseUrl + "/v3/key/delete";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("keyId", String.valueOf(keyId));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Authorize an existing common ekey to admin (grant admin rights).
     * 对应 /v3/key/authorize：把已发出的普通钥匙升级为管理员钥匙（userType=110301）。
     */
    public Map<String, Object> authorizeKey(String accessToken, int lockId, int keyId) {
        String url = apiBaseUrl + "/v3/key/authorize";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("keyId", String.valueOf(keyId));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    // =================== IC 卡 ===================

    /**
     * 添加 IC 卡（云端）。蓝牙端 addICCard 拿到 cardNumber 后调用此接口。
     * /v3/identityCard/addForReversedCardNumber
     */
    public Map<String, Object> addICCard(String accessToken, int lockId, String cardNumber,
                                         String cardName, Long startDate, Long endDate) {
        String url = apiBaseUrl + "/v3/identityCard/addForReversedCardNumber";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("cardNumber", cardNumber);
        if (cardName != null) params.add("cardName", cardName);
        if (startDate != null) params.add("startDate", String.valueOf(startDate));
        if (endDate != null) params.add("endDate", String.valueOf(endDate));
        params.add("addType", "1");
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /** /v3/identityCard/delete */
    public Map<String, Object> deleteICCard(String accessToken, int lockId, int cardId) {
        String url = apiBaseUrl + "/v3/identityCard/delete";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("cardId", String.valueOf(cardId));
        params.add("deleteType", "1");
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /** /v3/identityCard/list */
    public Map<String, Object> getICCardList(String accessToken, int lockId, int pageNo, int pageSize) {
        String url = apiBaseUrl + "/v3/identityCard/list";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("pageNo", String.valueOf(pageNo));
        params.add("pageSize", String.valueOf(pageSize));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    // =================== 指纹 ===================

    /**
     * /v3/fingerprint/add
     * @param fingerprintType 1=normal, 4=cyclic
     * @param cyclicConfigJson 周期型 JSON 数组字符串；非周期型传 null
     */
    public Map<String, Object> addFingerprint(String accessToken, int lockId, String fingerprintNumber,
                                              int fingerprintType, String fingerprintName,
                                              Long startDate, Long endDate, String cyclicConfigJson) {
        String url = apiBaseUrl + "/v3/fingerprint/add";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("fingerprintNumber", fingerprintNumber);
        params.add("fingerprintType", String.valueOf(fingerprintType));
        if (fingerprintName != null) params.add("fingerprintName", fingerprintName);
        if (startDate != null) params.add("startDate", String.valueOf(startDate));
        if (endDate != null) params.add("endDate", String.valueOf(endDate));
        if (cyclicConfigJson != null && fingerprintType == 4) {
            params.add("cyclicConfig", cyclicConfigJson);
        }
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /** /v3/fingerprint/delete */
    public Map<String, Object> deleteFingerprint(String accessToken, int lockId, int fingerprintId) {
        String url = apiBaseUrl + "/v3/fingerprint/delete";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("fingerprintId", String.valueOf(fingerprintId));
        params.add("deleteType", "1");
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /** /v3/fingerprint/list */
    public Map<String, Object> getFingerprintList(String accessToken, int lockId, int pageNo, int pageSize) {
        String url = apiBaseUrl + "/v3/fingerprint/list";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("pageNo", String.valueOf(pageNo));
        params.add("pageSize", String.valueOf(pageSize));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    /**
     * Get key list
     */
    public Map<String, Object> getKeyList(String accessToken, int pageNo, int pageSize) {
        String url = apiBaseUrl + "/v3/key/list";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("pageNo", String.valueOf(pageNo));
        params.add("pageSize", String.valueOf(pageSize));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    /**
     * Get key detail
     */
    public Map<String, Object> getKeyDetail(String accessToken, int lockId) {
        String url = apiBaseUrl + "/v3/key/get";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    /**
     * Get random password
     */
    public Map<String, Object> getRandomPassword(String accessToken, int lockId, int keyboardPwdVersion,
                                                 int keyboardPwdType, String keyboardPwdName,
                                                 Long startDate, Long endDate) {
        String url = apiBaseUrl + "/v3/keyboardPwd/get";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("keyboardPwdVersion", String.valueOf(keyboardPwdVersion));
        params.add("keyboardPwdType", String.valueOf(keyboardPwdType));
        if (keyboardPwdName != null) {
            params.add("keyboardPwdName", keyboardPwdName);
        }
        if (startDate != null) {
            params.add("startDate", String.valueOf(startDate));
        }
        if (endDate != null) {
            params.add("endDate", String.valueOf(endDate));
        }
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    /**
     * Add custom password
     */
    public Map<String, Object> addCustomPassword(String accessToken, int lockId, String keyboardPwd,
                                                  String keyboardPwdName, long startDate, long endDate) {
        String url = apiBaseUrl + "/v3/keyboardPwd/add";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("keyboardPwd", keyboardPwd);
        if (keyboardPwdName != null) {
            params.add("keyboardPwdName", keyboardPwdName);
        }
        params.add("startDate", String.valueOf(startDate));
        params.add("endDate", String.valueOf(endDate));
        params.add("addType", "1"); // Bluetooth
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Delete password
     */
    public Map<String, Object> deletePassword(String accessToken, int lockId, int keyboardPwdId) {
        String url = apiBaseUrl + "/v3/keyboardPwd/delete";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("keyboardPwdId", String.valueOf(keyboardPwdId));
        params.add("deleteType", "1"); // Bluetooth
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Change password
     */
    public Map<String, Object> changePassword(String accessToken, int lockId, int keyboardPwdId,
                                               String keyboardPwdName, String newKeyboardPwd,
                                               Long startDate, Long endDate) {
        String url = apiBaseUrl + "/v3/keyboardPwd/change";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("keyboardPwdId", String.valueOf(keyboardPwdId));
        if (keyboardPwdName != null) {
            params.add("keyboardPwdName", keyboardPwdName);
        }
        if (newKeyboardPwd != null) {
            params.add("newKeyboardPwd", newKeyboardPwd);
        }
        if (startDate != null) {
            params.add("startDate", String.valueOf(startDate));
        }
        if (endDate != null) {
            params.add("endDate", String.valueOf(endDate));
        }
        params.add("changeType", "1"); // Bluetooth
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Reset keyboard password
     */
    public Map<String, Object> resetKeyboardPassword(String accessToken, int lockId, String pwdInfo, Long timestamp) {
        String url = apiBaseUrl + "/v3/lock/resetKeyboardPwd";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("pwdInfo", pwdInfo);
        params.add("timestamp", String.valueOf(timestamp));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Upload operation record
     */
    public Map<String, Object> uploadRecord(String accessToken, int lockId, String records) {
        String url = apiBaseUrl + "/v3/lockRecord/upload";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("records", records);
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doPost(url, params);
    }

    /**
     * Get password list
     */
    public Map<String, Object> getPasswordList(String accessToken, int lockId, int pageNo, int pageSize) {
        String url = apiBaseUrl + "/v3/lock/listKeyboardPwd";
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("clientId", clientId);
        params.add("accessToken", accessToken);
        params.add("lockId", String.valueOf(lockId));
        params.add("pageNo", String.valueOf(pageNo));
        params.add("pageSize", String.valueOf(pageSize));
        params.add("date", String.valueOf(System.currentTimeMillis()));

        return doGet(url, params);
    }

    private Map<String, Object> doPost(String url, MultiValueMap<String, String> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            
            log.info("TTLock API POST {} Response: {}", url, response.getBody());
            
            return objectMapper.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            log.error("TTLock API POST {} Error: {}", url, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("errcode", -1);
            errorResult.put("errmsg", e.getMessage());
            return errorResult;
        }
    }

    private Map<String, Object> doGet(String url, MultiValueMap<String, String> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            params.forEach((key, values) -> values.forEach(value -> builder.queryParam(key, value)));
            URI uri = builder.build().encode().toUri();

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class);

            log.info("TTLock API GET {} Response: {}", url, response.getBody());

            return objectMapper.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            log.error("TTLock API GET {} Error: {}", url, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("errcode", -1);
            errorResult.put("errmsg", e.getMessage());
            return errorResult;
        }
    }
}
