import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { http, setUnauthorizedHandler } from './http'
import { ApiProblem, ApiRequestError } from './problem'

const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init
  })
}

function expireCookies(): void {
  for (const cookie of document.cookie.split(';')) {
    const name = cookie.split('=', 1)[0]?.trim()
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  expireCookies()
})

afterEach(() => {
  setUnauthorizedHandler(null)
  vi.unstubAllGlobals()
  expireCookies()
})

describe('http', () => {
  it('converts JSON response keys to camelCase and JSON request keys to snake_case recursively', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({
      site_title: '小M的思与行',
      latest_articles: [{ published_at: '2026-08-23T08:00:00Z' }]
    }))

    const result = await http.post<{
      siteTitle: string
      latestArticles: Array<{ publishedAt: string }>
    }>('/admin/example', {
      coverMediaId: 7,
      tagIds: [1, 2],
      nestedValue: { sortOrder: 3 }
    })

    expect(result).toEqual({
      siteTitle: '小M的思与行',
      latestArticles: [{ publishedAt: '2026-08-23T08:00:00Z' }]
    })
    const [, init] = fetchMock.mock.calls[0] ?? []
    expect(init?.body).toBe('{"cover_media_id":7,"tag_ids":[1,2],"nested_value":{"sort_order":3}}')
  })

  it('encodes query values, includes credentials and forwards the abort signal on GET', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ siteTitle: '小M的思与行' }))
    const controller = new AbortController()

    const result = await http.get<{ siteTitle: string }>('/public/site-profile', {
      query: { q: '思 与 行', page: 0, tag: ['vue', '测试'], ignored: undefined },
      signal: controller.signal,
      headers: { Accept: 'application/json' }
    })

    expect(result).toEqual({ siteTitle: '小M的思与行' })
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/public/site-profile?q=%E6%80%9D+%E4%B8%8E+%E8%A1%8C&page=0&tag=vue&tag=%E6%B5%8B%E8%AF%95')
    expect(init).toMatchObject({ credentials: 'include', method: 'GET', signal: controller.signal })
    const headers = new Headers(init?.headers)
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.has('Content-Type')).toBe(false)
    expect(headers.has('X-XSRF-TOKEN')).toBe(false)
  })

  it('decodes the exact XSRF cookie among multiple cookies for JSON POST requests', async () => {
    document.cookie = 'theme=paper; path=/'
    document.cookie = 'XSRF-TOKEN=token%2Bwith%2Fslashes%3D; path=/'
    document.cookie = 'XSRF-TOKEN-OLD=wrong; path=/'
    fetchMock.mockResolvedValueOnce(jsonResponse({ authenticated: true }))

    await http.post('/admin/session', { username: 'admin', password: 'secret' }, {
      headers: { Accept: 'application/problem+json' }
    })

    const [, init] = fetchMock.mock.calls[0] ?? []
    const headers = new Headers(init?.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('token+with/slashes=')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('Accept')).toBe('application/problem+json')
    expect(init?.body).toBe('{"username":"admin","password":"secret"}')
  })

  it.each(['PUT', 'DELETE'] as const)('adds CSRF for unsafe %s without overwriting caller headers', async (method) => {
    document.cookie = 'XSRF-TOKEN=cookie-token; path=/'
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    if (method === 'PUT') {
      await http.put<void>('/admin/settings', { siteTitle: '小M' }, {
        headers: { 'X-XSRF-TOKEN': 'caller-token', 'X-Trace-Id': 'trace_1234' }
      })
    } else {
      await http.delete<void>('/admin/session', {
        headers: { 'X-XSRF-TOKEN': 'caller-token', 'X-Trace-Id': 'trace_1234' }
      })
    }

    const [, init] = fetchMock.mock.calls[0] ?? []
    const headers = new Headers(init?.headers)
    expect(init?.method).toBe(method)
    expect(headers.get('X-XSRF-TOKEN')).toBe('caller-token')
    expect(headers.get('X-Trace-Id')).toBe('trace_1234')
    if (method === 'DELETE') expect(headers.has('Content-Type')).toBe(false)
  })

  it.each(['POST', 'PUT', 'DELETE'] as const)('copies cookie-derived CSRF for %s without a caller token', async (method) => {
    document.cookie = 'XSRF-TOKEN=cookie-derived-token; path=/'
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    if (method === 'POST') await http.post<void>('/admin/articles', { title: 'Draft' })
    if (method === 'PUT') await http.put<void>('/admin/settings', { siteTitle: '小M' })
    if (method === 'DELETE') await http.delete<void>('/admin/session')

    const [, init] = fetchMock.mock.calls[0] ?? []
    expect(init?.method).toBe(method)
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('cookie-derived-token')
  })

  it('passes a string body through unchanged when the caller selects text/plain', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await http.post<void>('/admin/import', 'first line\nsecond line', {
      headers: { 'Content-Type': 'text/plain; charset=utf-8' }
    })

    const [, init] = fetchMock.mock.calls[0] ?? []
    expect(init?.body).toBe('first line\nsecond line')
    expect(new Headers(init?.headers).get('Content-Type')).toBe('text/plain; charset=utf-8')
  })

  it('returns undefined for a 204 response', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(http.delete<void>('/admin/session')).resolves.toBeUndefined()
  })

  it('returns successful plain text safely', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<urlset />', {
      headers: { 'Content-Type': 'application/xml' }
    }))

    await expect(http.get<string>('/sitemap.xml')).resolves.toBe('<urlset />')
  })

  it('does not navigate when a 401 response is received', async () => {
    window.history.replaceState({}, '', '/admin/articles')
    fetchMock.mockResolvedValueOnce(jsonResponse({
      title: 'Unauthorized', status: 401, detail: 'Authentication required', traceId: 'trace-401'
    }, { status: 401 }))

    await expect(http.get('/admin/articles')).rejects.toBeInstanceOf(ApiProblem)
    expect(window.location.pathname).toBe('/admin/articles')
  })

  it('reports a later 401 through the controlled session hook without navigating', async () => {
    window.history.replaceState({}, '', '/admin/settings')
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    fetchMock.mockResolvedValueOnce(jsonResponse({
      title: 'Unauthorized', status: 401, detail: '会话已失效', traceId: 'trace-later-401'
    }, { status: 401 }))

    await http.get('/admin/settings').catch(() => undefined)

    expect(unauthorized).toHaveBeenCalledOnce()
    expect(unauthorized.mock.calls[0]?.[0]).toMatchObject({ status: 401, traceId: 'trace-later-401' })
    expect(window.location.pathname).toBe('/admin/settings')
  })

  it('parses problem+json trace IDs and validation fields', async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      type: 'about:blank',
      title: 'Validation failed',
      status: 400,
      detail: 'Request validation failed',
      instance: '/api/admin/articles',
      traceId: 'trace-validation',
      errors: { title: 'must not be blank', tagIds: ['must not be null'] }
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/problem+json; charset=utf-8' }
    }))

    const error = await http.post('/admin/articles', {}).catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiProblem)
    expect(error).toMatchObject({
      status: 400,
      title: 'Validation failed',
      detail: 'Request validation failed',
      traceId: 'trace-validation',
      errors: { title: ['must not be blank'], tagIds: ['must not be null'] }
    })
  })

  it('parses an application/json Problem Detail variant using the HTTP status fallback', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({
      title: 'Conflict', detail: 'slug exists', traceId: 'trace-conflict'
    }, { status: 409 }))

    const error = await http.post('/admin/tools', {}).catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiProblem)
    expect(error).toMatchObject({ status: 409, title: 'Conflict', detail: 'slug exists', traceId: 'trace-conflict' })
  })

  it('reports malformed JSON as an invalid response without exposing the Response object', async () => {
    fetchMock.mockResolvedValueOnce(new Response('{broken', {
      headers: { 'Content-Type': 'application/json' }
    }))

    const error = await http.get('/public/home').catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiRequestError)
    expect(error).toMatchObject({ kind: 'invalid-response', status: 200 })
    expect(error).not.toHaveProperty('response')
  })

  it('uses a generic problem for non-JSON failures without echoing the response body', async () => {
    fetchMock.mockResolvedValueOnce(new Response('database host and password leaked', {
      status: 500,
      headers: { 'Content-Type': 'text/plain' }
    }))

    const error = await http.get('/public/home').catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiProblem)
    expect(error).toMatchObject({ status: 500, title: 'Request failed' })
    expect(String(error)).not.toContain('database host')
  })

  it('normalizes fetch rejections into a network error', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('socket internals'))

    const error = await http.get('/public/home').catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(ApiRequestError)
    expect(error).toMatchObject({ kind: 'network' })
    expect(String(error)).not.toContain('socket internals')
  })
})
