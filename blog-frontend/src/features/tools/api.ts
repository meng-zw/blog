import type { PageResponse, ToolDetailResponse, ToolSummaryResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export interface ToolListParams {
  page: number
  size: number
  category?: string
  tag?: string
  q?: string
}

export function listTools(params: ToolListParams, signal?: AbortSignal): Promise<PageResponse<ToolSummaryResponse>> {
  return http.get<PageResponse<ToolSummaryResponse>>('/public/tools', {
    query: { page: params.page, size: params.size, category: params.category, tag: params.tag, q: params.q },
    signal
  })
}

export function loadTool(slug: string, signal?: AbortSignal): Promise<ToolDetailResponse> {
  return http.get<ToolDetailResponse>(`/public/tools/${encodeURIComponent(slug)}`, { signal })
}
