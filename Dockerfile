# 多阶段构建：先在 Maven 镜像里打包，再把产物拷贝进精简 JRE 运行镜像
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷贝 pom 并下载依赖，利用 Docker 层缓存避免每次改源码都重新拉依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/sk-agent-*.jar app.jar
# 时区：与业务日志/审批时间戳对齐（默认 Asia/Shanghai）
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
# 注意：浏览器自动化(Playwright)依赖系统浏览器与显示，容器内如需启用
# 应额外安装 chromium + headless 显示位，并设 SK_AGENT_BROWSER_HEADLESS=true；
# 默认镜像只承载平台核心能力（规划/筛选/审批/监督/审计）。