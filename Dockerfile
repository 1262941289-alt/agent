# ===== 构建阶段：编译并打包 Spring Boot 可执行 jar =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷贝 pom 并下载依赖，利用 Docker 层缓存避免每次改源码都重新拉依赖
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# ===== 运行阶段：Playwright Java 官方镜像（内置 Chromium + 全部系统依赖并带 JDK）=====
# 镜像标签版本必须与 pom.xml 的 com.microsoft.playwright:playwright 一致（当前 1.49.0）
FROM mcr.microsoft.com/playwright/java:v1.49.0-jammy
WORKDIR /app

# 时区：与业务日志/审批时间戳对齐
ENV TZ=Asia/Shanghai

# Playwright 浏览器安装根目录（官方镜像已把 chromium 装到 /ms-playwright）
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# 容器无显示 → 默认无头 Chromium：
#   SK_AGENT_BROWSER_CHANNEL 置空 => 使用内置 chromium 而非本机(msedge)，本地/有 GUI 主机可覆盖回看有头模式
#   SK_AGENT_BROWSER_PROFILE_DIR 持久化登录态（若在容器内做需要登录的抓取，可挂载该目录保留会话）
# 注：BrowserService 已对 root 容器加 --no-sandbox / --disable-dev-shm-usage
ENV SK_AGENT_BROWSER_HEADLESS=true \
    SK_AGENT_BROWSER_CHANNEL= \
    SK_AGENT_BROWSER_PROFILE_DIR=/app/.playwright-profile

COPY --from=build /build/target/sk-agent-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]