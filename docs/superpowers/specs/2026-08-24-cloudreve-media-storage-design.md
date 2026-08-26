# Cloudreve 媒体存储接入设计

## 1. 目标与范围

个人博客新增 Cloudreve v4 媒体存储提供方。Cloudreve 已在其内部管理 Cloudflare 存储，博客只依赖 Cloudreve OAuth 与文件 API，不直接依赖 Cloudreve 背后的 R2 配置。

本次接入覆盖：

- 管理员在博客后台通过 OAuth 2.0 Authorization Code + PKCE 连接 Cloudreve；
- 文章图片、公开附件、头像和封面通过既有媒体生命周期上传到 Cloudreve；
- 访客继续通过博客稳定媒体地址访问图片和下载附件，无需注册或登录 Cloudreve；
- Access Token 自动刷新，Refresh Token 失效后提示管理员重新授权；
- Cloudreve、Local、R2 可同时读取，默认提供方只决定新上传位置；
- 所有 IP、端口、OAuth 端点、回调地址和根目录均由配置注入；
- 客户端密钥、Token 和加密密钥不得进入 Git、日志或公开 API。

评论、访客账户、Cloudreve 文件管理器以及跨用户授权不在本次范围。

## 2. 架构选择

采用后端代理式 Cloudreve 适配器：浏览器沿用现有 `PROXY` 上传流程，博客后端以流式方式调用 Cloudreve 文件 API。公开图片和附件也由博客稳定地址流式读取。

选择该方案的原因：

- OAuth Token 不暴露给浏览器；
- 不需要前端理解 Cloudreve 针对不同存储策略返回的分片或预签名协议；
- Cloudreve 背后的 R2、其他存储策略及临时下载地址不会进入业务数据；
- 可以复用现有媒体状态机、内容校验、删除重试和稳定媒体 ID；
- 后续切换 Cloudreve 域名、端口或底层存储无需重写 Markdown。

浏览器直传 Cloudreve 和 WebDAV 接入暂不实现。浏览器直传可作为未来性能优化，但必须先抽象 Cloudreve 多策略上传协议和一次性上传凭据。

## 3. 配置模型

所有网络位置均来自配置，代码不得硬编码 IP 或端口：

```yaml
blog:
  media:
    provider: ${BLOG_MEDIA_PROVIDER:local}
    cloudreve:
      enabled: ${BLOG_MEDIA_CLOUDREVE_ENABLED:false}
      base-url: ${BLOG_MEDIA_CLOUDREVE_BASE_URL:}
      authorization-uri: ${BLOG_MEDIA_CLOUDREVE_AUTHORIZATION_URI:}
      token-uri: ${BLOG_MEDIA_CLOUDREVE_TOKEN_URI:}
      refresh-uri: ${BLOG_MEDIA_CLOUDREVE_REFRESH_URI:}
      userinfo-uri: ${BLOG_MEDIA_CLOUDREVE_USERINFO_URI:}
      redirect-uri: ${BLOG_MEDIA_CLOUDREVE_REDIRECT_URI:}
      client-id: ${BLOG_MEDIA_CLOUDREVE_CLIENT_ID}
      client-secret: ${BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET}
      root-path: ${BLOG_MEDIA_CLOUDREVE_ROOT_PATH:/blog}
      token-encryption-key: ${BLOG_MEDIA_TOKEN_ENCRYPTION_KEY}
```

`base-url` 为端点提供默认来源，其余 URI 可独立覆盖，以支持变更域名、端口和反向代理路径。生产配置只通过主机 `.env`、容器 Secret 或密钥管理系统注入 API 容器。前端构建、Nginx 容器、示例配置和日志不得包含 Client Secret、Token 或加密密钥。

启动校验要求：

- 默认提供方为 Cloudreve 或 Cloudreve 读取能力启用时，必须配置 HTTPS，或显式允许受信任内网 HTTP；
- OAuth URI 与回调 URI 必须为绝对 URI，禁止凭据、fragment 和通配符；
- 根目录必须是规范化绝对逻辑路径，不允许 `..`、控制字符或反斜杠；
- 加密密钥必须满足规定长度，不能与 Client Secret 相同；
- Local/R2 模式不应被空 Cloudreve 配置阻断。

## 4. OAuth 授权与令牌安全

后台媒体设置页提供“连接 Cloudreve”“重新授权”和“断开连接”。只有已认证管理员可以发起操作。

授权流程：

1. 后端生成一次性随机 `state`、PKCE verifier/challenge 和到期时间；
2. 将授权事务保存在服务端，浏览器只接收授权跳转地址；
3. Cloudreve 回调后校验 state、管理员会话、过期时间和一次性消费状态；
4. 后端用授权码、PKCE verifier、Client ID 和 Client Secret 换取 Token；
5. 调用 userinfo 确认账号并保存连接状态；
6. 回调成功后跳回后台媒体设置页，不在 URL 或页面中展示 Token。

使用专用表保存单一管理员连接：授权用户标识、显示名称、加密后的 Access Token、Refresh Token、各自过期时间、scope、连接状态和版本。Token 使用 AES-GCM 加密，每次写入使用随机 nonce，并把记录 ID/Token 类型作为附加认证数据。密文、nonce 和版本可入库，明文不得记录。

Access Token 临近过期时自动刷新。刷新使用数据库行锁或等价单航班机制，避免并发请求重复使用一次性 Refresh Token。刷新成功时原子替换 Token 对；刷新被拒绝或 Refresh Token 过期时将连接标记为 `REAUTH_REQUIRED`，Cloudreve 操作返回通用 503，并提示管理员重新授权。

断开连接会删除/失效本地 Token，但不会自动删除 Cloudreve 文件或历史媒体记录。历史 Cloudreve 媒体在重新授权前暂不可读，重新连接同一文件空间后恢复。

## 5. 存储位置与适配器

新增 `StorageProvider.CLOUDREVE` 和 `CloudreveObjectStorage`，实现现有 `ObjectStorage` 契约。适配器使用完整 `ObjectLocation(provider, bucket, objectKey)`：

- `provider`：`CLOUDREVE`；
- `bucket`：Cloudreve 文件空间/根目录配置的稳定标识；
- `objectKey`：博客生成的根目录相对路径；
- Cloudreve 返回的文件实体 ID作为可选提供方元数据保存，不能代替稳定相对路径的唯一约束。

示例逻辑路径：

```text
cloudreve://my/blog/inline-images/2026/08/<uuid>.png
cloudreve://my/blog/attachments/2026/08/<uuid>.pdf
```

文件名和路径只由后端生成。客户端不能指定根目录、文件空间、父目录或对象 key。路径必须规范化并验证仍位于配置根目录下。

Cloudreve 适配器使用 OAuth Bearer Token 调用文件 API，实现：

- 创建目录（幂等、按需）；
- 创建上传会话、流式分片上传、完成上传；
- 查询文件元数据以执行权威大小和类型验证；
- 打开文件内容流；
- 删除文件（幂等处理不存在）；
- 将 Cloudreve 401/403、404、409、429 和 5xx/网络异常映射为提供方中立异常。

Cloudreve 采用 `PROXY` 能力，博客后端不得把最大 50 MiB 文件整体缓冲到内存。分片大小以 Cloudreve 返回值为准，并设置请求超时、最大响应大小和连接池限制。

## 6. 媒体生命周期与数据流

上传继续复用现有媒体状态机：

1. 管理员申请上传计划，后端创建 `PENDING_UPLOAD` 媒体记录；
2. 浏览器把内容流式提交到博客代理上传端点；
3. 后端以 `UPLOADING` claim 防止重复上传和清理竞态；
4. Cloudreve 上传完成后，浏览器调用 complete；
5. 后端以 `VERIFYING` claim 查询 Cloudreve 元数据并读取内容执行权威校验；
6. 成功后进入 `READY`；内容不合法进入可恢复清理流程；Cloudreve 临时故障释放 claim 并返回 503；
7. 删除使用既有 `DELETING -> DELETED` 两阶段状态，由后台任务重试失败操作。

Cloudreve 文件上传成功但数据库更新失败时，记录必须保持可恢复状态，不能产生不可追踪的永久文件。所有数据库状态提交使用短事务，Cloudreve 网络 I/O 不跨数据库事务。

## 7. 公开访问与缓存

业务只保存媒体 ID或稳定地址：

- 图片：`/api/media/assets/{mediaId}`；
- 附件：`/api/media/assets/{mediaId}/download`。

第一版 Cloudreve 图片和附件均由博客后端代理读取。数据库只在短事务中复制 READY 媒体位置和安全元数据，随后关闭事务，再请求 Cloudreve 并流式响应。访客不需要博客账户或 Cloudreve Token。

响应要求：

- 仅 READY 媒体可访问；
- 附件最终响应设置安全的 UTF-8 `Content-Disposition: attachment`；
- 图片设置正确 Content-Type、长度和合理缓存头；
- Cloudreve 文件不存在返回通用 404；未授权、限流、网络或服务异常返回通用 503；
- 公开 Problem Details 不包含 Token、Cloudreve 文件 ID、逻辑路径、内部 URL或响应正文；详细信息只进入脱敏服务端日志。

未来可在 Cloudreve 提供稳定、可撤销且不泄露凭据的 CDN URL 后新增重定向能力，但不能把临时 URL持久化到 Markdown。

## 8. 后台体验

媒体设置页显示：

- Cloudreve 是否已配置；
- `DISCONNECTED`、`CONNECTED`、`REFRESHING`、`REAUTH_REQUIRED` 状态；
- 已授权用户的非敏感名称/ID；
- scope 与 Token 到期时间；
- 配置根目录；
- 连接、重新授权、断开连接操作。

媒体库继续显示 provider、用途、状态、引用状态和删除权限。Cloudreve 不可用时历史媒体仍保留，页面显示可重试错误，不把记录误标为已删除。

## 9. 现有发布阻断项

Cloudreve 接入前必须一并关闭当前最终审查中的四项问题：

1. 调整 V9 唯一索引，使 MySQL/InnoDB `utf8mb4` 索引键稳定小于 3072 字节，并增加真实 MySQL/Flyway 门禁；
2. 防止 R2 预签名 PUT在媒体 READY 后重放覆盖，采用只创建条件或上传到临时 key 后服务端转正；
3. 工具 Markdown 正文同步媒体引用，删除保护覆盖工具正文图片；
4. 图片/封面稳定地址将适配器缺失、历史 bucket 未配置等服务端错误统一映射为脱敏 404/503。

Cloudreve 复用相同媒体表、引用检查和稳定访问入口，因此这些问题是接入前置条件，不作为独立延期项。

## 10. 测试与验收

自动测试包括：

- OAuth state、PKCE、回调重放、Token 加解密、刷新并发和重新授权；
- 使用模拟 Cloudreve HTTP 服务验证上传会话、分片、完成、读取、删除及错误映射；
- 大文件流式传输和大小边界，不允许全量内存缓冲；
- 上传/complete/清理/删除的事务与故障恢复；
- Cloudreve、Local、R2 同时读取以及默认提供方切换；
- 管理员权限、CSRF、公开匿名访问和敏感信息扫描；
- 工具 Markdown 引用保护、V9 MySQL 索引与 R2 防覆盖回归；
- 前端连接状态、授权跳转、重新授权和媒体上传错误体验；
- Compose、环境变量、Nginx 限制和文档契约。

真实验收使用非生产凭据和当前 Cloudreve 实例执行：

1. 后台发起 OAuth 并完成回调；
2. 上传文章图片、头像/封面和各类公开附件；
3. 发布后以未登录窗口验证图片和附件；
4. 验证 Token 刷新、临时断网恢复和重新授权；
5. 删除未引用媒体并确认 Cloudreve 文件消失；
6. 切回 Local 默认上传后确认历史 Cloudreve 媒体仍可读取。

如 Cloudreve 实例要求 `Files.Read` 或其他 scope，真实联调前在 OAuth 应用中补充最小必要权限，不扩大到管理权限。

## 11. 运维与密钥轮换

当前对话中使用的 Client Secret 只用于开发测试。正式上线前必须在 Cloudreve 轮换，并将新值放入部署密钥系统。Token 加密密钥也必须使用独立随机值，并纳入备份恢复手册；丢失该密钥时只能重新授权，不能恢复旧 Token 明文。

日志、截图、CI、错误响应、数据库导出示例和 `.env.example` 均不得包含真实 Client Secret、Access Token、Refresh Token 或加密密钥。
