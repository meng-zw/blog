import type { PageResponse, PublicTopicSummaryResponse, TopicDetailResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export interface TopicListParams {
  page: number
  size: number
}

export function listTopics(params: TopicListParams, signal?: AbortSignal): Promise<PageResponse<PublicTopicSummaryResponse>> {
  return http.get<PageResponse<PublicTopicSummaryResponse>>('/public/topics', {
    query: { page: params.page, size: params.size },
    signal
  })
}

export function loadTopic(slug: string, signal?: AbortSignal): Promise<TopicDetailResponse> {
  return http.get<TopicDetailResponse>(`/public/topics/${encodeURIComponent(slug)}`, { signal })
}
