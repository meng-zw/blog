import type { MediaAssetResponse, MediaPurpose, MediaUploadPlanResponse } from '../../shared/api/contracts'
import { completeMediaUpload, requestMediaUpload } from './api'

export type UploadProgressHandler = (percent: number) => void

export interface UploadMediaOptions {
  /** Number of retry attempts for a transient PUT failure. Defaults to one. */
  retries?: number
  /** Maximum duration of each PUT attempt. Defaults to 60 seconds. */
  timeoutMs?: number
  /** Cancels the active PUT and prevents media completion. */
  signal?: AbortSignal
}

const DEFAULT_UPLOAD_TIMEOUT_MS = 60_000

class UploadTransportError extends Error {
  readonly status: number | undefined

  constructor(message: string, status?: number) {
    super(message)
    this.status = status
  }
}

class UploadTimeoutError extends Error {}

function uploadAbortError(): DOMException {
  return new DOMException('上传已取消。', 'AbortError')
}

function readCsrfToken(): string | undefined {
  for (const part of document.cookie.split(';')) {
    const separator = part.indexOf('=')
    const name = (separator < 0 ? part : part.slice(0, separator)).trim()
    if (name !== 'XSRF-TOKEN') continue
    const value = separator < 0 ? '' : part.slice(separator + 1)
    try {
      return decodeURIComponent(value)
    } catch {
      return value
    }
  }
  return undefined
}

function isProxyPlan(plan: MediaUploadPlanResponse): boolean {
  return plan.uploadMode === 'PROXY'
}

function uploadPlanContent(
  plan: MediaUploadPlanResponse,
  file: File,
  onProgress: UploadProgressHandler | undefined,
  timeoutMs: number,
  signal?: AbortSignal
): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(uploadAbortError())
      return
    }
    const request = new XMLHttpRequest()
    let settled = false
    const progressHandler = (event: ProgressEvent) => {
      if (settled || !event.lengthComputable || event.total <= 0) return
      onProgress?.(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    }
    const cleanup = () => {
      request.upload.removeEventListener('progress', progressHandler)
      signal?.removeEventListener('abort', abortHandler)
      request.onload = null
      request.onerror = null
      request.ontimeout = null
      request.onabort = null
    }
    const fail = (error: Error) => {
      if (settled) return
      settled = true
      cleanup()
      reject(error)
    }
    const abortHandler = () => {
      request.abort()
      fail(uploadAbortError())
    }
    request.open(plan.method, plan.uploadUrl)
    request.timeout = timeoutMs
    request.withCredentials = isProxyPlan(plan)
    for (const [name, value] of Object.entries(plan.headers)) request.setRequestHeader(name, value)
    if (isProxyPlan(plan)) {
      const csrfToken = readCsrfToken()
      if (csrfToken) request.setRequestHeader('X-XSRF-TOKEN', csrfToken)
    }
    request.upload.addEventListener('progress', progressHandler)
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        if (settled) return
        onProgress?.(100)
        settled = true
        cleanup()
        resolve()
        return
      }
      fail(new UploadTransportError(`上传图片失败（HTTP ${request.status || '网络错误'}），请检查网络后重试。`, request.status))
    }
    request.onerror = () => fail(new UploadTransportError('上传图片失败，请检查网络后重试。'))
    request.ontimeout = () => {
      fail(new UploadTimeoutError('上传超时，请检查网络后重试。'))
      request.abort()
    }
    request.onabort = () => fail(uploadAbortError())
    signal?.addEventListener('abort', abortHandler, { once: true })
    request.send(file)
  })
}

function isRetryable(error: unknown): boolean {
  if (!(error instanceof UploadTransportError)) return false
  return error.status === undefined || error.status === 0 || error.status === 408 || error.status === 429 || error.status >= 500
}

async function uploadPlanContentWithRetry(
  plan: MediaUploadPlanResponse,
  file: File,
  onProgress: UploadProgressHandler | undefined,
  retries: number,
  timeoutMs: number,
  signal?: AbortSignal
): Promise<void> {
  let lastError: unknown
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    try {
      await uploadPlanContent(plan, file, onProgress, timeoutMs, signal)
      return
    } catch (error: unknown) {
      lastError = error
      if (attempt >= retries || !isRetryable(error)) break
    }
  }
  if (lastError instanceof Error || lastError instanceof DOMException) throw lastError
  throw new Error('上传图片失败，请检查网络后重试。')
}

/**
 * Uploads a permanent media object through the server-issued transport plan.
 * Storage provider URLs are deliberately handled here, never persisted by callers.
 */
export async function uploadMedia(
  file: File,
  purpose: MediaPurpose,
  onProgress?: UploadProgressHandler,
  options: UploadMediaOptions = {}
): Promise<MediaAssetResponse> {
  const plan = await requestMediaUpload(file, purpose)
  const retries = Math.max(0, options.retries ?? 1)
  const timeoutMs = Math.max(1, options.timeoutMs ?? DEFAULT_UPLOAD_TIMEOUT_MS)
  await uploadPlanContentWithRetry(plan, file, onProgress, retries, timeoutMs, options.signal)
  if (options.signal?.aborted) throw uploadAbortError()
  // Completion is idempotent server-side, but it is deliberately outside the PUT retry loop:
  // a failed confirmation must never upload the same object again.
  return completeMediaUpload(plan.mediaId)
}
