import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getSession } from '../features/session/api'
import { useSessionStore } from '../features/session/store'
import { ApiProblem } from '../shared/api/problem'
import { createAdminGuard, safeAdminRedirect } from './adminGuard'

vi.mock('../features/session/api', () => ({
  getSession: vi.fn(), loginSession: vi.fn(), logoutSession: vi.fn()
}))

const mockedGetSession = vi.mocked(getSession)

describe('safe admin redirects', () => {
  it.each([
    ['/admin', '/admin'],
    ['/admin/articles?status=DRAFT#editor', '/admin/articles?status=DRAFT#editor'],
    ['/admin/media?from=%E4%B8%8A%E4%BC%A0', '/admin/media?from=%E4%B8%8A%E4%BC%A0']
  ])('accepts the normalized same-origin admin target %s', (input, expected) => {
    expect(safeAdminRedirect(input, 'https://blog.example')).toBe(expected)
  })

  it.each([
    undefined, '', '/', '/articles', '/admin/login', '/admin/login?redirect=/admin',
    '//evil.example/admin', 'https://evil.example/admin', 'javascript:alert(1)',
    '/admin\\settings', '/admin/../private', '/admin/%2e%2e/private',
    '%2F%2Fevil.example/admin', '/admin%2F..%2Fprivate'
  ])('rejects unsafe redirect %s', (input) => {
    expect(safeAdminRedirect(input, 'https://blog.example')).toBeNull()
  })
})

describe('admin navigation guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockedGetSession.mockReset()
  })

  it('restores direct protected navigation and redirects an anonymous visitor with fullPath', async () => {
    mockedGetSession.mockResolvedValue({ authenticated: false, username: null, displayName: null })
    const guard = createAdminGuard(useSessionStore(), 'https://blog.example')

    const result = await guard({
      name: 'admin-articles', fullPath: '/admin/articles?status=DRAFT',
      meta: { layout: 'admin', title: '文章', requiresAdmin: true }
    })

    expect(mockedGetSession).toHaveBeenCalledOnce()
    expect(result).toEqual({ name: 'admin-login', query: { redirect: '/admin/articles?status=DRAFT' }, replace: true })
  })

  it('allows authenticated direct navigation after restore', async () => {
    mockedGetSession.mockResolvedValue({ authenticated: true, username: 'admin', displayName: '小M' })
    const guard = createAdminGuard(useSessionStore(), 'https://blog.example')

    await expect(guard({
      name: 'admin-media', fullPath: '/admin/media',
      meta: { layout: 'admin', title: '媒体', requiresAdmin: true }
    })).resolves.toBe(true)
  })

  it('does not misclassify a restore network failure as an anonymous session', async () => {
    mockedGetSession.mockRejectedValueOnce(new Error('offline'))
    const store = useSessionStore()
    const guard = createAdminGuard(store, 'https://blog.example')

    await expect(guard({
      name: 'admin-settings', fullPath: '/admin/settings',
      meta: { layout: 'admin', title: '站点设置', requiresAdmin: true }
    })).resolves.toBe(true)
    expect(store.state).toBe('unknown')
  })

  it('redirects an expired protected navigation when the 401 hook precedes restore rejection', async () => {
    const problem = new ApiProblem({
      title: 'Unauthorized', status: 401, detail: '会话已失效', traceId: 'trace-expired'
    })
    const store = useSessionStore()
    mockedGetSession.mockImplementationOnce(async () => {
      store.handleUnauthorized(problem)
      throw problem
    })
    const guard = createAdminGuard(store, 'https://blog.example')

    await expect(guard({
      name: 'admin-settings', fullPath: '/admin/settings?tab=profile#avatar',
      meta: { layout: 'admin', title: '站点设置', requiresAdmin: true }
    })).resolves.toEqual({
      name: 'admin-login',
      query: { redirect: '/admin/settings?tab=profile#avatar' },
      replace: true
    })
    expect(store.state).toBe('anonymous')
  })

  it('redirects an authenticated login visit to only a safe target', async () => {
    mockedGetSession.mockResolvedValue({ authenticated: true, username: 'admin', displayName: '小M' })
    const guard = createAdminGuard(useSessionStore(), 'https://blog.example')

    await expect(guard({
      name: 'admin-login', fullPath: '/admin/login', query: { redirect: '/admin/settings?tab=profile#avatar' },
      meta: { layout: 'admin', title: '登录', public: true }
    })).resolves.toEqual('/admin/settings?tab=profile#avatar')

    await expect(guard({
      name: 'admin-login', fullPath: '/admin/login', query: { redirect: '//evil.example/admin' },
      meta: { layout: 'admin', title: '登录', public: true }
    })).resolves.toBe('/admin')
  })
})
