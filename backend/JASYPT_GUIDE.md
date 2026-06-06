# Jasypt 配置加密使用指南

## 快速开始

### 1. 生成加密值

```bash
# 编译
mvn clean compile

# 加密数据库密码
java -cp target/classes com.smartlock.util.JasyptUtil postgres
# 输出: ENC(xR4s3f2g1h5j7k9m=)

# 加密 TTLock 密钥
java -cp target/classes com.smartlock.util.JasyptUtil your_ttlock_secret
```

### 2. 替换 application.yml 中的值

```yaml
spring:
  datasource:
    username: ENC(加密后的用户名)
    password: ENC(加密后的密码)

jwt:
  secret: ENC(加密后的JWT密钥)

ttlock:
  client-id: ENC(加密后的client-id)
  client-secret: ENC(加密后的client-secret)
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

## 配置说明

`application.yml` 中的 Jasypt 配置：

```yaml
jasypt:
  encryptor:
    password: mySecretMasterKey123  # 主密钥（与 JasyptUtil 中一致）
```

## 注意事项

- 主密钥 `mySecretMasterKey123` 仅适合开发环境
- 生产环境建议使用环境变量：`${JASYPT_PASSWORD}`
- 每次加密结果不同（因为盐值随机），都是有效的
