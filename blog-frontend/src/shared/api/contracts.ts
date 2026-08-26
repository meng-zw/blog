export type IsoDateTime = string
export type ContentType = 'ARTICLE' | 'NOTE'
export type ArticleStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'ARCHIVED'
export type TopicStatus = 'DRAFT' | 'PUBLISHED'
export type ToolStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type CategoryScope = 'ARTICLE' | 'TOOL'
export type SearchResultType = 'ARTICLE' | 'NOTE' | 'TOPIC' | 'TOOL'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface SessionResponse {
  authenticated: boolean
  username: string | null
  displayName: string | null
}

export interface LoginRequest {
  username: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmation: string
}

export interface SiteProfileResponse {
  siteTitle: string
  subtitle: string
  nickname: string
  bio: string
  avatarUrl: string
  githubUrl: string
}

export interface AdminSiteProfileResponse extends SiteProfileResponse {
  avatarMediaId: number | null
}

export interface UpdateSiteProfileRequest {
  siteTitle: string
  subtitle: string
  nickname: string
  bio: string
  avatarMediaId: number | null
  githubUrl: string
}

export type MediaPurpose = 'AVATAR' | 'ARTICLE_COVER' | 'TOPIC_COVER' | 'TOOL_COVER' | 'INLINE_IMAGE' | 'ATTACHMENT'
export type MediaUploadMode = 'DIRECT' | 'PROXY'
export type MediaStatus = 'PENDING_UPLOAD' | 'READY' | 'DELETING' | 'FAILED' | 'ABANDONED' | 'DELETED'
export type CloudreveConnectionStatus = 'DISCONNECTED' | 'CONNECTED' | 'REFRESHING' | 'REAUTH_REQUIRED'

export interface CloudreveConnectionResponse {
  configured: boolean
  status: CloudreveConnectionStatus
  authorizedSubject: string | null
  authorizedDisplayName: string | null
  grantedScopes: string[]
  accessTokenExpiresAt: IsoDateTime | null
  refreshTokenExpiresAt: IsoDateTime | null
  rootPath: string
}

export interface MediaUploadPlanResponse {
  mediaId: number
  uploadMode: MediaUploadMode
  method: string
  uploadUrl: string
  headers: Record<string, string>
  expiresAt: IsoDateTime
}

export interface MediaAssetResponse {
  mediaId: number
  filename: string
  contentType: string
  byteSize: number
  width: number | null
  height: number | null
  status: MediaStatus
  purpose: MediaPurpose
  url: string
}

export interface AdminMediaAssetResponse extends MediaAssetResponse {
  provider: 'LOCAL' | 'R2' | 'CLOUDREVE'
  referenced: boolean
  canDelete: boolean
  createdAt: IsoDateTime
}

export interface CategoryResponse {
  id: number
  name: string
  slug: string
  description: string | null
  sortOrder: number
  scope: CategoryScope
}

export interface CategoryRequest {
  name: string
  description: string | null
  sortOrder: number
  scope: CategoryScope
}

export interface TagResponse {
  id: number
  name: string
  slug: string
}

export interface TagRequest {
  name: string
}

export interface PublicCategoryResponse {
  id: number
  name: string
  slug: string
}

export interface PublicTagResponse {
  id: number
  name: string
  slug: string
}

export interface PublicTopicResponse {
  id: number
  name: string
  slug: string
}

export interface TopicResponse {
  id: number
  name: string
  slug: string
  description: string | null
  coverUrl: string | null
  status: TopicStatus
  sortOrder: number
}

export interface PublicTopicSummaryResponse {
  id: number
  name: string
  slug: string
  description: string | null
  coverUrl: string | null
}

export interface TopicDetailResponse extends PublicTopicSummaryResponse {
  articles: ArticleSummaryResponse[]
}

export interface TopicWriteRequest {
  name: string
  description: string | null
  coverMediaId: number | null
  status: TopicStatus
  articleIds: number[]
  sortOrder: number
}

export interface ArticleSummaryResponse {
  id: number
  slug: string
  title: string
  summary: string
  contentType: ContentType
  publishedAt: IsoDateTime
  coverUrl: string | null
  category: PublicCategoryResponse | null
  tags: PublicTagResponse[]
}

export interface ArticleDetailResponse extends ArticleSummaryResponse {
  topic: PublicTopicResponse | null
  renderedHtml: string
  seoTitle: string | null
  seoDescription: string | null
  previous: ArticleSummaryResponse | null
  next: ArticleSummaryResponse | null
  attachments: ArticleAttachmentResponse[]
}

export interface ArticleAttachmentResponse {
  mediaId: number
  displayName: string
  contentType: string
  byteSize: number
  downloadUrl: string
}

export interface ArticleWriteRequest {
  title: string
  slug: string
  summary: string
  markdownContent: string
  contentType: ContentType
  coverMediaId: number | null
  categoryId: number | null
  topicId: number | null
  tagIds: number[]
  seoTitle: string | null
  seoDescription: string | null
  attachmentMediaIds: number[]
}

export interface ScheduleArticleRequest {
  scheduledAt: IsoDateTime
}

export interface AdminArticleSummaryResponse {
  id: number
  slug: string
  title: string
  summary: string
  contentType: ContentType
  status: ArticleStatus
  publishedAt: IsoDateTime | null
  scheduledAt: IsoDateTime | null
  coverUrl: string | null
  category: CategoryResponse | null
  tags: TagResponse[]
}

export interface AdminArticleResponse extends AdminArticleSummaryResponse {
  coverMediaId: number | null
  markdownContent: string
  renderedHtml: string
  topic: TopicResponse | null
  seoTitle: string | null
  seoDescription: string | null
  attachments: ArticleAttachmentResponse[]
}

export interface ToolSummaryResponse {
  id: number
  slug: string
  name: string
  summary: string
  officialUrl: string
  coverUrl: string | null
  category: PublicCategoryResponse | null
  tags: PublicTagResponse[]
  featured: boolean
  publishedAt: IsoDateTime
}

export interface ToolDetailResponse extends ToolSummaryResponse {
  renderedHtml: string
}

export interface ToolWriteRequest {
  name: string
  slug: string
  summary: string
  descriptionMarkdown: string
  officialUrl: string
  coverMediaId: number | null
  categoryId: number | null
  tagIds: number[]
  featured: boolean
}

export interface ToolReorderRequest {
  toolIds: number[]
}

export interface AdminToolSummaryResponse {
  id: number
  slug: string
  name: string
  summary: string
  officialUrl: string
  coverUrl: string | null
  category: CategoryResponse | null
  tags: TagResponse[]
  status: ToolStatus
  featured: boolean
  sortOrder: number
  publishedAt: IsoDateTime | null
  createdAt: IsoDateTime
  updatedAt: IsoDateTime
}

export interface AdminToolResponse extends AdminToolSummaryResponse {
  coverMediaId: number | null
  descriptionMarkdown: string
  renderedHtml: string
}

export interface HomeResponse {
  site: SiteProfileResponse
  featuredArticle: ArticleSummaryResponse | null
  latestArticles: ArticleSummaryResponse[]
  featuredTools: ToolSummaryResponse[]
  topics: PublicTopicSummaryResponse[]
}

export interface SearchResultResponse {
  type: SearchResultType
  id: number
  slug: string
  title: string
  summary: string | null
  publishedAt: IsoDateTime | null
}
