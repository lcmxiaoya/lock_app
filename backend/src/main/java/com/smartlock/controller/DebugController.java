package com.smartlock.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时诊断接口：列出 JRE cacerts 里 DigiCert / GeoTrust 相关条目，
 * 并对 api.weixin.qq.com 做一次 TLS 握手验证。
 *
 * <p>部署到云托管后，浏览器或 curl GET /api/debug/cacerts 即可。
 * 修好证书后请删除本类并在 JwtFilter 中移除 /api/debug/cacerts 白名单。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @GetMapping("/cacerts")
    public Map<String, Object> cacerts() {
        Map<String, Object> result = new HashMap<>();
        String javaHome = System.getProperty("java.home");
        result.put("javaHome", javaHome);

        // 1) 列出 cacerts 里所有 DigiCert / GeoTrust / Tencent 相关条目
        List<String> matched = new ArrayList<>();
        try {
            Process p = new ProcessBuilder(
                    javaHome + "/bin/keytool",
                    "-list", "-keystore", javaHome + "/lib/security/cacerts",
                    "-storepass", "changeit"
            ).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String low = line.toLowerCase();
                    if (low.contains("digicert") || low.contains("geotrust")
                            || low.contains("tencent") || low.contains("wechat")
                            || low.contains("globalsign") || low.contains("entrust")) {
                        matched.add(line);
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            result.put("keytoolError", e.getMessage());
        }
        result.put("matchedAliases", matched);

        // 2) 对 api.weixin.qq.com 做一次 TLS 握手
        Map<String, Object> handshake = new HashMap<>();
        try (SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault()
                .createSocket("api.weixin.qq.com", 443)) {
            s.startHandshake();
            X509Certificate[] chain;
            try {
                chain = (X509Certificate[]) s.getSession().getPeerCertificates();
            } catch (Exception ex) {
                chain = null;
            }
            handshake.put("ok", true);
            handshake.put("peerPrincipal", String.valueOf(s.getSession().getPeerPrincipal()));
            handshake.put("cipherSuite", s.getSession().getCipherSuite());
            if (chain != null) {
                List<String> subjects = new ArrayList<>();
                for (X509Certificate c : chain) {
                    subjects.add(c.getSubjectX500Principal().getName());
                }
                handshake.put("chainSubjects", subjects);
            }
        } catch (Exception e) {
            handshake.put("ok", false);
            handshake.put("error", e.getMessage());
        }
        result.put("handshake", handshake);

        return result;
    }
}
