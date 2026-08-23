import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ArticleListPage from './ArticleListPage.vue'
import { listArticles } from '../api'

vi.mock('../api', () => ({ listArticles: vi.fn() }))

const mockedList = vi.mocked(listArticles)

describe('article and note lists', () => {
  beforeEach(() => mockedList.mockReset())

  it('always requests NOTE content on /notes and preserves normalized filters in URL history', async () => {
    mockedList.mockResolvedValue({ items: [], page: 0, size: 20, total: 0, totalPages: 0 })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/notes', component: ArticleListPage }] })
    await router.push('/notes?category=thinking&tag=life&q=%20slow%20&page=-2&size=500')
    await router.isReady()
    mount(ArticleListPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(mockedList.mock.calls[0]?.[0]).toEqual({
      contentType: 'NOTE', category: 'thinking', tag: 'life', q: 'slow', page: 0, size: 50
    })
    expect(mockedList.mock.calls[0]?.[1]).toBeInstanceOf(AbortSignal)
    expect(router.currentRoute.value.query).toMatchObject({
      category: 'thinking', tag: 'life', q: 'slow', page: '0', size: '50'
    })
  })
})
