import type { MediaAssetResponse, MediaPurpose, MediaUploadPlanResponse } from '../../shared/api/contracts'
import { completeMediaUpload, requestMediaUpload } from './api'

export type UploadProgressHandler = (percent: number) => void

export interface UploadMediaOptions {
  /** Number of retry attempts for a transient PUT failure. Defaults to one. */
  retries?: number
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

function uploadPlanContent(plan: MediaUploadPlanResponse, file: File, onProgress?: UploadProgressHandler): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open(plan.method, plan.uploadUrl)
    request.withCredentials = isProxyPlan(plan)
    for (const [name, value] of Object.entries(plan.headers)) request.setRequestHeader(name, value)
    if (isProxyPlan(plan)) {
      const csrfToken = readCsrfToken()
      if (csrfToken) request.setRequestHeader('X-XSRF-TOKEN', csrfToken)
    }
    request.upload.addEventListener('progress', (event) => {
      if (!event.lengthComputable || event.total <= 0) return
      onProgress?.(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    })
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        onProgress?.(100)
        resolve()
        return
      }
      reject(new Error(`上传图片失败（HTTP ${request.status || '网络错误'}），请检查网络后重试。`))
    }
    request.onerror = () => reject(new Error('上传图片失败，请检查网络后重试。'))
    request.send(file)
  })
}

function isRetryable(error: unknown): boolean {
  const message = error instanceof Error ? error.message : ''
  return !/HTTP (4\d\d)/.test(message)
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
  let lastError: unknown
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    try {
      await uploadPlanContent(plan, file, onProgress)
      return await completeMediaUpload(plan.mediaId)
    } catch (error: unknown) {
      lastError = error
      if (attempt >= retries || !isRetryable(error)) break
    }
  }
  throw lastError instanceof Error ? lastError : new Error('上传图片失败，请检查网络后重试。')
}
