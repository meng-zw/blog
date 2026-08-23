import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ToolDetailResponse } from '../../../shared/api/contracts'
import ToolDetailPage from './ToolDetailPage.vue'
import { loadTool } from '../api'

vi.mock('../api', () => ({ loadTool: vi.fn() }))

const tool: ToolDetailResponse = {
  id: 1, slug: 'focus', name: '专注工具', summary: '帮助保持注意力。',
  officialUrl: 'https://tools.example/focus', coverUrl: null,
  category: { id: 2, name: '效率', slug: 'productivity' },
  tags: [{ id: 3, name: '专注', slug: 'focus' }], featured: true,
  publishedAt: '2026-08-18T08:00:00Z',
  renderedHtml: '<h2>适用场景</h2><p><a href="https://docs.example">说明文档</a></p>'
}
const StubPage = defineComponent({ template: '<div />' })
const mockedLoadTool = vi.mocked(loadTool)

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/tools', component: StubPage }, { path: '/tools/:slug', component: ToolDetailPage }
  ] })
  await router.push('/tools/focus')
  await router.isReady()
  return mount(ToolDetailPage, { global: { plugins: [router] } })
}

describe('tool detail page', () => {
  beforeEach(() => mockedLoadTool.mockReset())

  it('renders safe rich content and only an HTTPS official external link', async () => {
    mockedLoadTool.mockResolvedValue(tool)
    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.get('a[href="https://tools.example/focus"]').attributes()).toMatchObject({ target: '_blank', rel: 'noopener noreferrer' })
    expect(wrapper.get('a[href="https://docs.example"]').attributes()).toMatchObject({ target: '_blank', rel: 'noopener noreferrer' })

    wrapper.unmount()
    mockedLoadTool.mockResolvedValue({ ...tool, officialUrl: 'http://tools.example/focus' })
    const unsafeWrapper = await mountPage()
    await flushPromises()
    expect(unsafeWrapper.find('a[href^="http://tools.example"]').exists()).toBe(false)
  })
})
