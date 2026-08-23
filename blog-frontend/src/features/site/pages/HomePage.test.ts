import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import PublicLayout from '../../../app/public/PublicLayout.vue'
import type { HomeResponse } from '../../../shared/api/contracts'
import { ApiProblem } from '../../../shared/api/problem'
import HomePage from './HomePage.vue'
import { loadHome } from '../api'

vi.mock('../api', () => ({ loadHome: vi.fn() }))

const home: HomeResponse = {
  site: {
    siteTitle: '小M的思与行',
    subtitle: '中庸之道',
    nickname: '小M',
    bio: '中庸之道',
    avatarUrl: '/images/xiao-m-mark.png',
    githubUrl: 'https://github.com/meng-zw'
  },
  featuredArticle: {
    id: 1,
    slug: 'knowledge-system',
    title: '如何构建属于自己的知识体系',
    summary: '把零散的阅读和思考，整理成可复用的方法。',
    contentType: 'ARTICLE',
    publishedAt: '2026-08-18T08:00:00Z',
    coverUrl: '/media/knowledge.webp',
    category: { id: 1, name: '学习与成长', slug: 'learning' },
    tags: []
  },
  latestArticles: [{
    id: 2,
    slug: 'notion-notes',
    title: '我如何用 Notion 管理个人知识库',
    summary: '从零搭建的笔记方法。',
    contentType: 'ARTICLE',
    publishedAt: '2026-08-15T08:00:00Z',
    coverUrl: null,
    category: { id: 2, name: '技术随笔', slug: 'technology' },
    tags: []
  }],
  featuredTools: [{
    id: 3,
    slug: 'focus-tool',
    name: '专注时钟',
    summary: '简洁的专注计时工具。',
    officialUrl: 'https://example.com/focus',
    coverUrl: null,
    category: { id: 3, name: '工具与效率', slug: 'tools' },
    tags: [],
    featured: true,
    publishedAt: '2026-08-11T08:00:00Z'
  }],
  topics: [{
    id: 4,
    name: '技术随笔',
    slug: 'technology',
    description: '分享编程、工具与效率',
    coverUrl: null
  }]
}

const mockedLoadHome = vi.mocked(loadHome)

function mountPage() {
  return mount(PublicLayout, {
    global: {
      stubs: {
        RouterView: HomePage
      }
    }
  })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

describe('public home page', () => {
  beforeEach(() => {
    mockedLoadHome.mockReset()
    document.title = ''
  })

  afterEach(() => {
    document.head.querySelector('meta[name="description"]')?.remove()
  })

  it('renders API content in the editorial public shell without visitor account controls', async () => {
    mockedLoadHome.mockResolvedValue(home)

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[aria-label="主导航"]')).toBeTruthy()
    expect(wrapper.text()).toContain('小M的思与行')
    expect(wrapper.text()).toContain('中庸之道')
    expect(wrapper.get('h1').text()).toBe('在思考中前行，在记录中成长')
    expect(wrapper.text()).toContain('如何构建属于自己的知识体系')
    expect(wrapper.text()).toContain('我如何用 Notion 管理个人知识库')
    expect(wrapper.text()).toContain('专注时钟')
    expect(wrapper.text()).toContain('技术随笔')
    expect(wrapper.text()).toContain('关于小M')
    expect(wrapper.findAll('h2').map((heading) => heading.text())).toEqual([
      '本周精选', '最新文章', '推荐工具', '探索更多主题', '关于小M'
    ])
    expect(wrapper.text()).not.toMatch(/注册|登录|评论|收藏|点赞|订阅/)
    expect(document.title).toBe('小M的思与行 · 中庸之道')
    expect(document.head.querySelector('meta[name="description"]')?.getAttribute('content')).toContain('在思考中前行')
  })

  it('updates header and footer branding from the latest site profile', async () => {
    mockedLoadHome.mockResolvedValue({
      ...home,
      site: {
        ...home.site,
        siteTitle: '山中笔记',
        subtitle: '且听风吟',
        nickname: '阿山'
      }
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.site-brand__title').text()).toBe('山中笔记')
    expect(wrapper.get('.site-brand__subtitle').text()).toBe('且听风吟')
    expect(wrapper.get('.site-footer__brand').text()).toBe('山中笔记')
    expect(wrapper.get('.site-footer__tagline').text()).toBe('且听风吟')
    expect(wrapper.get('.site-footer__copyright').text()).toContain('阿山')
  })

  it('renders AVIF and WebP hero sources with explicit decorative image dimensions', async () => {
    mockedLoadHome.mockResolvedValue(home)

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('picture source[type="image/avif"]').attributes('srcset')).toBe('/images/hero-workspace.avif')
    expect(wrapper.get('picture source[type="image/webp"]').attributes('srcset')).toBe('/images/hero-workspace.webp')
    expect(wrapper.get('picture img').attributes()).toMatchObject({
      alt: '', width: '2400', height: '750'
    })
  })

  it('marks the profile GitHub link as an external safe navigation', async () => {
    mockedLoadHome.mockResolvedValue(home)

    const wrapper = mountPage()
    await flushPromises()

    const github = wrapper.get('a[href="https://github.com/meng-zw"]')
    expect(github.attributes('target')).toBe('_blank')
    expect(github.attributes('rel')?.split(' ').sort()).toEqual(['noopener', 'noreferrer'])
  })

  it('announces a loading skeleton while the home request is pending', () => {
    mockedLoadHome.mockReturnValue(new Promise(() => undefined))

    const wrapper = mountPage()

    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载首页')
    expect(wrapper.findAll('[data-skeleton]').length).toBeGreaterThan(2)
  })

  it('shows a recoverable API problem and retries the request', async () => {
    mockedLoadHome
      .mockRejectedValueOnce(new ApiProblem({
        title: '服务暂时不可用',
        status: 503,
        detail: '首页内容暂时无法加载。',
        traceId: 'trace-home-503'
      }))
      .mockResolvedValueOnce(home)

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('首页内容暂时无法加载。')
    expect(wrapper.get('[role="alert"]').text()).toContain('trace-home-503')
    await wrapper.get('.home-error button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('如何构建属于自己的知识体系')
  })

  it('uses meaningful empty states and never invents articles or tools', async () => {
    mockedLoadHome.mockResolvedValue({
      ...home,
      featuredArticle: null,
      latestArticles: [],
      featuredTools: [],
      topics: []
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('还没有公开文章')
    expect(wrapper.text()).not.toContain('如何构建属于自己的知识体系')
    expect(wrapper.text()).not.toContain('专注时钟')
    expect(wrapper.find('[data-section="topics"]').exists()).toBe(false)
    expect(wrapper.find('[data-section="tools"]').exists()).toBe(false)
  })

  it('keeps the newest response when an aborted older retry resolves late', async () => {
    const stale = deferred<HomeResponse>()
    const newest = deferred<HomeResponse>()
    mockedLoadHome
      .mockRejectedValueOnce(new ApiProblem({ title: '失败', status: 503, detail: '请重试' }))
      .mockReturnValueOnce(stale.promise)
      .mockReturnValueOnce(newest.promise)

    const wrapper = mountPage()
    await flushPromises()
    const retry = wrapper.get('.home-error button').element
    retry.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    retry.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    newest.resolve({ ...home, site: { ...home.site, siteTitle: '最新站点' } })
    await flushPromises()
    stale.resolve({ ...home, site: { ...home.site, siteTitle: '过期站点' } })
    await flushPromises()

    expect(wrapper.get('.site-brand__title').text()).toBe('最新站点')
    expect(wrapper.text()).not.toContain('过期站点')
  })

  it('aborts the in-flight home request when unmounted', () => {
    let requestSignal: AbortSignal | undefined
    mockedLoadHome.mockImplementation((signal) => {
      requestSignal = signal
      return new Promise(() => undefined)
    })

    const wrapper = mountPage()
    expect(requestSignal?.aborted).toBe(false)
    wrapper.unmount()

    expect(requestSignal?.aborted).toBe(true)
  })
})
