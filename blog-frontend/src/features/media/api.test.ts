import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { attachmentFileHint, completeMediaUpload, requestMediaUpload } from './api'

const fetchMock = vi.fn<typeof fetch>()

describe('media API', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=media-csrf; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('requests a provider-neutral upload plan with CSRF and converts snake case fields', async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      media_id: 8, upload_mode: 'DIRECT', method: 'PUT', upload_url: 'https://r2.example/asset.png',
      headers: { 'Content-Type': 'image/png' }, expires_at: '2026-08-24T10:15:00Z'
    }), { headers: { 'Content-Type': 'application/json' } }))
    const file = new File(['image'], 'badge.png', { type: 'image/png' })

    const result = await requestMediaUpload(file, 'INLINE_IMAGE')

    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/admin/media/uploads')
    expect(init?.body).toBe(JSON.stringify({ filename: 'badge.png', content_type: 'image/png', byte_size: 5, purpose: 'INLINE_IMAGE' }))
    const headers = new Headers(init?.headers)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-XSRF-TOKEN')).toBe('media-csrf')
    expect(result).toEqual({
      mediaId: 8, uploadMode: 'DIRECT', method: 'PUT', uploadUrl: 'https://r2.example/asset.png',
      headers: { 'Content-Type': 'image/png' }, expiresAt: '2026-08-24T10:15:00Z'
    })
  })

  it.each([
    ['manual.pdf', '', 'application/pdf'],
    ['archive.zip', 'application/x-zip-compressed', 'application/zip'],
    ['notes.txt', 'application/octet-stream', 'text/plain'],
    ['report.docx', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
    ['workbook.xlsx', 'application/octet-stream', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
    ['slides.pptx', '', 'application/vnd.openxmlformats-officedocument.presentationml.presentation']
  ])('canonicalizes the upload-plan MIME for %s when the browser reports %s', async (filename, browserType, expectedType) => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      media_id: 9, upload_mode: 'PROXY', method: 'PUT', upload_url: '/api/admin/media/uploads/9/content',
      headers: { 'Content-Type': expectedType }, expires_at: '2026-08-24T10:15:00Z'
    }), { headers: { 'Content-Type': 'application/json' } }))

    await requestMediaUpload(new File(['attachment'], filename, { type: browserType }), 'ATTACHMENT')

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body)) as { content_type: string }
    expect(body.content_type).toBe(expectedType)
  })

  it('allows a supported attachment extension when the browser only reports generic binary MIME', () => {
    expect(attachmentFileHint(new File(['notes'], 'notes.txt', { type: 'application/octet-stream' }))).toBeNull()
  })

  it('completes an uploaded media asset and returns its stable URL', async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      media_id: 8, filename: 'badge.png', content_type: 'image/png', byte_size: 5, width: 2, height: 2,
      status: 'READY', purpose: 'INLINE_IMAGE', url: '/api/media/assets/8'
    }), { headers: { 'Content-Type': 'application/json' } }))

    await expect(completeMediaUpload(8)).resolves.toMatchObject({
      mediaId: 8, url: '/api/media/assets/8', status: 'READY'
    })
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/media/8/complete')
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('media-csrf')
  })
})
