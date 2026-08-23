import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { PageResponse, SearchResultResponse } from '../../../shared/api/contracts'
import SearchPage from './SearchPage.vue'
import { searchPublic } from '../api'

vi.mock('../api', () => ({ searchPublic: vi.fn() }))

const mockedSearch = vi.mocked(searchPublic)
const StubPage = defineComponent({ template: '<div />' })

async function mountSearch(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/search', component: SearchPage },
    { path: '/articles/:slug', component: StubPage },
    { path: '/topics/:slug', component: StubPage },
    { path: '/tools/:slug', component: StubPage }
  ] })
  await router.push(path)
  await router.isReady()
  return { wrapper: mount(SearchPage, { global: { plugins: [router] } }), router }
}

describe('public search page', () => {
  beforeEach(() => { mockedSearch.mockReset() })

  it('shows guidance and sends no request for a blank query', async () => {
    const { wrapper } = await mountSearch('/search?q=%20%20')
    await flushPromises()

    expect(wrapper.text()).toContain('输入关键词')
    expect(mockedSearch).not.toHaveBeenCalled()
  })

  it('links every discriminated result type to its public destination', async () => {
    const items: SearchResultResponse[] = [
      { type: 'ARTICLE', id: 1, slug: 'article', title: '文章', summary: '文章摘要', publishedAt: '2026-08-20T08:00:00Z' },
      { type: 'NOTE', id: 2, slug: 'note', title: '随笔', summary: '随笔摘要', publishedAt: '2026-08-19T08:00:00Z' },
      { type: 'TOPIC', id: 3, slug: 'topic', title: '专题', summary: null, publishedAt: null },
      { type: 'TOOL', id: 4, slug: 'tool', title: '工具', summary: '工具摘要', publishedAt: '2026-08-18T08:00:00Z' }
    ]
    mockedSearch.mockResolvedValue({ items, page: 0, size: 20, total: 4, totalPages: 1 })
    const { wrapper } = await mountSearch('/search?q=效率&page=0&size=20')
    await flushPromises()

    expect(wrapper.get('a[href="/articles/article"]').text()).toContain('文章')
    expect(wrapper.get('a[href="/articles/note"]').text()).toContain('随笔')
    expect(wrapper.get('a[href="/topics/topic"]').text()).toContain('专题')
    expect(wrapper.get('a[href="/tools/tool"]').text()).toContain('工具')
  })

  it('shows an honest empty result and keeps query pagination in the URL', async () => {
    const empty: PageResponse<SearchResultResponse> = { items: [], page: 0, size: 10, total: 0, totalPages: 0 }
    mockedSearch.mockResolvedValue(empty)
    const { wrapper, router } = await mountSearch('/search?q=没有结果&page=-8&size=999')
    await flushPromises()

    expect(wrapper.text()).toContain('没有找到匹配内容')
    expect(mockedSearch.mock.calls[0]?.[0]).toEqual({ q: '没有结果', page: 0, size: 50 })
    expect(mockedSearch.mock.calls[0]?.[1]).toBeInstanceOf(AbortSignal)
    expect(router.currentRoute.value.query).toMatchObject({ q: '没有结果', page: '0', size: '50' })
  })

  it('aborts an in-flight search when navigation clears the query', async () => {
    let resolveSearch!: (value: PageResponse<SearchResultResponse>) => void
    let requestSignal: AbortSignal | undefined
    mockedSearch.mockImplementation((_params, signal) => {
      requestSignal = signal
      return new Promise((resolve) => { resolveSearch = resolve })
    })
    const { wrapper, router } = await mountSearch('/search?q=旧查询&page=0&size=20')
    await router.push('/search')
    await flushPromises()

    expect(requestSignal?.aborted).toBe(true)
    resolveSearch({
      items: [{ type: 'ARTICLE', id: 9, slug: 'stale', title: '过期结果', summary: null, publishedAt: null }],
      page: 0, size: 20, total: 1, totalPages: 1
    })
    await flushPromises()
    expect(wrapper.text()).toContain('输入关键词')
    expect(wrapper.text()).not.toContain('过期结果')
    wrapper.unmount()
  })
})
