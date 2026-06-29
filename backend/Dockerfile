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
#    缺的就是那个 2022 中间 CA
#
#    关键：不能只放在 build 时 keytool -importcert，因为云托管可能
#    在 runtime 用 platform base image 覆盖 build 镜像的 cacerts。
#    所以 build 时只放证书文件，runtime 启动时再 import。
COPY wechat-tls-intermediate.crt /app/wechat-tls-intermediate.crt

COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENV SERVER_PORT=8080
ENV TZ=Asia/Shanghai

# 3. 启动脚本：先补证书再跑 jar（无论 JRE 来自 build 还是 platform runtime 都生效）
RUN echo '#!/bin/sh\n\
set -e\n\
JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}\n\
if [ -f /app/wechat-tls-intermediate.crt ]; then\n\
  echo "[entrypoint] importing wechat-tls-intermediate.crt into $JAVA_HOME/lib/security/cacerts"\n\
  $JAVA_HOME/bin/keytool -importcert -trustcacerts -noprompt \\\n\
    -alias wechat-tls-intermediate \\\n\
    -file /app/wechat-tls-intermediate.crt \\\n\
    -keystore $JAVA_HOME/lib/security/cacerts \\\n\
    -storepass changeit 2>/dev/null || true\n\
  $JAVA_HOME/bin/keytool -list -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit 2>/dev/null | grep -i wechat || echo "[entrypoint] cert NOT in cacerts!"\n\
fi\n\
exec java -jar app.jar' > /app/entrypoint.sh && chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
