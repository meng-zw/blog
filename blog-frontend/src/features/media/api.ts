import type { AdminMediaAssetResponse, CloudreveConnectionResponse, MediaPurpose, MediaStatus, MediaUploadPlanResponse, MediaAssetResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = 'image/png,image/jpeg,image/gif'
export const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
export const MAX_ZIP_ATTACHMENT_BYTES = 50 * 1024 * 1024
export const ACCEPTED_ATTACHMENT_TYPES = 'application/pdf,application/zip,application/x-zip-compressed,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.openxmlformats-officedocument.presentationml.presentation'
const ACCEPTED_IMAGE_TYPE_SET: ReadonlySet<string> = new Set(ACCEPTED_IMAGE_TYPES.split(','))
const ATTACHMENT_CONTENT_TYPES: Readonly<Record<string, string>> = {
  pdf: 'application/pdf',
  zip: 'application/zip',
  txt: 'text/plain',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
}

export function imageFileHint(file: File): string | null {
  if (!file.type.startsWith('image/')) return '只能选择图片文件，服务器会再次校验格式和文件签名。'
  if (!ACCEPTED_IMAGE_TYPE_SET.has(file.type.toLowerCase())) return '仅支持 PNG、JPEG 或 GIF 图片，服务器会再次校验格式和文件签名。'
  if (file.size > MAX_IMAGE_BYTES) return '图片建议不超过 5 MiB；请压缩后上传，服务器校验为最终结果。'
  return null
}

export function attachmentFileHint(file: File): string | null {
  const extension = file.name.split('.').pop()?.toLowerCase()
  const zip = extension === 'zip'
  const expectedTypes = 'PDF、ZIP、TXT、DOCX、XLSX 或 PPTX'
  const canonicalType = extension ? ATTACHMENT_CONTENT_TYPES[extension] : undefined
  if (!canonicalType) return `仅支持 ${expectedTypes} 附件，服务器会再次校验格式和文件签名。`
  const browserType = file.type.trim().toLowerCase()
  const normalizedBrowserType = browserType === 'application/x-zip-compressed' ? 'application/zip' : browserType
  if (normalizedBrowserType && normalizedBrowserType !== 'application/octet-stream' && normalizedBrowserType !== canonicalType) return `仅支持 ${expectedTypes} 附件，服务器会再次校验格式和文件签名。`
  const limit = zip ? MAX_ZIP_ATTACHMENT_BYTES : MAX_ATTACHMENT_BYTES
  if (file.size > limit) return `${zip ? 'ZIP 附件' : '附件'}建议不超过 ${zip ? '50' : '20'} MiB；服务器校验为最终结果。`
  return null
}

function uploadContentType(file: File, purpose: MediaPurpose): string {
  const browserType = file.type.trim().toLowerCase()
  if (purpose !== 'ATTACHMENT') return browserType
  const extension = file.name.split('.').pop()?.toLowerCase()
  const canonicalType = extension ? ATTACHMENT_CONTENT_TYPES[extension] : undefined
  if (browserType === 'application/x-zip-compressed' && extension === 'zip') return 'application/zip'
  if ((!browserType || browserType === 'application/octet-stream') && canonicalType) return canonicalType
  return browserType
}

export function requestMediaUpload(file: File, purpose: MediaPurpose): Promise<MediaUploadPlanResponse> {
  return http.post<MediaUploadPlanResponse>('/admin/media/uploads', {
    filename: file.name,
    contentType: uploadContentType(file, purpose),
    byteSize: file.size,
    purpose
  })
}

export function completeMediaUpload(mediaId: number): Promise<MediaAssetResponse> {
  return http.post<MediaAssetResponse>(`/admin/media/${mediaId}/complete`)
}

export interface MediaPage { items: AdminMediaAssetResponse[]; page: number; size: number; total: number; totalPages: number }

export function listMedia(page = 0, size = 24, status?: MediaStatus, purpose?: MediaPurpose): Promise<MediaPage> {
  return http.get<MediaPage>('/admin/media', { query: { page, size, status, purpose } })
}

export function deleteMedia(mediaId: number): Promise<void> { return http.delete<void>(`/admin/media/${mediaId}`) }

type CloudreveAuthorizationRedirectResponse = { redirectUrl?: unknown }
export type CloudreveAuthorizationUrl = string

function trustedInternalAuthorizationOrigin(value: string | null): string | null {
  if (typeof value !== 'string' || !value || value !== value.trim()) return null
  try {
    const url = new URL(value)
    if (url.protocol !== 'http:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) return null
    return url.origin
  } catch {
    return null
  }
}

function allowedCloudreveAuthorizationUrl(value: unknown, approvedInternalAuthorizationOrigin: string | null): CloudreveAuthorizationUrl {
  if (typeof value !== 'string' || !value || value !== value.trim()) throw new Error('invalid Cloudreve authorization redirect')

  let url: URL
  try {
    url = new URL(value)
  } catch {
    throw new Error('invalid Cloudreve authorization redirect')
  }

  const approvedHttpOrigin = trustedInternalAuthorizationOrigin(approvedInternalAuthorizationOrigin)
  const allowedInternalHttp = url.protocol === 'http:' && approvedHttpOrigin !== null && url.origin === approvedHttpOrigin
  if ((url.protocol !== 'https:' && !allowedInternalHttp) || url.username || url.password) {
    throw new Error('invalid Cloudreve authorization redirect')
  }
  return url.toString()
}

export function getCloudreveConnection(): Promise<CloudreveConnectionResponse> {
  return http.get<CloudreveConnectionResponse>('/admin/media/cloudreve')
}

export async function authorizeCloudreve(approvedInternalAuthorizationOrigin: string | null = null): Promise<CloudreveAuthorizationUrl> {
  const response = await http.post<CloudreveAuthorizationRedirectResponse>('/admin/media/cloudreve/authorize')
  return allowedCloudreveAuthorizationUrl(response.redirectUrl, approvedInternalAuthorizationOrigin)
}

export function navigateToCloudreveAuthorization(authorizationUrl: CloudreveAuthorizationUrl): void {
  window.location.assign(authorizationUrl)
}

export function disconnectCloudreve(): Promise<void> {
  return http.post<void>('/admin/media/cloudreve/disconnect')
}
