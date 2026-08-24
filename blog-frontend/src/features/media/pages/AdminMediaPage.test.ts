import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { deleteMedia, listMedia } from '../api'
import { uploadMedia } from '../uploader'
import AdminMediaPage from './AdminMediaPage.vue'

vi.mock('../api', async importOriginal => ({ ...await importOriginal<typeof import('../api')>(), listMedia: vi.fn(), deleteMedia: vi.fn() }))
vi.mock('../uploader', () => ({ uploadMedia: vi.fn() }))

const asset: any = { mediaId: 12, filename: 'asset.png', contentType: 'image/png', byteSize: 1024,
  width: 1200, height: 800, provider: 'R2', status: 'READY', purpose: 'INLINE_IMAGE', referenced: false,
  url: '/api/media/assets/12', createdAt: '2026-08-24T00:00:00Z' }
const page = { items: [asset], page: 0, size: 24, total: 49, totalPages: 3 }

describe('admin media page', () => {
  beforeEach(() => { vi.mocked(listMedia).mockReset().mockResolvedValue(page); vi.mocked(deleteMedia).mockReset(); vi.mocked(uploadMedia).mockReset() })
  it('lists provider, status, purpose and reference badges with unused-only deletion', async () => {
    const wrapper = mount(AdminMediaPage); await flushPromises()
    expect(wrapper.text()).toContain('R2'); expect(wrapper.text()).toContain('INLINE_IMAGE'); expect(wrapper.text()).toContain('未使用')
    expect(wrapper.get('img').attributes('src')).toBe('/api/media/assets/12')
    await wrapper.get('button.danger').trigger('click'); expect(deleteMedia).toHaveBeenCalledWith(12)
  })
  it('uploads images through shared uploader then refreshes library', async () => {
    vi.mocked(uploadMedia).mockResolvedValue(page.items[0]); const wrapper = mount(AdminMediaPage); await flushPromises()
    const file = new File(['x'], 'asset.png', { type: 'image/png' }); Object.defineProperty(wrapper.get('input[type="file"]').element, 'files', { value: [file] })
    await wrapper.get('input[type="file"]').trigger('change'); await flushPromises()
    expect(uploadMedia).toHaveBeenCalledWith(file, 'INLINE_IMAGE', expect.any(Function)); expect(listMedia).toHaveBeenCalledTimes(2)
  })
  it('moves through media pages without losing active filters', async () => {
    const wrapper = mount(AdminMediaPage); await flushPromises()
    await wrapper.get('select').setValue('READY')
    await wrapper.get('button[aria-label="下一页"]').trigger('click'); await flushPromises()
    expect(listMedia).toHaveBeenLastCalledWith(1, 24, 'READY', undefined)
  })
})
