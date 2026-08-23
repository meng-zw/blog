import type { MediaUploadResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = 'image/png,image/jpeg,image/gif'
const ACCEPTED_IMAGE_TYPE_SET: ReadonlySet<string> = new Set(ACCEPTED_IMAGE_TYPES.split(','))

interface MediaUploadWireResponse {
  id: number
  storage_key?: string
  storageKey?: string
  content_type?: string
  contentType?: string
  width: number | null
  height: number | null
  url: string
}

export function imageFileHint(file: File): string | null {
  if (!file.type.startsWith('image/')) return '只能选择图片文件，服务器会再次校验格式和文件签名。'
  if (!ACCEPTED_IMAGE_TYPE_SET.has(file.type.toLowerCase())) return '仅支持 PNG、JPEG 或 GIF 图片，服务器会再次校验格式和文件签名。'
  if (file.size > MAX_IMAGE_BYTES) return '图片建议不超过 5 MiB；请压缩后上传，服务器校验为最终结果。'
  return null
}

export async function uploadMedia(file: File): Promise<MediaUploadResponse> {
  const form = new FormData()
  form.append('file', file, file.name)
  const response = await http.post<MediaUploadWireResponse>('/admin/media', form)
  return {
    id: response.id,
    storageKey: response.storage_key ?? response.storageKey ?? '',
    contentType: response.content_type ?? response.contentType ?? '',
    width: response.width,
    height: response.height,
    url: response.url
  }
}
