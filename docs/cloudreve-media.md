# Cloudreve 媒体运行手册

本手册将 Cloudreve v4 作为博客的第三种媒体 provider。浏览器始终只访问博客稳定媒体地址；博客 API 使用 OAuth Token 代理 Cloudreve 的上传、读取与删除。Cloudreve 的 Client Secret、Access Token、Refresh Token、Token 加密密钥、内部端点和上传临时凭据均不得进入浏览器、Nginx、前端构建、健康检查、日志、截图或 Git。

Local 是默认值。只有把 `BLOG_MEDIA_PROVIDER=cloudreve` 用于新上传，或设置 `BLOG_MEDIA_CLOUDREVE_ENABLED=true` 以读取历史 Cloudreve 对象时，API 才会校验 Cloudreve 配置。

## 上线前条件

- 使用非生产 Cloudreve OAuth 应用、文件空间和博客环境完成本手册末尾的外部联调门禁；本仓库的自动测试不连接真实 Cloudreve。
- Cloudreve、博客公网入口和 OAuth 回调在生产均使用 HTTPS。`BLOG_MEDIA_CLOUDREVE_ALLOW_TRUSTED_INTERNAL_HTTP=true` 仅可用于隔离开发环境中可确认的私有网络，不能用于生产、公共网络或通过不受控代理的流量。
- Compose 只把 Cloudreve 配置传给 `api`。`web`、Nginx、前端 Docker build、公开健康检查和命令行都不应拿到这些值。运行 `docker compose --env-file .env config --quiet` 只验证插值，不会检查 OAuth 是否真实可用。
- 生产 `.env` 必须在受限主机目录，或由容器 Secret/密钥管理系统注入；它不属于仓库，也不应复制进备份日志。

## 创建 OAuth 应用和文件空间

1. 在目标 Cloudreve v4 实例创建一个只供博客使用的 OAuth 应用，使用 Authorization Code + PKCE，不复用管理后台或其他应用的 Client ID。
2. 将下面的**完整且精确**回调地址登记为 Redirect URI：`https://blog.example.com/api/admin/media/cloudreve/callback`。实际域名和路径以 `BLOG_MEDIA_CLOUDREVE_REDIRECT_URI` 为准；不要使用通配符、fragment、URL 中的凭据或临时测试回调。反向代理必须将该路径转发至 API，并保持管理员会话 cookie。
3. 应用请求 `openid profile offline_access Files.Write`。代码强制要求 `offline_access`（刷新 Token）和 `Files.Write`（文件操作）；`openid profile` 用于核对并显示授权身份。不要预先授予管理权限。若真实实例拒绝读取、下载或删除并明确要求额外 scope（例如 `Files.Read`），记录实例返回的精确要求，停止发布，并只为专用 OAuth 应用增加该最小 scope 后重测。
4. 在 Cloudreve 为 OAuth 授权用户选择或创建博客专用根目录，例如 `/blog`。授予此用户该目录及其子目录所需的创建、写入、查询、读取和删除权限；不要把根目录设为可写的整个个人文件空间。记录 Cloudreve 的 storage policy ID，填写到 `BLOG_MEDIA_CLOUDREVE_POLICY_ID`。政策、根目录或权限不足通常会在首次上传/读取时失败。
5. 将 Client ID、Client Secret 和 token 加密密钥存入部署密钥系统。任何测试 Client Secret 都必须在生产上线前在 Cloudreve 中轮换：测试环境、浏览器历史、截图、CI 或聊天记录都可能已经暴露过它，不能作为生产密钥使用。

## API 容器配置

从 `.env.example` 复制变量名到部署密钥系统。以下只由 `api` 使用；所有网络位置均可配置，仓库和 Compose 不包含具体实例地址、端口或凭据。

| 变量 | 用途与要求 |
| --- | --- |
| `BLOG_MEDIA_PROVIDER` | `local`（默认）、`r2` 或 `cloudreve`；只决定新上传位置。 |
| `BLOG_MEDIA_CLOUDREVE_ENABLED` | `false` 默认；设为 `true` 可在默认 provider 不是 Cloudreve 时保留历史 Cloudreve 读取能力。 |
| `BLOG_MEDIA_CLOUDREVE_BASE_URL` | Cloudreve 的绝对基础 URL。留空的 OAuth endpoint 会从它解析。生产必须是 HTTPS、无凭据、无 query/fragment。 |
| `BLOG_MEDIA_CLOUDREVE_AUTHORIZATION_URI` | 可选绝对授权 endpoint；空值使用 `/session/authorize`。 |
| `BLOG_MEDIA_CLOUDREVE_TOKEN_URI` | 可选绝对换码 endpoint；空值使用 `/api/v4/session/oauth/token`。 |
| `BLOG_MEDIA_CLOUDREVE_REFRESH_URI` | 可选绝对刷新 endpoint；空值使用 `/api/v4/session/token/refresh`。 |
| `BLOG_MEDIA_CLOUDREVE_USERINFO_URI` | 可选绝对用户信息 endpoint；空值使用 `/api/v4/session/oauth/userinfo`。 |
| `BLOG_MEDIA_CLOUDREVE_REDIRECT_URI` | 已登记的完整博客回调 URL，通常为 `https://blog.example.com/api/admin/media/cloudreve/callback`。 |
| `BLOG_MEDIA_CLOUDREVE_CLIENT_ID` / `BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET` | OAuth 应用凭据；仅从密钥系统注入。 |
| `BLOG_MEDIA_CLOUDREVE_POLICY_ID` | Cloudreve storage policy 的稳定 ID，不能填名称猜测值。 |
| `BLOG_MEDIA_CLOUDREVE_ROOT_PATH` | 逻辑绝对根目录，默认 `/blog`；不允许 `..`、反斜杠或控制字符。 |
| `BLOG_MEDIA_TOKEN_ENCRYPTION_KEY` | 与 Client Secret 不同的 base64 编码 32-byte AES-256 密钥。 |
| `BLOG_MEDIA_CLOUDREVE_ALLOW_TRUSTED_INTERNAL_HTTP` | 生产固定 `false`；仅隔离开发环境可以显式设为 `true`。 |
| `BLOG_MEDIA_CLOUDREVE_CONNECT_TIMEOUT` / `BLOG_MEDIA_CLOUDREVE_REQUEST_TIMEOUT` | 可选正时长，默认 `5s` / `30s`。 |

端点可单独覆盖，以支持反向代理路径、域名或端口变化；不要在应用代码、Compose、Nginx 或前端中硬编码它们。默认 endpoint 仅适用于未被实例定制的 Cloudreve v4 安装。

生成 Token 加密密钥时，在受控管理员工作站运行以下命令并直接保存输出到密钥系统，绝不把输出粘贴到 issue、终端录屏或仓库：

```bash
openssl rand -base64 32
```

备份恢复必须同时保护数据库备份和当时的 `BLOG_MEDIA_TOKEN_ENCRYPTION_KEY`：没有该密钥，旧数据库中的 Token 密文不能恢复为明文，只能重新授权。密钥不等于 Client Secret，二者不得复用。

## 连接、重新授权与断开

1. 使用管理员账号打开后台“媒体设置”，确认 Cloudreve 状态显示为已配置，然后选择“连接 Cloudreve”。
2. 在 Cloudreve 完成授权并返回已登记回调。成功后页面只显示授权账户的非敏感身份、scope 与过期时间；Token 不会出现在页面或回调 URL。
3. Access Token 接近过期时 API 自动刷新。Refresh Token 失效、scope 缺失或密钥无法解密时状态会变成 `REAUTH_REQUIRED`，Cloudreve 媒体操作返回可重试的通用 503；选择“重新授权”，不要手工修改数据库。
4. “断开连接”会删除/失效本地保存的 Token，不会删除 Cloudreve 文件或历史媒体记录。重新连接同一文件空间后可恢复读取；断开期间历史 Cloudreve 媒体不可读。

## 密钥轮换

轮换 Client Secret：在 Cloudreve 创建或轮换专用应用密钥，将新值更新到密钥系统，滚动重启 API，并用非生产或维护窗口完成连接/重新授权、上传、匿名读取和删除未引用媒体的验证；确认正常后再撤销旧值。泄露时立刻撤销旧 Client Secret、审计授权记录并重新授权。

轮换 Token 加密密钥需要计划维护：先备份数据库并安全保管旧密钥；替换 `BLOG_MEDIA_TOKEN_ENCRYPTION_KEY` 后，旧 Token 密文不能由新实例读取，连接会要求重新授权。完成重新授权和媒体读取验证后再按密钥保留策略销毁旧密钥。恢复历史数据库备份时，必须恢复与其对应的旧密钥；不要用新密钥尝试解密旧备份。

## Provider 切换、迁移、回退与卸载

`BLOG_MEDIA_PROVIDER` 仅改变**新上传**位置。数据库记录保存实际 provider 和位置，因此 Cloudreve、Local 与 R2 可以同时读取。切换到 Cloudreve 前保留 Local/R2 配置与数据卷；从 Cloudreve 切回 Local 时，先设 `BLOG_MEDIA_PROVIDER=local`，但对仍有 Cloudreve 历史记录的安装继续设置 `BLOG_MEDIA_CLOUDREVE_ENABLED=true` 并保留完整 API 凭据和连接。

没有内置一键迁移。生产迁移应先备份并在非生产副本演练，暂停上传；以小批次从源读取、向目标写入、验证 key、类型、大小和 ETag，再只更新已验证记录的 provider/location。稳定媒体 ID、Markdown、封面和附件关系无需改变。保留源对象、旧配置和可恢复数据库备份直至观察窗口结束。回退时将已迁移记录指回仍保留的源对象，并把默认 provider 改回原值。

若要卸载 Cloudreve：先迁移或删除所有 Cloudreve `media_asset` 记录并验证稳定地址，再断开后台连接，保留备份窗口后移除 `BLOG_MEDIA_CLOUDREVE_*`、`BLOG_MEDIA_TOKEN_ENCRYPTION_KEY` 和 OAuth 应用。不能先移除凭据或禁用 Cloudreve，否则历史对象会返回 503；断开或卸载不会自动删除远端文件。

## 故障排查

| 现象 | 首先检查 | 安全处理 |
| --- | --- | --- |
| 后台显示“未配置”或启动失败 | `enabled`/provider 是否意外启用；所有必填 API 变量、HTTPS、root、policy ID 与 32-byte 密钥是否完整 | 只检查变量是否存在和格式，不回显值。 |
| 授权后回调失败 | 精确 Redirect URI、管理员会话 cookie、反向代理路径、HTTPS 和时间同步 | 不记录 authorization code、state、PKCE verifier 或完整回调 query。 |
| `REAUTH_REQUIRED` 或通用 503 | Refresh Token 过期/撤销、scope、Client Secret 轮换或加密密钥是否变化 | 用后台“重新授权”；不要修改密文或把 Token 打到日志。 |
| 上传、读取或删除失败 | root 子目录权限、policy ID、Cloudreve 服务状态、网络/TLS、超时与最小 scope | 暂停新上传，保留媒体记录与源对象，恢复后重试。 |
| 历史 Cloudreve 媒体在 Local 默认下不可读 | `BLOG_MEDIA_CLOUDREVE_ENABLED=true`、API 连接和 Cloudreve 根目录仍可用 | 不要只因切换默认 provider 删除 Cloudreve 配置。 |

## 外部联调发布门禁（Task 10）

自动测试和 Compose 静态检查只能验证配置边界，不能证明具体 Cloudreve 实例的 OAuth、scope 或 storage policy。使用未提交的非生产 Client ID/Secret 和文件空间，在生产发布前记录**脱敏**结果：

1. 完成 OAuth，验证回调回到博客后台、已授权身份和授予 scope；如果缺少额外 read scope，停止并记录精确 scope。
2. 上传 PNG 与小型公开附件，完成校验，发布后用未登录窗口验证图片和附件下载；确认对象位于配置根目录。
3. 验证 Access Token 自动刷新；短暂中断 Cloudreve 后确认返回可重试 503 且没有误删，再恢复并重试。
4. 删除一条未引用测试媒体，确认 Cloudreve 文件消失；把默认 provider 切回 Local，确认历史 Cloudreve 媒体仍可读取。

记录时间、HTTP 状态类别、媒体 ID、已脱敏路径和结论即可。不得记录 Client Secret、授权码、state、PKCE verifier、Access/Refresh Token、签名 URL 或可能包含凭据的响应正文。未完成这些外部门禁时，不得声称真实 Cloudreve 实例已经验收。
