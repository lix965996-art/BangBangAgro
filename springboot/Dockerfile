# ============================================================
# 帮帮农后端 Dockerfile (多阶段构建)
# ============================================================
# 构建: docker build -t bangbangagro-backend:1.1.0 .
# 运行: docker run -p 9090:9090 bangbangagro-backend:1.1.0
# ============================================================

# 阶段 1: 用 Maven 构建 JAR
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先拷贝 pom.xml 单独下载依赖,利用 Docker 层缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 拷贝源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ============================================================
# 阶段 2: 运行时镜像(只带 JRE,体积小 60%)
# ============================================================
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="bangbangagro@team"
LABEL version="1.1.0"
LABEL description="帮帮农智慧农业平台后端"

WORKDIR /app

# 拷贝构建产物
COPY --from=builder /build/target/*.jar app.jar

# 创建非 root 用户运行(安全规范)
RUN useradd -m -u 1001 spring && chown -R spring:spring /app
USER spring

# 健康检查 (依赖 actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:9090/actuator/health || exit 1

EXPOSE 9090

# JVM 参数: G1GC + 容器感知内存
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
