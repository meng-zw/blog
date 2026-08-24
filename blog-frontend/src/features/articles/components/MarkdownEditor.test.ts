import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { uploadMedia } from '../../media/uploader'
import MarkdownEditor from './MarkdownEditor.vue'

let constructorOptions: Record<string, unknown> | undefined
const insertValue = vi.fn()

vi.mock('vditor', () => ({
  default: class {
    constructor(_host: HTMLElement, options: Record<string, unknown>) { constructorOptions = options }
    getValue(): string { return '' }
    setValue(): void {}
    insertValue = insertValue
    destroy(): void {}
  }
}))
vi.mock('vditor/dist/index.css', () => ({}))
vi.mock('../../media/uploader', () => ({ uploadMedia: vi.fn() }))

async function editorUpload() {
  const wrapper = mount(MarkdownEditor, { props: { modelValue: '已有内容' } })
  await flushPromises()
  const upload = constructorOptions?.upload as { handler: (files: File[]) => Promise<string | null> }
  return { wrapper, handler: upload.handler }
}

describe('MarkdownEditor media uploads', () => {
  beforeEach(() => {
    constructorOptions = undefined
    insertValue.mockReset()
    vi.mocked(uploadMedia).mockReset()
  })
  afterEach(() => vi.restoreAllMocks())

  it('uploads pasted PNG images with progress and inserts a stable escaped Markdown URL', async () => {
    let resolveUpload!: (value: { mediaId: number, filename: string, contentType: string, byteSize: number, width: number, height: number, status: 'READY', purpose: 'INLINE_IMAGE', url: string }) => void
    vi.mocked(uploadMedia).mockImplementation((_file, _purpose, progress) => new Promise((resolve) => {
      resolveUpload = resolve
      progress?.(50)
    }))
    const { wrapper, handler } = await editorUpload()

    const result = handler([new File(['data'], '图[1].png', { type: 'image/png' })])
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toContain('50%')
    resolveUpload({ mediaId: 123, filename: '图[1].png', contentType: 'image/png', byteSize: 4, width: 2, height: 2, status: 'READY', purpose: 'INLINE_IMAGE', url: '/api/media/assets/123' })

    await expect(result).resolves.toBeNull()
    expect(uploadMedia).toHaveBeenCalledWith(expect.any(File), 'INLINE_IMAGE', expect.any(Function))
    expect(insertValue).toHaveBeenCalledWith('\n![图\\[1\\]](/api/media/assets/123)\n')
    wrapper.unmount()
  })

  it('returns a Chinese error and does not mutate Markdown when the upload fails', async () => {
    vi.mocked(uploadMedia).mockRejectedValue(new Error('network'))

    const { wrapper, handler } = await editorUpload()
    await expect(handler([new File(['data'], 'diagram.png', { type: 'image/png' })])).resolves.toBe('图片上传失败，请检查网络后重试。')
    expect(insertValue).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('图片上传失败，请检查网络后重试。')
    wrapper.unmount()
  })

  it('rejects unsupported files before starting an upload', async () => {
    const { wrapper, handler } = await editorUpload()
    await expect(handler([new File(['data'], 'unsafe.svg', { type: 'image/svg+xml' })]))
      .resolves.toContain('仅支持 PNG、JPEG 或 GIF')
    expect(uploadMedia).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('uploads every pasted image sequentially instead of silently dropping later files', async () => {
    vi.mocked(uploadMedia)
      .mockResolvedValueOnce({ mediaId: 11, filename: 'one.png', contentType: 'image/png', byteSize: 1, width: 1, height: 1, status: 'READY', purpose: 'INLINE_IMAGE', url: '/api/media/assets/11' })
      .mockResolvedValueOnce({ mediaId: 12, filename: 'two.png', contentType: 'image/png', byteSize: 1, width: 1, height: 1, status: 'READY', purpose: 'INLINE_IMAGE', url: '/api/media/assets/12' })
    const { wrapper, handler } = await editorUpload()

    await expect(handler([
      new File(['1'], 'one.png', { type: 'image/png' }),
      new File(['2'], 'two.png', { type: 'image/png' })
    ])).resolves.toBeNull()

    expect(uploadMedia).toHaveBeenCalledTimes(2)
    expect(insertValue).toHaveBeenNthCalledWith(1, '\n![one](/api/media/assets/11)\n')
    expect(insertValue).toHaveBeenNthCalledWith(2, '\n![two](/api/media/assets/12)\n')
    wrapper.unmount()
  })

  it('restores the original selection before inserting after an asynchronous upload', async () => {
    const initialRange = document.createRange()
    const editorText = document.createTextNode('原位置')
    document.body.append(editorText)
    initialRange.setStart(editorText, 1)
    initialRange.collapse(true)
    const selection = window.getSelection()!
    selection.removeAllRanges()
    selection.addRange(initialRange)
    let resolveUpload!: (value: { mediaId: number, filename: string, contentType: string, byteSize: number, width: number, height: number, status: 'READY', purpose: 'INLINE_IMAGE', url: string }) => void
    vi.mocked(uploadMedia).mockImplementation(() => new Promise((resolve) => { resolveUpload = resolve }))
    const { wrapper, handler } = await editorUpload()
    const result = handler([new File(['data'], 'cursor.png', { type: 'image/png' })])
    const movedRange = document.createRange()
    movedRange.setStart(editorText, 3)
    movedRange.collapse(true)
    selection.removeAllRanges()
    selection.addRange(movedRange)
    const addRange = vi.spyOn(selection, 'addRange')

    resolveUpload({ mediaId: 15, filename: 'cursor.png', contentType: 'image/png', byteSize: 1, width: 1, height: 1, status: 'READY', purpose: 'INLINE_IMAGE', url: '/api/media/assets/15' })
    await result

    expect(addRange).toHaveBeenCalled()
    expect(addRange.mock.calls[0]?.[0].startOffset).toBe(1)
    expect(insertValue).toHaveBeenCalledWith('\n![cursor](/api/media/assets/15)\n')
    editorText.remove()
    wrapper.unmount()
  })

  it('does not insert a completed upload after the editor has unmounted', async () => {
    let resolveUpload!: (value: { mediaId: number, filename: string, contentType: string, byteSize: number, width: number, height: number, status: 'READY', purpose: 'INLINE_IMAGE', url: string }) => void
    vi.mocked(uploadMedia).mockImplementation(() => new Promise((resolve) => { resolveUpload = resolve }))
    const { wrapper, handler } = await editorUpload()
    const result = handler([new File(['data'], 'gone.png', { type: 'image/png' })])
    wrapper.unmount()

    resolveUpload({ mediaId: 16, filename: 'gone.png', contentType: 'image/png', byteSize: 1, width: 1, height: 1, status: 'READY', purpose: 'INLINE_IMAGE', url: '/api/media/assets/16' })
    await expect(result).resolves.toBeNull()
    expect(insertValue).not.toHaveBeenCalled()
  })
})
