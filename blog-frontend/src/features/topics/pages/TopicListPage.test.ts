import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { PageResponse, PublicTopicSummaryResponse } from '../../../shared/api/contracts'
import { ApiProblem } from '../../../shared/api/problem'
import TopicListPage from './TopicListPage.vue'
import { listTopics } from '../api'

vi.mock('../api', () => ({ listTopics: vi.fn() }))
const mockedListTopics = vi.mocked(listTopics)
const StubPage = defineComponent({ template: '<div />' })

async function mountPage(path = '/topics') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/topics', component: TopicListPage }, { path: '/topics/:slug', component: StubPage }
  ] })
  await router.push(path)
  await router.isReady()
  return { wrapper: mount(TopicListPage, { global: { plugins: [router] } }), router }
}

const topics: PageResponse<PublicTopicSummaryResponse> = {
  items: [{ id: 1, name: 'Java', slug: 'java', description: 'JVM', coverUrl: null }],
  page: 0, size: 20, total: 1, totalPages: 1
}

describe('topic list page', () => {
  beforeEach(() => { mockedListTopics.mockReset() })

  it('normalizes URL pagination and requests the database page', async () => {
    mockedListTopics.mockResolvedValue({ ...topics, size: 50 })
    const { wrapper, router } = await mountPage('/topics?page=-4&size=999')
    await flushPromises()

    expect(mockedListTopics.mock.calls[0]?.[0]).toEqual({ page: 0, size: 50 })
    expect(mockedListTopics.mock.calls[0]?.[1]).toBeInstanceOf(AbortSignal)
    expect(router.currentRoute.value.query).toEqual({ page: '0', size: '50' })
    expect(wrapper.text()).toContain('Java')
  })

  it('shows an honest empty state and retries an API problem', async () => {
    mockedListTopics
      .mockRejectedValueOnce(new ApiProblem({ title: '暂时不可用', status: 503, detail: '请稍后重试' }))
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, total: 0, totalPages: 0 })
      .mockResolvedValue(topics)
    const { wrapper } = await mountPage('/topics?page=0&size=20')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('请稍后重试')

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有公开专题')
  })

  it('aborts a stale page request and keeps the newest page response', async () => {
    let resolveOld!: (value: PageResponse<PublicTopicSummaryResponse>) => void
    let resolveNew!: (value: PageResponse<PublicTopicSummaryResponse>) => void
    let oldSignal: AbortSignal | undefined
    mockedListTopics
      .mockImplementationOnce((_params, signal) => {
        oldSignal = signal
        return new Promise((resolve) => { resolveOld = resolve })
      })
      .mockImplementationOnce(() => new Promise((resolve) => { resolveNew = resolve }))
    const { wrapper, router } = await mountPage('/topics?page=0&size=1')
    await router.push('/topics?page=1&size=1')
    await flushPromises()
    expect(oldSignal?.aborted).toBe(true)

    resolveNew({ items: [{ id: 2, name: '最新专题', slug: 'new', description: null, coverUrl: null }], page: 1, size: 1, total: 2, totalPages: 2 })
    await flushPromises()
    resolveOld({ items: [{ id: 1, name: '过期专题', slug: 'old', description: null, coverUrl: null }], page: 0, size: 1, total: 2, totalPages: 2 })
    await flushPromises()
    expect(wrapper.text()).toContain('最新专题')
    expect(wrapper.text()).not.toContain('过期专题')
  })
})
