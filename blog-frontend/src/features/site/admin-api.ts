import type { AdminSiteProfileResponse, UpdateSiteProfileRequest } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

interface SiteProfileWireResponse {
  site_title?: string
  siteTitle?: string
  subtitle: string
  nickname: string
  bio: string
  avatar_url?: string
  avatarUrl?: string
  avatar_media_id?: number | null
  avatarMediaId?: number | null
  github_url?: string
  githubUrl?: string
}

function profileFromWire(response: SiteProfileWireResponse): AdminSiteProfileResponse {
  return {
    siteTitle: response.site_title ?? response.siteTitle ?? '',
    subtitle: response.subtitle,
    nickname: response.nickname,
    bio: response.bio,
    avatarUrl: response.avatar_url ?? response.avatarUrl ?? '',
    avatarMediaId: response.avatar_media_id ?? response.avatarMediaId ?? null,
    githubUrl: response.github_url ?? response.githubUrl ?? ''
  }
}

export async function loadAdminSettings(): Promise<AdminSiteProfileResponse> {
  return profileFromWire(await http.get<SiteProfileWireResponse>('/admin/settings'))
}

export async function updateAdminSettings(request: UpdateSiteProfileRequest): Promise<AdminSiteProfileResponse> {
  const response = await http.put<SiteProfileWireResponse>('/admin/settings', {
    site_title: request.siteTitle,
    subtitle: request.subtitle,
    nickname: request.nickname,
    bio: request.bio,
    avatar_media_id: request.avatarMediaId,
    github_url: request.githubUrl
  })
  return profileFromWire(response)
}
