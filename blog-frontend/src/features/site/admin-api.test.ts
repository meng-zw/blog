import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { loadAdminSettings, updateAdminSettings } from './admin-api'

const fetchMock = vi.fn<typeof fetch>()
const wireProfile = {
  site_title: '小M的思与行',
  subtitle: '中庸之道',
  nickname: '小M',
  bio: '中庸之道',
  avatar_media_id: 42,
  avatar_url: '/api/media/existing.png',
  github_url: 'https://github.com/meng-zw'
}

function response(): Response {
  return new Response(JSON.stringify(wireProfile), { headers: { 'Content-Type': 'application/json' } })
}

describe('admin settings API contract', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=settings-csrf; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('maps the dedicated admin avatar id and preserves it in the snake-case update body', async () => {
    fetchMock.mockResolvedValueOnce(response()).mockResolvedValueOnce(response())

    const loaded = await loadAdminSettings()
    expect(loaded.avatarMediaId).toBe(42)

    await updateAdminSettings({
      siteTitle: loaded.siteTitle,
      subtitle: '且听风吟',
      nickname: loaded.nickname,
      bio: loaded.bio,
      avatarMediaId: loaded.avatarMediaId,
      githubUrl: loaded.githubUrl
    })

    const [url, init] = fetchMock.mock.calls[1] ?? []
    expect(url).toBe('/api/admin/settings')
    expect(init?.body).toBe(JSON.stringify({
      site_title: '小M的思与行', subtitle: '且听风吟', nickname: '小M', bio: '中庸之道',
      avatar_media_id: 42, github_url: 'https://github.com/meng-zw'
    }))
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('settings-csrf')
  })
})
