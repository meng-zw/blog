import type { ArticleDetailResponse, ArticleSummaryResponse, ContentType, PageResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

type Wire = Record<string, any>

function attachment(v: Wire) {
  return { mediaId: v.media_id ?? v.mediaId, displayName: v.display_name ?? v.displayName, contentType: v.content_type ?? v.contentType, byteSize: v.byte_size ?? v.byteSize, downloadUrl: v.download_url ?? v.downloadUrl }
}

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

export async function loadArticle(slug: string, signal?: AbortSignal): Promise<ArticleDetailResponse> {
  const response = await http.get<Wire>(`/public/articles/${encodeURIComponent(slug)}`, { signal })
  return { ...response, attachments: (response.attachments ?? []).map(attachment) } as ArticleDetailResponse
}
