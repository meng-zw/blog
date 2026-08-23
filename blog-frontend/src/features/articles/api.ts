import type { ArticleDetailResponse, ArticleSummaryResponse, ContentType, PageResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export interface ArticleListParams {
  page: number
  size: number
  contentType: ContentType
  category?: string
  tag?: string
  q?: string
}

export function listArticles(params: ArticleListParams, signal?: AbortSignal): Promise<PageResponse<ArticleSummaryResponse>> {
  return http.get<PageResponse<ArticleSummaryResponse>>('/public/articles', {
    query: {
      page: params.page, size: params.size, contentType: params.contentType,
      category: params.category, tag: params.tag, q: params.q
    },
    signal
  })
}

export function loadArticle(slug: string, signal?: AbortSignal): Promise<ArticleDetailResponse> {
  return http.get<ArticleDetailResponse>(`/public/articles/${encodeURIComponent(slug)}`, { signal })
}
