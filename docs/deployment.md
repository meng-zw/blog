# 部署与上线验收

## 前置条件

- Docker Engine 与 Docker Compose v2；生产主机建议仅允许反向代理或防火墙访问 `WEB_PORT`。
- 一个可由容器访问的远程 MySQL 8 地址，以及绑定到 `PUBLIC_BASE_URL` 的 HTTPS 入口。默认 Compose 不启动本地 MySQL；`api` 没有宿主机端口，Actuator 只在 Compose 内部网络可见。
- 发布前必须在有 Docker 的环境运行全部 MySQL/Testcontainers 与 Playwright 门禁。

## 首次部署

1. `cp .env.example .env`，替换每个 `CHANGE_ME`，管理员密码使用独立强密码，`PUBLIC_BASE_URL` 使用最终 HTTPS 地址。Local 为默认媒体存储；如选择 R2，先按 [媒体存储运行手册](media-storage.md) 配好 API 专用凭据、桶、公开域名、精确的 `MEDIA_UPLOAD_ORIGIN` / `MEDIA_PUBLIC_ORIGINS` CSP 来源和 CORS。
2. `docker compose --env-file .env config --quiet` 检查变量与配置。
3. `docker compose --env-file .env build --pull` 构建镜像；构建阶段会先运行后端和前端测试。
4. `docker compose --env-file .env up -d --wait --wait-timeout 240`。
5. `docker compose --env-file .env ps`，确认 `web`、`api` 均为 healthy；访问首页和 `/admin/login`。

不要将 `.env`、数据库卷、媒体卷或备份提交到 Git。生产环境必须保持 `BLOG_SESSION_COOKIE_SECURE=true`。即使启用 R2，也必须保留 `media-data` 卷：其中存有旧 Local 媒体，删除它会让未迁移的历史地址失效。

## 发布前强制验证

```bash
cd blog-backend && mvn -Dblog.requireDockerTests=true clean verify
cd ../blog-frontend && npm ci && npm run test:run && npm run typecheck && npm run build
cd .. && cp .env.test.example .env.test
docker compose --env-file .env.test --profile local-db up --build -d --wait --wait-timeout 240
cd blog-frontend && E2E_BASE_URL=http://127.0.0.1:8080 npm run test:e2e
```

随后在桌面 1440×900 与移动端 390×844 检查 `/`、`/articles`、一篇文章详情、`/tools`、`/about`、`/admin/login`、`/admin/articles/new`。重点核对参考图的页头、横幅比例、内容网格、暖色、字体、边框与间距，并确认没有注册、评论、订阅入口。

媒体验收还必须覆盖 Markdown 粘贴/拖拽、Vditor 文件选择、头像与各类封面、PDF/ZIP/TXT/DOCX/XLSX/PPTX 附件、保存草稿与发布后的匿名图片访问/附件下载。R2 测试使用专用测试桶和未提交变量，检查预签名 PUT、`complete`、稳定地址 302、对象 `Content-Type`/ETag；不要把任何 R2 凭据写入 CI 日志。

验收结束后运行 `docker compose --env-file .env.test down --volumes --remove-orphans` 并删除本地 `.env.test`。

## 备份、恢复与升级

备份/恢复命令见 `scripts/README.md`。恢复会先验证固定 manifest、校验和、临时数据库和媒体归档，暂停 API 写入并保留即时 recovery dump；媒体在同一文件系统以目录切换替换。目标与 manifest 不一致时默认拒绝，只有显式 `--allow-cross-target` 并完成第二次确认才可继续。每次升级前先备份，并在隔离环境验证恢复与校验和。升级使用：

```bash
docker compose --env-file .env build --pull
docker compose --env-file .env up -d --wait --wait-timeout 240
docker compose --env-file .env ps
```

失败时保留远程数据库和媒体卷，查看 `docker compose --env-file .env logs api web`，修复后重建；不要用空卷覆盖已有数据。Local↔R2 的对象迁移、回退和密钥轮换流程见 [媒体存储运行手册](media-storage.md)。仅测试环境需要容器数据库时，使用 `--profile local-db`。
