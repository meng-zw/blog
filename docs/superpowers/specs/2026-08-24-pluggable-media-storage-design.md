# 可插拔媒体存储设计

## 1. 背景与目标

博客当前由 `MediaStorageService` 将后台上传的图片写入本地目录，并通过 `/api/media/{storageKey}` 返回文件。Markdown 编辑器尚未接入该上传流程，底层存储、媒体业务与文件系统访问耦合，无法低成本切换到 Cloudflare R2、阿里云 OSS 等服务。

本次改造将媒体能力整理为后端内的独立模块。第一版实现本地文件系统和 Cloudflare R2 两种适配器，支持 Markdown 图片、头像、各类封面和公开文章附件。业务模块只保存媒体 ID 或稳定媒体地址，不保存供应商 URL。R2 作为公开图床，浏览器通过后端签发的预签名 URL 直接上传正式对象。

第一版不实现临时桶与“临时转正”复制。上传完成但未被内容引用的媒体保留在媒体库并标记为未使用，不自动删除。该取舍降低第一版的事务和恢复复杂度，同时保留未来加入临时对象生命周期的扩展空间。

## 2. 范围

### 2.1 包含

- 后端内独立媒体模块及存储能力抽象。
- `LocalObjectStorage` 与 `R2ObjectStorage`。
- 支持直传与后端代理两类上传模式的统一契约。
- Markdown 粘贴、拖拽和选择图片上传。
- 头像、文章封面、专题封面和工具封面统一接入媒体模块。
- 公开文章附件上传、排序、展示和下载。
- 稳定媒体地址、媒体引用关系、后台媒体库和安全删除。
- 旧本地媒体和旧 `/api/media/{storageKey}` 地址兼容。
- Docker、环境变量、部署及 R2 CORS 文档。

### 2.2 不包含

- 临时桶、对象转正复制和七天生命周期清理。
- 私有附件或访客级访问控制。
- GitHub、Gitee、OSS、COS 的具体适配器。
- 视频转码、病毒扫描、内容审核和多租户。
- Cloudflare Images 或实时多规格图片变换。
- 自动删除所有未引用的永久媒体。

## 3. 架构原则

1. 文章、站点、专题和工具模块只依赖媒体应用服务及媒体 ID，不依赖 R2 SDK、bucket、endpoint 或对象 key。
2. Markdown 持久化稳定地址 `/api/media/assets/{mediaId}`，不持久化 `r2.dev`、Cloudflare 自定义域或其他供应商 URL。
3. 媒体模块拥有媒体状态、上传会话、对象验证、公开地址解析、引用同步和删除规则。
4. 存储适配器通过能力声明支持不同上传方式。R2 使用浏览器直传；Local 及未来 GitHub/Gitee 可通过后端代理上传。
5. 原有本地媒体可原地兼容并在以后迁移，迁移时保持媒体 ID 不变。
6. 对外错误使用现有 Problem Details 规范；complete、删除和引用同步保持幂等。

## 4. 模块边界

建议结构：

```text
com.blog.media
├── api
│   ├── AdminMediaController
│   └── PublicMediaController
├── application
│   ├── MediaApplicationService
│   ├── MediaReferenceService
│   └── MediaCleanupService
├── domain
│   ├── MediaAsset
│   ├── MediaPurpose
│   ├── MediaStatus
│   ├── StorageProvider
│   └── repository
└── infrastructure
    └── storage
        ├── ObjectStorage
        ├── LocalObjectStorage
        └── r2
            ├── R2ObjectStorage
            └── R2Configuration
```

`ObjectStorage` 提供以下能力：

```java
public interface ObjectStorage {
    StorageCapabilities capabilities();
    UploadTicket createDirectUpload(ObjectUploadRequest request);
    StoredObject upload(ObjectUploadRequest request, InputStream content);
    StoredObject inspect(String objectKey);
    URI resolvePublicUrl(String objectKey);
    void delete(String objectKey);
}
```

`StorageCapabilities` 至少声明 `directUpload` 与 `publicRead`。应用服务根据能力返回 `DIRECT` 或 `PROXY` 上传计划，使前端和业务模块不需要识别具体供应商。

## 5. 数据模型

通过新的 Flyway 迁移扩展现有 `media_asset`：

| 字段 | 含义 |
| --- | --- |
| `provider` | `LOCAL`、`R2`，未来可增加其他提供商 |
| `bucket` | 对象桶；本地存储为空 |
| `storage_key` | 供应商内部对象 key，不是公开 URL |
| `status` | `PENDING_UPLOAD`、`READY`、`FAILED`、`ABANDONED`、`DELETED` |
| `purpose` | `AVATAR`、各类 `COVER`、`INLINE_IMAGE`、`ATTACHMENT` |
| `original_filename` | 原始展示名称 |
| `content_type` | 后端确认后的 MIME |
| `byte_size` | 后端确认后的实际大小 |
| `width` / `height` | 图片尺寸，附件为空 |
| `etag` | 存储端对象标识，用于确认与诊断 |
| `uploaded_by_id` | 上传管理员 |
| `created_at` / `updated_at` | 生命周期时间 |
| `confirmed_at` | 对象确认完成时间 |

唯一约束调整为 `(provider, bucket, storage_key)`。迁移将现有记录设置为 `provider=LOCAL`、`status=READY`，保留原 storage key 和媒体 ID。

新增 `article_media`：

| 字段 | 含义 |
| --- | --- |
| `article_id` | 文章 ID |
| `media_id` | 媒体 ID |
| `role` | `INLINE` 或 `ATTACHMENT` |
| `display_name` | 附件展示名；内嵌图片可为空 |
| `sort_order` | 附件顺序 |
| `created_at` | 建立引用时间 |

文章封面、头像、专题封面及工具封面继续使用现有媒体外键。文章保存时解析 Markdown 的稳定媒体地址并同步 `INLINE` 引用；请求中的附件列表同步 `ATTACHMENT` 引用。所有媒体必须为 `READY`，否则拒绝保存。

## 6. 上传与访问协议

### 6.1 申请上传

`POST /api/admin/media/uploads`

请求包含 `filename`、`contentType`、`byteSize` 和 `purpose`。后端完成身份、CSRF、速率、类型、大小和文件名校验，生成媒体记录及不可预测的 object key。

R2 返回：

```json
{
  "mediaId": 123,
  "uploadMode": "DIRECT",
  "method": "PUT",
  "uploadUrl": "https://...r2.cloudflarestorage.com/...",
  "headers": { "Content-Type": "image/png" },
  "expiresAt": "2026-08-24T10:15:00Z"
}
```

Local 或未来不支持预签名上传的适配器返回：

```json
{
  "mediaId": 123,
  "uploadMode": "PROXY",
  "method": "PUT",
  "uploadUrl": "/api/admin/media/uploads/123/content",
  "headers": { "Content-Type": "image/png" },
  "expiresAt": "2026-08-24T10:15:00Z"
}
```

预签名地址默认有效 10 分钟。前端严格使用响应中的 method 和 headers。

### 6.2 完成上传

`POST /api/admin/media/{mediaId}/complete`

后端调用 `inspect` 验证对象存在、实际大小、Content-Type 和 ETag。图片还需验证文件签名、可解码性和尺寸。验证成功后状态变为 `READY`；失败时删除对象并标记 `FAILED`。重复 complete 对 READY 媒体返回相同结果。

### 6.3 稳定访问

- 新入口：`GET /api/media/assets/{mediaId}`。
- 公开附件：`GET /api/media/assets/{mediaId}/download`。
- 旧入口：`GET /api/media/{storageKey}`，只用于兼容已有本地地址。

新入口仅允许访问 READY 媒体，通过对应适配器解析当前公开地址并返回 302。图片和附件对象设置合适的 `Content-Type`、`Cache-Control`；附件设置安全的 `Content-Disposition`。公开域名变化或供应商迁移不会改变 Markdown。

## 7. 前端交互

Vditor 使用自定义 `upload.handler`：申请上传、按上传模式发送文件、调用 complete，并在成功后插入：

```markdown
![图片说明](/api/media/assets/123)
```

上传期间显示进度并禁止重复提交；失败时不修改 Markdown，显示可理解的中文错误。粘贴、拖拽和文件选择共用同一流程。

文章编辑页增加附件区域，支持上传、显示名称和大小、移除及排序。文章写请求增加结构化附件列表；文章详情页为普通访客显示公开下载入口。

头像和各类封面复用相同客户端 API，以 `purpose` 区分规则，不关心当前 provider。

## 8. 安全与校验

- R2 密钥只存在于后端环境变量，Token 仅授权指定桶。
- 申请上传必须通过管理员会话、CSRF 和限流。
- 后端生成 object key，客户端不能选择 bucket 或 key。
- 预签名 PUT 固定 Content-Type；complete 再验证真实对象。
- 图片第一版允许 PNG、JPEG、GIF，默认最大 5 MiB；验证魔数并实际解码。
- 附件第一版允许 PDF、ZIP、TXT、DOCX、XLSX、PPTX；普通附件默认 20 MiB，ZIP 默认 50 MiB。
- 文件大小、尺寸和允许类型均由后端配置，前端提示仅用于体验。
- 文件名只作显示元数据，不能用于对象路径；下载文件名必须安全编码。
- 禁止 HTML、JavaScript、SVG 及其他可能产生主动内容的附件。
- 公开媒体不承载秘密或个人敏感文件。

## 9. 失败恢复与删除

- `PENDING_UPLOAD` 超过 24 小时变为 `ABANDONED`，后台任务删除可确认存在的对象。
- 验证失败进入 `FAILED`，删除失败则记录错误并由后台任务重试。
- `READY` 但没有引用的媒体显示为“未使用”，第一版不自动删除。
- 管理员只能删除没有任何文章、头像或封面引用的 READY 媒体。
- 删除流程先标记删除中并执行对象删除，成功后置为 `DELETED`；重复请求幂等。
- 文章删除只移除引用，不自动删除媒体，避免共享资源和历史内容断链。

## 10. 配置与部署

本地：

```dotenv
BLOG_MEDIA_PROVIDER=local
BLOG_MEDIA_LOCAL_DIRECTORY=./media
```

R2：

```dotenv
BLOG_MEDIA_PROVIDER=r2
BLOG_MEDIA_R2_ACCOUNT_ID=
BLOG_MEDIA_R2_ACCESS_KEY_ID=
BLOG_MEDIA_R2_SECRET_ACCESS_KEY=
BLOG_MEDIA_R2_BUCKET=xiaom-blog-media
BLOG_MEDIA_R2_PUBLIC_BASE_URL=https://img.example.com
BLOG_MEDIA_R2_REGION=auto
BLOG_MEDIA_R2_UPLOAD_URL_TTL=10m
```

`.env.example` 只包含空值或占位符，Compose 将变量传给 API，不包含真实凭证。R2 bucket 配置仅允许博客生产域名和本地开发地址执行 PUT/HEAD 所需的 CORS；公开读取通过自定义域，`r2.dev` 只用于开发。

## 11. 迁移与供应商切换

旧媒体保持 LOCAL/READY 并由兼容路由继续访问。迁移任务可逐个读取本地媒体、写入 R2、验证大小及校验信息，再原子更新同一 `media_asset` 的 provider、bucket 和 storage key。媒体 ID 与新稳定地址不变。

切换到 OSS 等存储时新增适配器和配置，复制对象后更新媒体存储定位；文章和 Markdown 无需修改。GitHub/Gitee 可通过 `PROXY` 模式实现，但因仓库体积、提交冲突、带宽和访问稳定性限制，不作为第一版或推荐生产图床。

## 12. 测试与验收

- 存储契约测试覆盖 Local 与 R2 适配器的上传计划、inspect、URL 解析和删除。
- 应用服务测试覆盖状态机、幂等 complete、上传校验、引用同步和安全删除。
- Controller 测试覆盖管理员鉴权、CSRF、公开访问及 Problem Details。
- Markdown 测试覆盖粘贴、拖拽、上传进度、失败恢复和稳定地址插入。
- 文章测试覆盖内嵌图片引用与附件列表同步。
- Flyway/MySQL 集成测试覆盖旧媒体迁移和约束。
- R2 集成测试使用专用测试桶或兼容 S3 的本地测试服务，不在普通单元测试中依赖生产凭证。
- 上线验收包括头像、各类封面、Markdown 图片、公开附件、未引用媒体、旧本地媒体和 provider 切换回退。

## 13. 实施顺序

1. 数据迁移、领域模型和存储接口。
2. Local 适配器及旧路径兼容，确保现有功能不回退。
3. 统一媒体应用 API、状态机和引用关系。
4. 前端通用上传客户端、Vditor 和附件区域。
5. R2 适配器、配置、CORS 与部署文档。
6. 媒体管理、失败清理、迁移验证和完整回归。
