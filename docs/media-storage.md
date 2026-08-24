# 媒体存储：Local 与 Cloudflare R2

媒体模块为文章内图片、头像、封面和公开附件分配稳定地址：
`/api/media/assets/{mediaId}`。Markdown 与业务数据只保存该地址或媒体 ID，**绝不保存** R2、`r2.dev`、GitHub/Gitee 等供应商 URL。因此对象域名、桶名或供应商变化时，已发布文章无需重写。

第一版支持 `local` 与 `r2`。上传是正式对象上传：`READY` 但未被内容引用的对象会在后台媒体库显示为“未使用”，不会自动清理；等待上传超过 24 小时或校验失败的对象会进入清理流程。附件默认公开下载，不能上传敏感资料。

## 运行方式与变量

开发与不使用对象存储时保持默认 Local：

```dotenv
BLOG_MEDIA_PROVIDER=local
BLOG_MEDIA_LOCAL_DIRECTORY=/app/data/media
```

Compose 将该目录持久化到具名卷 `media-data`。不要在升级、故障恢复或切换 provider 时删除此卷；它仍保存旧的 Local 媒体。生产 `.env` 只应位于部署主机的受限目录或由密钥管理系统注入，绝不能提交。

启用 R2 时将 `BLOG_MEDIA_PROVIDER=r2`，并仅把以下变量注入 **API 容器**：

```dotenv
BLOG_MEDIA_PROVIDER=r2
BLOG_MEDIA_R2_ACCOUNT_ID=<Cloudflare account id>
BLOG_MEDIA_R2_ACCESS_KEY_ID=<R2 access key id>
BLOG_MEDIA_R2_SECRET_ACCESS_KEY=<R2 secret access key>
BLOG_MEDIA_R2_BUCKET=xiaom-blog-media
# 留空时服务根据 account id 推导；显式填写便于审计。
BLOG_MEDIA_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
BLOG_MEDIA_R2_PUBLIC_BASE_URL=https://img.example.com
BLOG_MEDIA_R2_LEGACY_BUCKETS=
BLOG_MEDIA_R2_UPLOAD_URL_TTL=10m
# Nginx CSP：必须是精确 origin，不允许 *。应分别与上面的 endpoint、公共域 origin 一致。
MEDIA_UPLOAD_ORIGIN=https://<account-id>.r2.cloudflarestorage.com
MEDIA_PUBLIC_ORIGINS=https://img.example.com
```

R2 provider 启动时会校验账户、Access Key、Secret、bucket 与 HTTPS 公共地址；故意不会让 Compose 在 Local 模式下要求这些变量。R2 固定使用 S3 区域 `auto` 和 path-style endpoint，因而没有 `BLOG_MEDIA_R2_REGION` 变量。凭据不会传给 Nginx/Web 容器、浏览器或前端构建；浏览器只会取得服务端签发、默认 10 分钟过期的单对象 PUT URL。

`MEDIA_UPLOAD_ORIGIN` 与 `MEDIA_PUBLIC_ORIGINS` 只是注入 Nginx CSP 的非敏感 origin：前者填写 R2 S3 endpoint 的 origin，后者填写公共图片域 origin；多个公共域以空格分隔。不要填写 `*`。API 也会从 R2 配置生成同样的窄 CSP。启用 R2 后若遗漏这些 Web 变量，浏览器会阻止跨域 PUT 或重定向后的图片加载。

新上传使用 `BLOG_MEDIA_PROVIDER` 指定的默认 provider；历史对象读取则始终按数据库中的 `(provider,bucket,storage_key)` 定位。因而切回 Local 作为默认上传方式后，只要仍保留 R2 凭据与可读桶配置，历史 R2 对象仍可访问。跨桶迁移期间可设置：

```dotenv
BLOG_MEDIA_R2_LEGACY_BUCKETS=old-media=https://old-img.example.com,archive-media=https://archive-img.example.com/blog
MEDIA_PUBLIC_ORIGINS=https://img.example.com https://old-img.example.com https://archive-img.example.com
```

旧桶列表采用 `bucket=https://public-base`，逗号分隔；每个桶必须显式列出，同一组 R2 凭据也必须仍有这些桶的 Object Read/Delete 权限。未列出的持久化桶会被拒绝，避免悄悄从当前默认桶读取同名 key。

## 创建 R2 桶与公开域名

1. 在 Cloudflare Dashboard 的 **R2 Object Storage** 创建私有桶，例如 `xiaom-blog-media`。名称一经使用不要随意改动。
2. 在 **R2 API Tokens / Manage API Tokens** 创建专用 token，只授予这个桶的 **Object Read & Write** 权限。不要使用账户全局 API Token，也不要给 Delete Bucket、DNS 或其他 Cloudflare 产品权限。保存返回的 Access Key ID 与 Secret Access Key 到部署密钥管理系统。
3. 使用 API endpoint `https://<account-id>.r2.cloudflarestorage.com`。它用于服务器签名、HEAD、读取和删除对象，不能作为 Markdown 地址。
4. 在桶的 **Settings / Public access** 绑定自定义域，例如 `img.example.com`，完成 DNS/证书生效后设置为 `BLOG_MEDIA_R2_PUBLIC_BASE_URL=https://img.example.com`。公共域必须是 HTTPS，且不能包含 query、fragment 或凭据；如使用路径前缀，填写完整前缀，例如 `https://img.example.com/blog-media`。
5. `r2.dev` 只适合临时开发和排障；它不是生产公开域名。生产使用已绑定的自定义域，并对该域配置缓存、安全和归属管理。

对象 key 由 API 生成，客户端不能传 bucket 或 key。图片为 PNG/JPEG/GIF（最大 5 MiB、边长最多 6000 像素）；公开附件为 PDF、ZIP、TXT、DOCX、XLSX、PPTX（通常 20 MiB，ZIP 50 MiB）。服务会在 complete 阶段重新检查对象大小、类型、签名和图片可解码性。
Compose 的 Nginx 入口将 `client_max_body_size` 设为 64 MiB，为后端 51 MiB 的请求上限和 multipart 封装预留空间。若在 Compose 前再部署 CDN 或反向代理，也必须把其请求体上限设为至少 64 MiB，否则 Local/代理上传会在到达应用前被拒绝。

## R2 CORS（浏览器直传必配）

在该桶的 CORS 设置中粘贴以下 JSON，并把两个示例 origin 换成实际生产站点与开发站点。`AllowedOrigins` 必须是精确 origin，不要使用 `*`；生产不需要本地开发时删除第二项。

```json
[
  {
    "AllowedOrigins": [
      "https://blog.example.com",
      "http://127.0.0.1:5173"
    ],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": [
      "Content-Type",
      "Cache-Control",
      "Content-Disposition"
    ],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 86400
  }
]
```

前端会原样使用上传计划要求的 `Content-Type`、`Cache-Control` 与 `Content-Disposition`。少一个 header 或 origin 不匹配，浏览器会在 PUT 前拦截请求。CORS 只允许上传；对象公开读取由自定义域处理，不需要把 R2 管理 endpoint 暴露给访客。

## 上传与访问行为

1. 管理员调用 `POST /api/admin/media/uploads` 申请上传。该接口要求管理员 Session 与 CSRF，返回 `DIRECT`（R2）或 `PROXY`（Local）计划。
2. R2 时浏览器向预签名 URL 直接 `PUT`；Local 时浏览器带 Session/CSRF 向 `/api/admin/media/uploads/{id}/content` 上传。
3. 浏览器调用 `POST /api/admin/media/{id}/complete`。服务先用短数据库事务将媒体从 `PENDING_UPLOAD` 原子认领为 `VERIFYING`，再在事务外验证对象，最后用第二个短事务将媒体置为 `READY`。重复 complete 是幂等的。
4. Vditor 插入 `![名称](/api/media/assets/{id})`。公开读该地址会 302 到该媒体当前 provider 的公开地址；公开附件下载使用 `/api/media/assets/{id}/download`，并强制下载响应。

上传计划默认按“管理员账号 + 客户端 IP”每分钟最多 30 次，并最多跟踪 10000 个 key。该限流器是有界的单节点内存实现；单实例部署可直接使用。若横向扩展 API，发布前必须替换为 Redis 等共享限流实现，不能把每个节点各自的额度当成集群额度。

未完成、失败、删除中或已删除媒体不会通过稳定公开地址读出。管理员只能开始删除没有文章、头像、封面、专题或工具引用的 READY 媒体，并可重试自己处于 `DELETING` 的媒体；文章删除只移除引用，不自动删除可能复用的对象。

`complete` 会区分内容校验与存储故障：大小、类型、签名或图片解码不合法属于权威失败，服务先在独立事务中持久化 `FAILED`，再在事务外进入可恢复删除流程；R2 HEAD/GET 的超时、5xx、网络中断以及对象暂未可见会返回 HTTP 503，并把仍有效的认领释放回 `PENDING_UPLOAD`，前端可安全重试 `complete`，不会删除对象。Local 代理上传同样用 `UPLOADING` 短事务认领，数据库行锁不会跨越磁盘或网络 I/O。

媒体删除采用可恢复的两阶段状态机。服务先在独立数据库事务内锁定媒体、重新确认无引用并提交 `DELETING`，然后在事务外删除 Local/R2 对象，最后以第二个事务提交 `DELETED`。对象删除或最终数据库提交失败时，`DELETING`/`FAILED` 会保留，后台任务每次按更新时间处理最多 100 条并继续重试；对象删除必须保持幂等。完成认领与清理认领共享同一行级悲观锁和不可猜测的 `operation_token`，因此清理不可能删除一个随后又被写成 `READY` 的对象；进程崩溃遗留的 `UPLOADING`/`VERIFYING` 会在过期后由同一清理任务回收。因此发布或备份脚本不得把删除中状态当作已经彻底删除，也不要手工清除这些行。

V8 以前的历史媒体可能没有 `uploaded_by_id`。仅当系统仍只有一个启用的管理员时，该管理员可删除这些无所有者历史资源；新上传资源始终要求所有者一致。媒体库的 `can_delete` 字段与删除接口使用同一授权规则，前端不得自行根据状态推断权限。

## 密钥轮换与故障处理

1. 新建同桶、同权限的新 R2 API token，并安全保存新 key/secret。
2. 先在非生产环境或一个新的实例以新凭据验证：申请上传、PUT、complete、稳定 URL 302、附件下载和删除未使用媒体。
3. 滚动更新 API 的 `BLOG_MEDIA_R2_ACCESS_KEY_ID` 与 `BLOG_MEDIA_R2_SECRET_ACCESS_KEY`，检查健康检查和日志中没有 R2 验证失败。
4. 观察一个完整上传 TTL（至少 10 分钟）和正常上传后，再撤销旧 token。泄露时立即撤销旧 token、轮换变量并审计对象与访问日志。

不要在日志、截图、CI 输出、issue 或 `.env.example` 中记录 Secret Access Key。若 R2 故障，暂停后台上传；已发布媒体仍可由 CDN 缓存短暂提供，但不要将新的媒体标记为 READY，直到对象检查恢复。

## 迁移与切换 provider

稳定媒体 ID 是切换边界。对每条 `media_asset`，在目标 provider 写入对象、验证目标对象的 key、类型、大小和 ETag 后，再在同一数据库事务更新该记录的 `provider`、`bucket` 与 `storage_key`。只要 ID 不变，Markdown、封面和附件关系不变。

这版没有内置“一键批量搬迁”命令，生产迁移应按以下受控流程执行：

1. 备份 MySQL 与 Local `media-data` 卷，先在非生产副本演练并记录媒体总数、失败数和校验结果。
2. 暂停后台上传；保留旧 provider 配置和对象，不能先删旧数据。
3. 以小批次读取源对象、写入目标对象、HEAD 验证，并仅对验证成功的媒体更新定位。使用可重复运行的脚本，逐条记录 media ID、源/目标 key、大小、ETag 与时间。
4. 抽样验证旧 `/api/media/{storageKey}`、稳定图片地址和公开附件下载；确认所有记录均已迁移后才将默认 `BLOG_MEDIA_PROVIDER` 改为目标 provider，用于新上传。
5. 保留旧对象与旧凭据直到备份窗口、回归和日志观察都完成。回退时恢复上一份数据库备份或将已迁移记录的存储定位指回仍保留的源对象，再将默认 provider 改回 Local。

未来增加 OSS 等 provider 只需实现 `ObjectStorage` 适配器并复用该流程。GitHub/Gitee 可以实现代理上传适配器，但受仓库体积、提交冲突、限流与访问稳定性影响，不适合作为生产图床。

## 上线检查

在测试桶使用未提交的真实变量运行：

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --wait --wait-timeout 240
```

登录后台后依次粘贴/拖拽 Markdown 图片、选择图片、上传每种允许附件、保存草稿、发布文章，并以未登录窗口验证图片 302 与附件下载。检查媒体库的 provider、状态、用途和“未使用”标识；然后切回 Local 环境验证旧 Local 地址仍可读取。记录 HTTP 状态、对象 metadata 和测试桶名称，但不要记录凭据。
