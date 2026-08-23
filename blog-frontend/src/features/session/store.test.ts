import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { SessionResponse } from '../../shared/api/contracts'
import { ApiProblem } from '../../shared/api/problem'
import { getSession, loginSession, logoutSession } from './api'
import { useSessionStore } from './store'

vi.mock('./api', () => ({
  getSession: vi.fn(),
  loginSession: vi.fn(),
  logoutSession: vi.fn()
}))

const authenticated: SessionResponse = {
  authenticated: true,
  username: 'admin',
  displayName: '小M'
}

const mockedGetSession = vi.mocked(getSession)
const mockedLoginSession = vi.mocked(loginSession)
const mockedLogoutSession = vi.mocked(logoutSession)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((done, fail) => {
    resolve = done
    reject = fail
  })
  return { promise, resolve, reject }
}

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockedGetSession.mockReset()
    mockedLoginSession.mockReset()
    mockedLogoutSession.mockReset()
  })

  it('restores a direct-navigation session once and exposes authenticated identity', async () => {
    mockedGetSession.mockResolvedValue(authenticated)
    const store = useSessionStore()

    await store.restore()
    await store.restore()

    expect(mockedGetSession).toHaveBeenCalledOnce()
    expect(store.state).toBe('authenticated')
    expect(store.session).toEqual(authenticated)
    expect(store.isAuthenticated).toBe(true)
  })

  it('deduplicates concurrent restoration while state is restoring', async () => {
    const pending = deferred<SessionResponse>()
    mockedGetSession.mockReturnValue(pending.promise)
    const store = useSessionStore()

    const first = store.restore()
    const second = store.restore()
    expect(store.state).toBe('restoring')
    expect(mockedGetSession).toHaveBeenCalledOnce()

    pending.resolve({ authenticated: false, username: null, displayName: null })
    await Promise.all([first, second])
    expect(store.state).toBe('anonymous')
  })

  it('keeps concurrent expired restoration anonymous when the 401 hook runs before rejection', async () => {
    const pending = deferred<SessionResponse>()
    const problem = new ApiProblem({
      title: 'Unauthorized', status: 401, detail: '会话已失效', traceId: 'trace-expired'
    })
    mockedGetSession.mockReturnValue(pending.promise)
    const store = useSessionStore()

    const first = store.restore()
    const second = store.restore()
    store.handleUnauthorized(problem)
    pending.reject(problem)

    await expect(first).resolves.toEqual({ authenticated: false, username: null, displayName: null })
    await expect(second).resolves.toEqual({ authenticated: false, username: null, displayName: null })
    expect(mockedGetSession).toHaveBeenCalledOnce()
    expect(store.state).toBe('anonymous')
    expect(store.session).toBeNull()
  })

  it('keeps credentials out of browser storage and clears login request ownership', async () => {
    const localSet = vi.spyOn(Storage.prototype, 'setItem')
    mockedLoginSession.mockResolvedValue(authenticated)
    const store = useSessionStore()

    await store.login({ username: 'admin', password: 'private-password' })

    expect(store.session).toEqual(authenticated)
    expect(JSON.stringify(store.$state)).not.toContain('private-password')
    expect(localSet).not.toHaveBeenCalled()
  })

  it('marks the in-memory session anonymous after a controlled later 401', () => {
    const store = useSessionStore()
    store.accept(authenticated)

    store.handleUnauthorized(new ApiProblem({
      title: 'Unauthorized', status: 401, detail: '会话已失效', traceId: 'trace-401'
    }))

    expect(store.state).toBe('anonymous')
    expect(store.session).toBeNull()
    expect(window.location.pathname).not.toBe('/admin/login')
  })

  it('clears stale identity even when logout fails', async () => {
    mockedLogoutSession.mockRejectedValue(new Error('offline'))
    const store = useSessionStore()
    store.accept(authenticated)

    await expect(store.logout()).rejects.toThrow('offline')
    expect(store.state).toBe('anonymous')
    expect(store.session).toBeNull()
    expect(store.logoutNotice).toContain('退出请求未能送达')
  })
})
