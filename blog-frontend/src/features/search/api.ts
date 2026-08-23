import type { PageResponse, SearchResultResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export interface SearchParams {
  q: string
  page: number
  size: number
}

export function searchPublic(params: SearchParams, signal?: AbortSignal): Promise<PageResponse<SearchResultResponse>> {
  return http.get<PageResponse<SearchResultResponse>>('/public/search', {
    query: { q: params.q, page: params.page, size: params.size },
    signal
  })
}
