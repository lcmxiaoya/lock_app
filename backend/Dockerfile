FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# 运行时镜像
# 1. 锁版本：避免 :17-jre 浮动 tag 拉到老 patch 版（cacerts 不全）
FROM eclipse-temurin:17.0.11_9-jre

WORKDIR /app

# 2. 补 api.weixin.qq.com 需要的中间 CA
#    链：api.weixin.qq.com
#        └─ DigiCert Secure Site OV G2 TLS CN RSA4096 SHA256 2022 CA1
#            └─ DigiCert Global Root G2 (JRE cacerts 默认就有)
#    缺的就是那个 2022 中间 CA，导进 JRE cacerts
COPY wechat-tls-intermediate.crt /tmp/wechat-tls-intermediate.crt
RUN $JAVA_HOME/bin/keytool -importcert -trustcacerts -noprompt \
        -alias wechat-tls-intermediate \
        -file /tmp/wechat-tls-intermediate.crt \
        -keystore $JAVA_HOME/lib/security/cacerts \
        -storepass changeit \
    && rm /tmp/wechat-tls-intermediate.crt

COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENV SERVER_PORT=8080
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "app.jar"]
