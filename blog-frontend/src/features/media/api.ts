import type { AdminMediaAssetResponse, MediaPurpose, MediaStatus, MediaUploadPlanResponse, MediaAssetResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = 'image/png,image/jpeg,image/gif'
export const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
export const MAX_ZIP_ATTACHMENT_BYTES = 50 * 1024 * 1024
export const ACCEPTED_ATTACHMENT_TYPES = 'application/pdf,application/zip,application/x-zip-compressed,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.openxmlformats-officedocument.presentationml.presentation'
const ACCEPTED_IMAGE_TYPE_SET: ReadonlySet<string> = new Set(ACCEPTED_IMAGE_TYPES.split(','))
const ACCEPTED_ATTACHMENT_TYPE_SET: ReadonlySet<string> = new Set(ACCEPTED_ATTACHMENT_TYPES.split(','))

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
  if (!extension || !['pdf', 'zip', 'txt', 'docx', 'xlsx', 'pptx'].includes(extension)) return `仅支持 ${expectedTypes} 附件，服务器会再次校验格式和文件签名。`
  if (file.type && !ACCEPTED_ATTACHMENT_TYPE_SET.has(file.type.toLowerCase())) return `仅支持 ${expectedTypes} 附件，服务器会再次校验格式和文件签名。`
  const limit = zip ? MAX_ZIP_ATTACHMENT_BYTES : MAX_ATTACHMENT_BYTES
  if (file.size > limit) return `${zip ? 'ZIP 附件' : '附件'}建议不超过 ${zip ? '50' : '20'} MiB；服务器校验为最终结果。`
  return null
}

export function requestMediaUpload(file: File, purpose: MediaPurpose): Promise<MediaUploadPlanResponse> {
  return http.post<MediaUploadPlanResponse>('/admin/media/uploads', {
    filename: file.name,
    contentType: file.type,
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
