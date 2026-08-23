import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { TopicDetailResponse } from '../../../shared/api/contracts'
import TopicDetailPage from './TopicDetailPage.vue'
import { loadTopic } from '../api'

vi.mock('../api', () => ({ loadTopic: vi.fn() }))

const topic: TopicDetailResponse = {
  id: 1, name: '工作方法', slug: 'work', description: '循序阅读。', coverUrl: null,
  articles: [
    { id: 2, slug: 'first', title: '第一篇', summary: '先读', contentType: 'ARTICLE', publishedAt: '2026-08-18T08:00:00Z', coverUrl: null, category: null, tags: [] },
    { id: 3, slug: 'second', title: '第二篇', summary: '后读', contentType: 'ARTICLE', publishedAt: '2026-08-19T08:00:00Z', coverUrl: null, category: null, tags: [] }
  ]
}
const StubPage = defineComponent({ template: '<div />' })
const mockedLoadTopic = vi.mocked(loadTopic)

describe('topic detail page', () => {
  beforeEach(() => mockedLoadTopic.mockReset())

  it('keeps the backend article order without exposing administrative fields', async () => {
    mockedLoadTopic.mockResolvedValue(topic)
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/topics/:slug', component: TopicDetailPage }, { path: '/articles/:slug', component: StubPage }
    ] })
    await router.push('/topics/work')
    await router.isReady()
    const wrapper = mount(TopicDetailPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.findAll('.ordered-articles h2').map((heading) => heading.text())).toEqual(['第一篇', '第二篇'])
    expect(wrapper.text()).not.toContain('status')
    expect(wrapper.text()).not.toContain('sortOrder')
  })
})
