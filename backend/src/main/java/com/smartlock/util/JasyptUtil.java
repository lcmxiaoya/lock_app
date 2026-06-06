package com.smartlock.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * Jasypt encryption utility
 * 
 * Usage:
 * 1. Run main method: java JasyptUtil <plaintext>
 * 2. Copy ENC(...) output to application.yml
 */
public class JasyptUtil {

    // 与 application.yml 中 jasypt.encryptor.password 保持一致
    private static final String MASTER_PASSWORD = "mySecretMasterKey123";

    public static String encrypt(String plaintext) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setConfig(getConfig(MASTER_PASSWORD));
        return encryptor.encrypt(plaintext);
    }

    public static String decrypt(String ciphertext) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setConfig(getConfig(MASTER_PASSWORD));
        return encryptor.decrypt(ciphertext);
    }

    private static SimpleStringPBEConfig getConfig(String password) {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        return config;
    }

    /**
     * 生成加密值
     * 
     * 用法: java -cp <classpath> com.smartlock.util.JasyptUtil <要加密的值>
     * 
     * 示例:
     *   java -cp target/classes com.smartlock.util.JasyptUtil postgres
     *   输出: ENC(xR4s3f2g1h5j7k9m=)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: JasyptUtil <要加密的值>");
            System.out.println("示例: JasyptUtil postgres");
            return;
        }

        String plaintext = args[0];
        String encrypted = encrypt(plaintext);
        System.out.println("明文: " + plaintext);
        System.out.println("密文: ENC(" + encrypted + ")");
        System.out.println();
        System.out.println("复制以下内容到 application.yml:");
        System.out.println("ENC(" + encrypted + ")");
    }
}
