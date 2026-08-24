import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { MediaAssetResponse, MediaUploadPlanResponse } from '../../shared/api/contracts'
import { completeMediaUpload, requestMediaUpload } from './api'
import { uploadMedia } from './uploader'

vi.mock('./api', () => ({
  requestMediaUpload: vi.fn(),
  completeMediaUpload: vi.fn()
}))

type UploadBehavior = { status?: number, error?: boolean, pending?: boolean, timeout?: boolean }
let behavior: UploadBehavior[] = []
let requests: FakeXmlHttpRequest[] = []

class FakeXmlHttpRequest {
  static readonly DONE = 4
  method = ''
  url = ''
  requestHeaders = new Headers()
  withCredentials = false
  status = 0
  readyState = 0
  responseText = ''
  timeout = 0
  aborted = false
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  ontimeout: (() => void) | null = null
  onabort: (() => void) | null = null
  upload = {
    addEventListener: vi.fn((type: string, listener: (event: ProgressEvent) => void) => {
      if (type === 'progress') this.progressListener = listener
    }),
    removeEventListener: vi.fn((type: string, listener: (event: ProgressEvent) => void) => {
      if (type === 'progress' && this.progressListener === listener) this.progressListener = null
    })
  }
  private progressListener: ((event: ProgressEvent) => void) | null = null

  open(method: string, url: string): void { this.method = method; this.url = url }
  setRequestHeader(name: string, value: string): void { this.requestHeaders.set(name, value) }
  send(): void {
    this.progressListener?.({ lengthComputable: true, loaded: 2, total: 4 } as ProgressEvent)
    const next = behavior.shift() ?? { status: 204 }
    if (next.pending) return
    if (next.timeout) {
      this.ontimeout?.()
      return
    }
    if (next.error) {
      this.onerror?.()
      return
    }
    this.status = next.status ?? 204
    this.readyState = FakeXmlHttpRequest.DONE
    this.onload?.()
  }
  abort(): void {
    this.aborted = true
    this.onabort?.()
  }
  emitProgress(loaded: number, total: number): void {
    this.progressListener?.({ lengthComputable: true, loaded, total } as ProgressEvent)
  }
}

const directPlan: MediaUploadPlanResponse = {
  mediaId: 123, uploadMode: 'DIRECT', method: 'PUT', uploadUrl: 'https://r2.example/inline-images/123.png',
  headers: { 'Content-Type': 'image/png', 'x-amz-acl': 'public-read' }, expiresAt: '2026-08-24T10:15:00Z'
}
const proxyPlan: MediaUploadPlanResponse = {
  mediaId: 124, uploadMode: 'PROXY', method: 'PUT', uploadUrl: '/api/admin/media/uploads/124/content',
  headers: { 'Content-Type': 'image/png' }, expiresAt: '2026-08-24T10:15:00Z'
}
const ready: MediaAssetResponse = {
  mediaId: 123, filename: 'diagram.png', contentType: 'image/png', byteSize: 4, width: 2, height: 2,
  status: 'READY', purpose: 'INLINE_IMAGE', url: '/api/media/assets/123'
}

describe('media uploader', () => {
  beforeEach(() => {
    behavior = []
    requests = []
    vi.stubGlobal('XMLHttpRequest', class extends FakeXmlHttpRequest {
      constructor() { super(); requests.push(this) }
    })
    document.cookie = 'XSRF-TOKEN=upload-csrf; path=/'
    vi.mocked(requestMediaUpload).mockReset()
    vi.mocked(completeMediaUpload).mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('performs direct PUT with the issued headers, reports progress, and returns the stable media URL', async () => {
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)
    vi.mocked(completeMediaUpload).mockResolvedValue(ready)
    const progress = vi.fn()

    await expect(uploadMedia(new File(['data'], 'diagram.png', { type: 'image/png' }), 'INLINE_IMAGE', progress)).resolves.toEqual(ready)

    expect(requests).toHaveLength(1)
    expect(requests[0]).toMatchObject({ method: 'PUT', url: directPlan.uploadUrl, withCredentials: false })
    expect(requests[0]?.requestHeaders).toEqual(expect.any(Headers))
    expect(requests[0]?.requestHeaders.get('Content-Type')).toBe('image/png')
    expect(requests[0]?.requestHeaders.get('x-amz-acl')).toBe('public-read')
    expect(requests[0]?.requestHeaders.has('X-XSRF-TOKEN')).toBe(false)
    expect(progress).toHaveBeenCalledWith(50)
    expect(completeMediaUpload).toHaveBeenCalledWith(123)
  })

  it('uses credentials and CSRF when the issued plan requires a proxy upload', async () => {
    vi.mocked(requestMediaUpload).mockResolvedValue(proxyPlan)
    vi.mocked(completeMediaUpload).mockResolvedValue({ ...ready, mediaId: 124, url: '/api/media/assets/124' })

    await uploadMedia(new File(['data'], 'diagram.png', { type: 'image/png' }), 'INLINE_IMAGE')

    expect(requests[0]).toMatchObject({ method: 'PUT', url: proxyPlan.uploadUrl, withCredentials: true })
    expect(requests[0]?.requestHeaders.get('X-XSRF-TOKEN')).toBe('upload-csrf')
  })

  it('does not complete a media asset when its PUT upload fails', async () => {
    behavior = [{ status: 500 }]
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)

    await expect(uploadMedia(new File(['data'], 'diagram.png', { type: 'image/png' }), 'INLINE_IMAGE', undefined, { retries: 0 }))
      .rejects.toThrow('上传图片失败')
    expect(completeMediaUpload).not.toHaveBeenCalled()
  })

  it('retries a transient upload failure before completing', async () => {
    behavior = [{ error: true }, { status: 204 }]
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)
    vi.mocked(completeMediaUpload).mockResolvedValue(ready)

    await uploadMedia(new File(['data'], 'diagram.png', { type: 'image/png' }), 'INLINE_IMAGE')

    expect(requests).toHaveLength(2)
    expect(completeMediaUpload).toHaveBeenCalledOnce()
  })

  it('does not issue a second PUT when completion fails after a successful upload', async () => {
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)
    vi.mocked(completeMediaUpload).mockRejectedValue(new Error('complete unavailable'))

    await expect(uploadMedia(new File(['data'], 'diagram.png', { type: 'image/png' }), 'INLINE_IMAGE'))
      .rejects.toThrow('complete unavailable')

    expect(requests).toHaveLength(1)
    expect(completeMediaUpload).toHaveBeenCalledOnce()
  })

  it('does not retry a non-transient PUT response based on its actual status', async () => {
    behavior = [{ status: 400 }]
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)

    await expect(uploadMedia(new File(['data'], 'diagram.png', { type: 'image/png' }), 'INLINE_IMAGE'))
      .rejects.toThrow('HTTP 400')

    expect(requests).toHaveLength(1)
  })

  it('times out a stalled upload without retrying or completing the media asset', async () => {
    behavior = [{ timeout: true }]
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)

    const result = uploadMedia(
      new File(['data'], 'diagram.png', { type: 'image/png' }),
      'INLINE_IMAGE',
      undefined,
      { timeoutMs: 1_234 }
    )
    const outcome = await Promise.race([
      result.then(() => 'resolved', (error: unknown) => error instanceof Error ? error.message : String(error)),
      new Promise<string>((resolve) => setTimeout(() => resolve('pending'), 0))
    ])

    expect(outcome).toContain('超时')
    expect(requests).toHaveLength(1)
    expect(requests[0]?.timeout).toBe(1_234)
    expect(requests[0]?.aborted).toBe(true)
    expect(completeMediaUpload).not.toHaveBeenCalled()
  })

  it('aborts a stalled upload through AbortSignal without completing the media asset', async () => {
    behavior = [{ pending: true }]
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)
    const controller = new AbortController()
    const result = uploadMedia(
      new File(['data'], 'diagram.png', { type: 'image/png' }),
      'INLINE_IMAGE',
      undefined,
      { signal: controller.signal }
    )
    await Promise.resolve()

    controller.abort()
    const outcome = await Promise.race([
      result.then(() => 'resolved', (error: unknown) => (
        typeof error === 'object' && error !== null && 'name' in error ? String(error.name) : String(error)
      )),
      new Promise<string>((resolve) => setTimeout(() => resolve('pending'), 0))
    ])

    expect(outcome).toBe('AbortError')
    expect(requests[0]?.aborted).toBe(true)
    expect(completeMediaUpload).not.toHaveBeenCalled()
  })

  it('removes abort and progress listeners after a successful upload settles', async () => {
    vi.mocked(requestMediaUpload).mockResolvedValue(directPlan)
    vi.mocked(completeMediaUpload).mockResolvedValue(ready)
    const controller = new AbortController()
    const progress = vi.fn()

    await uploadMedia(
      new File(['data'], 'diagram.png', { type: 'image/png' }),
      'INLINE_IMAGE',
      progress,
      { signal: controller.signal }
    )
    const progressCalls = progress.mock.calls.length
    controller.abort()
    requests[0]?.emitProgress(3, 4)

    expect(requests[0]?.aborted).toBe(false)
    expect(progress).toHaveBeenCalledTimes(progressCalls)
  })
})
