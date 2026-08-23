import { inject, readonly, shallowRef } from 'vue'
import type { InjectionKey, Ref } from 'vue'

import type { SiteProfileResponse } from '../../shared/api/contracts'

export const DEFAULT_PUBLIC_PROFILE: SiteProfileResponse = {
  siteTitle: '小M的思与行',
  subtitle: '中庸之道',
  nickname: '小M',
  bio: '中庸之道',
  avatarUrl: '/images/xiao-m-mark.png',
  githubUrl: 'https://github.com/meng-zw'
}

let latestSharedProfile: SiteProfileResponse = DEFAULT_PUBLIC_PROFILE

export function updateSharedPublicProfile(profile: SiteProfileResponse): void {
  latestSharedProfile = profile
}

export function readSharedPublicProfile(): SiteProfileResponse {
  return latestSharedProfile
}

export function resetSharedPublicProfile(): void {
  latestSharedProfile = DEFAULT_PUBLIC_PROFILE
}

export interface PublicProfileContext {
  profile: Readonly<Ref<SiteProfileResponse>>
  update(profile: SiteProfileResponse): void
}

export const publicProfileKey: InjectionKey<PublicProfileContext> = Symbol('public-profile')

export function createPublicProfileContext(): PublicProfileContext {
  const profile = shallowRef<SiteProfileResponse>(latestSharedProfile)
  return {
    profile: readonly(profile),
    update(nextProfile) {
      profile.value = nextProfile
      latestSharedProfile = nextProfile
    }
  }
}

const fallbackContext: PublicProfileContext = {
  profile: readonly(shallowRef(DEFAULT_PUBLIC_PROFILE)),
  update() {}
}

export function usePublicProfile(): PublicProfileContext {
  return inject(publicProfileKey, fallbackContext)
}
