import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import type { LoginRequest, SessionResponse } from '../../shared/api/contracts'
import { setUnauthorizedHandler } from '../../shared/api/http'
import { ApiProblem } from '../../shared/api/problem'
import { getSession, loginSession, logoutSession } from './api'

export type SessionState = 'unknown' | 'restoring' | 'authenticated' | 'anonymous'

export const useSessionStore = defineStore('admin-session', () => {
  const state = ref<SessionState>('unknown')
  const session = shallowRef<SessionResponse | null>(null)
  const lastError = shallowRef<unknown>(null)
  const logoutNotice = ref('')
  let restoreInFlight: Promise<SessionResponse> | null = null

  const isAuthenticated = computed(() => state.value === 'authenticated' && session.value?.authenticated === true)

  function accept(response: SessionResponse): void {
    lastError.value = null
    if (response.authenticated) {
      session.value = response
      state.value = 'authenticated'
    } else {
      session.value = null
      state.value = 'anonymous'
    }
  }

  function handleUnauthorized(_problem?: ApiProblem): void {
    session.value = null
    state.value = 'anonymous'
  }

  async function restore(): Promise<SessionResponse> {
    if (state.value === 'authenticated' && session.value) return session.value
    if (state.value === 'anonymous') return { authenticated: false, username: null, displayName: null }
    if (restoreInFlight) return restoreInFlight

    state.value = 'restoring'
    restoreInFlight = getSession()
      .then((response) => {
        accept(response)
        return response
      })
      .catch((error: unknown) => {
        lastError.value = error
        if (error instanceof ApiProblem && error.status === 401) {
          handleUnauthorized(error)
          return { authenticated: false, username: null, displayName: null }
        }
        state.value = 'unknown'
        throw error
      })
      .finally(() => {
        restoreInFlight = null
      })
    return restoreInFlight
  }

  async function login(credentials: LoginRequest): Promise<SessionResponse> {
    const response = await loginSession(credentials)
    accept(response)
    return response
  }

  async function logout(): Promise<void> {
    lastError.value = null
    logoutNotice.value = ''
    try {
      if (state.value !== 'anonymous') await logoutSession()
    } catch (error: unknown) {
      lastError.value = error
      logoutNotice.value = '退出请求未能送达，本机已清除管理身份。服务恢复后请再确认会话状态。'
      throw error
    } finally {
      session.value = null
      state.value = 'anonymous'
    }
  }

  function clearLogoutNotice(): void {
    logoutNotice.value = ''
  }

  setUnauthorizedHandler(handleUnauthorized)

  return {
    state,
    session,
    lastError,
    logoutNotice,
    isAuthenticated,
    accept,
    handleUnauthorized,
    restore,
    login,
    logout,
    clearLogoutNotice
  }
})
