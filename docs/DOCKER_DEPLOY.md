# 本地 Docker 部署操作手册

> 面向**自托管 / 局域网私有化**部署。项目后端 `agent-soma` 打包为 Docker 镜像，与 MySQL 一起用 `docker compose` 编排。
> 本文档中的中文命令适用于装有 Docker Engine 20.10+ 与 Docker Compose v2 的 Linux/Windows(WSL2) 主机。

---

## 1. 前置条件

| 项 | 说明 |
|---|---|
| Docker Engine | ≥ 20.10 |
| Docker Compose | v2（`docker compose` 子命令） |
| 磁盘 | 镜像约 2.5~3GB（含 Chromium），数据卷另计 |
| 网络 | 构建/拉取镜像需访问 Docker Hub 与 Maven Central |

> 本机若尚未安装 Docker，请先安装（Windows：Docker Desktop + WSL2 后端；Linux：`sudo apt install docker.io docker-compose-v2`），并确认 `docker compose version` 可用。

---

## 2. 快速开始

```bash
# 1) 进入项目根目录
cd agent-soma

# 2) 由模板生成 .env，并填写强口令与 AI Key
cp .env.example .env
vi .env            # 至少填 AI_API_KEY / MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD

# 3) 构建并启动（数据库先起，应用等数据库健康后启动）
docker compose up -d --build

# 4) 查看状态
docker compose ps

# 5) 打开控制台
#    http://<主机IP>:8080/
```

首次启动会自动：创建 `sk_agent` 库与最小权限账号 `sk_app`；应用启动时建表。日志：
```bash
docker compose logs -f app
docker compose logs mysql
```

停止 / 删除（保留数据卷）：
```bash
docker compose down        # 停止容器，数据卷保留
docker compose down -v     # 停止并删除数据卷（谨慎，会清空数据库）
```

---

## 3. 配置项（.env）

| 变量 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `AI_API_KEY` | ✅ | - | LLM 密钥（如 DeepSeek） |
| `AI_BASE_URL` | | `https://api.deepseek.com` | LLM 基址 |
| `AI_MODEL` | | `deepseek-chat` | 模型名 |
| `MYSQL_ROOT_PASSWORD` | ✅ | - | MySQL root 口令（仅容器内部管理用） |
| `MYSQL_USER` | | `sk_app` | 应用连接的低权限业务账号 |
| `MYSQL_PASSWORD` | ✅ | - | 业务账号口令 |

> `.env` 已被 `.gitignore` 忽略，真实密钥不会入库；`.env.example` 作模板提交。

---

## 4. 浏览器自动化（关键说明）

镜像内**默认无头 Chromium**（Playwright Java 1.49 官方运行时镜像，浏览器与系统依赖已内置），用于抓取/操作需登录的企业系统（如用友 U9）。特点：

- 容器内 `SK_AGENT_BROWSER_CHANNEL` 置空 → 使用内置 chromium，而非本机 Edge。
- `BrowserService` 已对容器加 `--no-sandbox --disable-dev-shm-usage`，避免 root 容器与 `/dev/shm` 过小导致的启动失败。
- **登录态持久化**：默认登录态保存在容器内 `/app/.playwright-profile`。重启容器会丢失，建议把它做成卷持久化：
  ```yaml
  # docker-compose.yml 的 app 增加：
  volumes:
    - browser-profile:/app/.playwright-profile
  ```
  （并 `volumes:` 下声明 `browser-profile:`）
- 有头可视化调试：在 `.env` 覆盖 `SK_AGENT_BROWSER_HEADLESS=false`（需有显示环境；纯服务器场景无 GUI 时不要开）。

---

## 5. 常用运维命令

```bash
# 更新镜像与应用
git pull && docker compose up -d --build

# 重启单个服务
docker compose restart app

# MySQL 命令行（进容器）
docker compose exec mysql mysql -usk_app -p sk_agent

# 数据备份（落盘到主机）
docker compose exec -T mysql \
  sh -c 'exec mysqldump -usk_app -p"$MYSQL_PASSWORD" sk_agent' \
  > ./backup-ska-$(date +%F).sql
# 恢复
docker compose exec -T mysql sh -c 'exec mysql -usk_app -p"$MYSQL_PASSWORD" sk_agent' < ./backup-ska-2026-08-25.sql

# 查看资源占用
docker stats
```

---

## 6. 数据库安全要点（本部署已落实）

| 检查项 | 现状 |
|---|---|
| 弱口令/硬编码 | 已改为 `.env` 强口令，禁止默认 `root/root` |
| 对外暴露 | MySQL **不对外暴露端口**，仅 compose 内部网络按服务名可达 |
| 权限最小化 | 应用用 `sk_app` 业务账号（仅授权 `sk_agent` 库），不再用 root 连接 |
| 健康检查 | `mysqladmin ping` 用环境变量口令，不落明文 |
| 密钥入库 | `.env` 已忽略 |

生产建议（超出本镜像范畴）：前端加反向代理（Nginx/`traefik`）+ HTTPS，并将 `ports: "8080:8080"` 收敛为内网 / 反代专用网段。

---

## 7. 常见问题排查

| 现象 | 处理 |
|---|---|
| `Database connection error` / 起不来 | 检查 `.env` 的 `MYSQL_PASSWORD` 与 `MYSQL_USER` 是否一致、`MYSQL_ROOT_PASSWORD` 是否已填 |
| 提示 `MYSQL_ROOT_PASSWORD is required` | 忘了 `cp .env.example .env` 或变量为空；compose 用 `:?` 强制校验 |
| 浏览器任务报错 | 确认镜像 tag 与 pom 的 playwright 版本一致（当前 `v1.49.0`）；查看 `docker compose logs app` 中 `启动 Playwright: channel=, headless=true` |
| `/dev/shm` 过小崩溃 | 已加 `--disable-dev-shm-usage`；仍不足可给 app 加 `shm_size: 1gb` |
| 无法连接内置浏览器 | 若该版本镜像拉取失败，按实际版本改 Dockerfile 的 `playwright/java:v<版本>-jammy` |