import type { HomeResponse, SiteProfileResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export function loadHome(signal?: AbortSignal): Promise<HomeResponse> {
  return http.get<HomeResponse>('/public/home', { signal })
}

export function loadSiteProfile(signal?: AbortSignal): Promise<SiteProfileResponse> {
  return http.get<SiteProfileResponse>('/public/site-profile', { signal })
}
