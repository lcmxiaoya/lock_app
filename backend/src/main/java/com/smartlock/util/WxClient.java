package com.smartlock.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlock.exception.BusinessException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序后端封装：jscode2session、access_token、phonenumber.getPhoneNumber。
 *
 * <p>access_token 内存缓存 7000 秒（微信侧 7200，留 200 秒余量）。
 * 单实例够用；多实例后续上 Redis 集中存放，避免每个实例各刷一份导致频率限制。</p>
 *
 * <p>{@code wechat.skip-ssl-validate=true} 时跳过 SSL 证书校验——仅用于临时绕过
 * cacerts 缺失问题（如云托管 runtime JRE 不带所需中间 CA），生产请保持 false。</p>
 */
@Slf4j
@Component
public class WxClient {

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.api-base-url:https://api.weixin.qq.com}")
    private String apiBaseUrl;

    /** dev/test 模式跳过 SSL 证书校验。生产务必保持 false。 */
    @Value("${wechat.skip-ssl-validate:false}")
    private boolean skipSslValidate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile HttpClient httpClient;
    private volatile String cachedAccessToken;
    private volatile long accessTokenExpiresAt = 0L;

    /**
     * 懒构造 HttpClient：默认走 JRE cacerts；skip-ssl-validate=true 时跳过证书校验。
     */
    private HttpClient httpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    HttpClient.Builder builder = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10));
                    if (skipSslValidate) {
                        try {
                            SSLContext sslContext = SSLContext.getInstance("TLS");
                            sslContext.init(null, trustAllManagers(), new SecureRandom());
                            builder.sslContext(sslContext);
                            log.warn("WxClient: SSL certificate validation is DISABLED (wechat.skip-ssl-validate=true). DO NOT use in production.");
                        } catch (Exception e) {
                            log.error("Failed to init trust-all SSL context, fallback to default", e);
                        }
                    }
                    httpClient = builder.build();
                }
            }
        }
        return httpClient;
    }

    private static TrustManager[] trustAllManagers() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                }
        };
    }

    private void requireConfigured() {
        if (appid == null || appid.isEmpty() || secret == null || secret.isEmpty()) {
            throw new BusinessException(2011, "微信登录未配置，请联系管理员");
        }
    }

    /**
     * 用 wx.login() 颁发的 code 换 openid + unionid + session_key。
     * code 5 分钟有效，一次性消费。
     */
    public WxSession jscode2session(String code) {
        requireConfigured();
        String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/sns/jscode2session")
                .queryParam("appid", appid)
                .queryParam("secret", secret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .toUriString();

        Map<String, Object> body = doGet(url);
        if (body.containsKey("errcode") && ((Number) body.get("errcode")).intValue() != 0) {
            throw translateWxError(body, "微信登录会话获取失败");
        }
        String openid = (String) body.get("openid");
        if (openid == null || openid.isEmpty()) {
            throw new BusinessException(2010, "微信登录失败：openid 为空");
        }
        WxSession session = new WxSession();
        session.openid = openid;
        session.unionid = (String) body.get("unionid");
        session.sessionKey = (String) body.get("session_key");
        return session;
    }

    /**
     * 通过 getPhoneNumber 回调里的 code 换手机号。返回纯号码（不带国家码）。
     * 若 access_token 失效（errcode=40001），自动清缓存并重试一次。
     */
    public String getPhoneNumber(String phoneCode) {
        requireConfigured();
        String accessToken = getAccessToken();
        String url = UriComponentsBuilder
                .fromHttpUrl(apiBaseUrl + "/wxa/business/getuserphonenumber")
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();

        Map<String, String> req = new HashMap<>();
        req.put("code", phoneCode);

        Map<String, Object> body = doPostJson(url, req);
        if (body.containsKey("errcode") && ((Number) body.get("errcode")).intValue() != 0) {
            int errcode = ((Number) body.get("errcode")).intValue();
            if (errcode == 40001) {
                log.warn("access_token invalid, clearing cache and retrying");
                clearAccessTokenCache();
                accessToken = getAccessToken();
                url = UriComponentsBuilder
                        .fromHttpUrl(apiBaseUrl + "/wxa/business/getuserphonenumber")
                        .queryParam("access_token", accessToken)
                        .build()
                        .toUriString();
                body = doPostJson(url, req);
            }
            if (body.containsKey("errcode") && ((Number) body.get("errcode")).intValue() != 0) {
                throw translateWxError(body, "获取手机号失败");
            }
        }
        Object phoneInfoObj = body.get("phone_info");
        if (!(phoneInfoObj instanceof Map)) {
            throw new BusinessException(2010, "获取手机号失败：响应格式异常");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> phoneInfo = (Map<String, Object>) phoneInfoObj;
        String pure = (String) phoneInfo.get("purePhoneNumber");
        if (pure == null || pure.isEmpty()) {
            throw new BusinessException(2010, "获取手机号失败：手机号为空");
        }
        return pure;
    }

    /**
     * 获取小程序全局 access_token。带内存缓存。
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < accessTokenExpiresAt) {
            return cachedAccessToken;
        }
        synchronized (this) {
            if (cachedAccessToken != null && now < accessTokenExpiresAt) {
                return cachedAccessToken;
            }
            requireConfigured();
            String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/cgi-bin/token")
                    .queryParam("grant_type", "client_credential")
                    .queryParam("appid", appid)
                    .queryParam("secret", secret)
                    .build()
                    .toUriString();

            Map<String, Object> body = doGet(url);
            if (body.containsKey("errcode") && ((Number) body.get("errcode")).intValue() != 0) {
                throw translateWxError(body, "获取微信 access_token 失败");
            }
            String token = (String) body.get("access_token");
            Number expiresIn = (Number) body.get("expires_in");
            if (token == null || token.isEmpty() || expiresIn == null) {
                throw new BusinessException(2010, "获取微信 access_token 失败：响应异常");
            }
            // 7200s 留 200s 余量
            this.cachedAccessToken = token;
            this.accessTokenExpiresAt = now + (expiresIn.longValue() - 200) * 1000L;
            log.info("WX access_token refreshed, expires in {}s", expiresIn.longValue());
            return token;
        }
    }

    /**
     * 清除 access_token 缓存，强制下次调用时重新获取。
     */
    public void clearAccessTokenCache() {
        synchronized (this) {
            this.cachedAccessToken = null;
            this.accessTokenExpiresAt = 0L;
            log.info("WX access_token cache cleared");
        }
    }

    private BusinessException translateWxError(Map<String, Object> body, String fallbackPrefix) {
        int errcode = ((Number) body.get("errcode")).intValue();
        String errmsg = String.valueOf(body.get("errmsg"));
        String friendly;
        switch (errcode) {
            case 40001: friendly = "微信登录凭证已过期，请重试"; break;
            case 40029: friendly = "微信授权已过期，请重新点击登录"; break;
            case 45011: friendly = "操作过于频繁，请稍后再试"; break;
            case 40013: friendly = "微信 AppID 配置错误"; break;
            case 40125: friendly = "微信 AppSecret 配置错误"; break;
            case 40226: friendly = "高风险用户，微信侧已拒绝"; break;
            default:    friendly = fallbackPrefix + "（" + errcode + "）";
        }
        log.warn("WX API error: code={}, msg={}", errcode, errmsg);
        return new BusinessException(2010, friendly);
    }

    private Map<String, Object> doGet(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(resp.body());
            return objectMapper.convertValue(node, Map.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("WX GET failed: url={}", url, e);
            throw new BusinessException(2010, "微信接口调用失败：" + e.getMessage());
        }
    }

    private Map<String, Object> doPostJson(String url, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(resp.body());
            return objectMapper.convertValue(node, Map.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("WX POST failed: url={}", url, e);
            throw new BusinessException(2010, "微信接口调用失败：" + e.getMessage());
        }
    }

    @Getter
    public static class WxSession {
        private String openid;
        private String unionid;
        private String sessionKey;
    }
}
