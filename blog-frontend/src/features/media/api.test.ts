import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { uploadMedia } from './api'

const fetchMock = vi.fn<typeof fetch>()

describe('media API', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=media-csrf; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('uploads a named file as multipart without forcing a JSON content type', async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      id: 8, storage_key: 'asset.png', content_type: 'image/png', width: 640, height: 640,
      url: '/api/media/asset.png'
    }), { headers: { 'Content-Type': 'application/json' } }))
    const file = new File(['image'], 'badge.png', { type: 'image/png' })

    const result = await uploadMedia(file)

    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/admin/media')
    expect(init?.body).toBeInstanceOf(FormData)
    const uploaded = (init?.body as FormData).get('file') as File
    expect(uploaded.name).toBe('badge.png')
    expect(uploaded.type).toBe('image/png')
    expect(uploaded.size).toBe(5)
    const headers = new Headers(init?.headers)
    expect(headers.has('Content-Type')).toBe(false)
    expect(headers.get('X-XSRF-TOKEN')).toBe('media-csrf')
    expect(result).toEqual({
      id: 8, storageKey: 'asset.png', contentType: 'image/png', width: 640, height: 640,
      url: '/api/media/asset.png'
    })
  })
})
