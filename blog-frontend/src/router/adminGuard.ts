import type { NavigationGuardReturn, RouteLocationRaw } from 'vue-router'

import type { useSessionStore } from '../features/session/store'

type SessionStore = ReturnType<typeof useSessionStore>

interface GuardTarget {
  name?: unknown
  fullPath: string
  query?: Readonly<Record<string, unknown>>
  meta: Readonly<Record<string, unknown>>
}

const ENCODED_PATH_ESCAPE = /%(?:2f|5c|2e)/i

export function safeAdminRedirect(value: unknown, origin = window.location.origin): string | null {
  if (typeof value !== 'string' || !value || value !== value.trim()) return null
  const pathPart = value.split(/[?#]/, 1)[0] ?? ''
  if (!pathPart.startsWith('/') || pathPart.startsWith('//') || pathPart.includes('\\')) return null
  if (ENCODED_PATH_ESCAPE.test(pathPart)) return null

  let target: URL
  try {
    target = new URL(value, origin)
  } catch {
    return null
  }
  if (target.origin !== origin) return null
  if (!(target.pathname === '/admin' || target.pathname.startsWith('/admin/'))) return null
  if (target.pathname === '/admin/login' || target.pathname.startsWith('/admin/login/')) return null
  return `${target.pathname}${target.search}${target.hash}`
}

export function createAdminGuard(store: SessionStore, origin = window.location.origin) {
  return async (to: GuardTarget): Promise<NavigationGuardReturn> => {
    const loginRoute = to.name === 'admin-login'
    if (!loginRoute && to.meta.requiresAdmin !== true) return true

    try {
      await store.restore()
    } catch {
      return true
    }

    if (loginRoute) {
      if (!store.isAuthenticated) return true
      return safeAdminRedirect(to.query?.redirect, origin) ?? '/admin'
    }
    if (store.isAuthenticated) return true
    return {
      name: 'admin-login',
      query: { redirect: to.fullPath },
      replace: true
    } satisfies RouteLocationRaw
  }
}
