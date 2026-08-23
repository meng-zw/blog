import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { uploadMedia } from '../api'
import AdminMediaPage from './AdminMediaPage.vue'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, uploadMedia: vi.fn() }
})

describe('admin media page', () => {
  beforeEach(() => vi.mocked(uploadMedia).mockReset())

  it('states the v1 listing limitation before an upload', () => {
    const wrapper = mount(AdminMediaPage)

    expect(wrapper.text()).toContain('当前 v1 后端暂不提供已有媒体库列表')
    expect(wrapper.get('input[type="file"]').attributes('accept')).toBe('image/png,image/jpeg,image/gif')
  })

  it.each([
    [new File(['text'], 'notes.txt', { type: 'text/plain' }), '只能选择图片'],
    [new File(['webp'], 'asset.webp', { type: 'image/webp' }), '仅支持 PNG、JPEG 或 GIF'],
    [new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }), '5 MiB']
  ])('rejects an obvious client-side upload bound while keeping the server authoritative', async (file, message) => {
    const wrapper = mount(AdminMediaPage)
    Object.defineProperty(wrapper.get('input[type="file"]').element, 'files', { value: [file] })
    await wrapper.get('input[type="file"]').trigger('change')

    expect(wrapper.get('[role="alert"]').text()).toContain(message)
    expect(uploadMedia).not.toHaveBeenCalled()
  })

  it('shows only the last successfully uploaded asset details and contain-fitted preview', async () => {
    vi.mocked(uploadMedia).mockResolvedValue({
      id: 12, storageKey: 'asset.png', contentType: 'image/png', width: 1200, height: 800,
      url: '/api/media/asset.png'
    })
    const wrapper = mount(AdminMediaPage)
    const file = new File(['image'], 'asset.png', { type: 'image/png' })
    Object.defineProperty(wrapper.get('input[type="file"]').element, 'files', { value: [file] })
    await wrapper.get('input[type="file"]').trigger('change')
    await flushPromises()

    expect(wrapper.get('[data-last-upload]').text()).toContain('1200 × 800')
    expect(wrapper.get('[data-last-upload]').text()).toContain('image/png')
    expect(wrapper.get('[data-last-upload] img').attributes('src')).toBe('/api/media/asset.png')
  })
})
