import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ArticleDetailResponse } from '../../../shared/api/contracts'
import { ApiProblem } from '../../../shared/api/problem'
import { ARTICLE_SCHEMA_ID } from '../../../shared/lib/seo'
import ArticleDetailPage from './ArticleDetailPage.vue'
import { loadArticle } from '../api'

vi.mock('../api', () => ({ loadArticle: vi.fn() }))

const article: ArticleDetailResponse = {
  id: 7,
  slug: 'steady-work',
  title: '缓慢而稳定地工作',
  summary: '关于节奏、注意力与长期主义。',
  contentType: 'ARTICLE',
  publishedAt: '2026-08-20T08:00:00Z',
  coverUrl: '/media/steady.jpg',
  category: { id: 1, name: '思考', slug: 'thinking' },
  tags: [{ id: 2, name: '长期主义', slug: 'long-term' }],
  topic: { id: 3, name: '工作方法', slug: 'work-methods' },
  renderedHtml: '<h2>找到节奏</h2><p>正文 <a href="https://outside.example">参考</a></p><h2>继续前行</h2>',
  seoTitle: '缓慢而稳定地工作',
  seoDescription: '稳定工作的方法',
  attachments: [],
  previous: { id: 6, slug: 'before', title: '上一篇', summary: '前文', contentType: 'ARTICLE', publishedAt: '2026-08-19T08:00:00Z', coverUrl: null, category: null, tags: [] },
  next: { id: 8, slug: 'after', title: '下一篇', summary: '后文', contentType: 'ARTICLE', publishedAt: '2026-08-21T08:00:00Z', coverUrl: null, category: null, tags: [] }
}

const mockedLoadArticle = vi.mocked(loadArticle)
const StubPage = defineComponent({ template: '<div />' })

async function mountPage(slug = 'steady-work') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/articles', component: StubPage },
      { path: '/articles/:slug', component: ArticleDetailPage },
      { path: '/topics/:slug', component: StubPage }
    ]
  })
  await router.push(`/articles/${slug}`)
  await router.isReady()
  return { wrapper: mount(ArticleDetailPage, { global: { plugins: [router] } }), router }
}

describe('article detail page', () => {
  beforeEach(() => mockedLoadArticle.mockReset())
  afterEach(() => document.head.querySelectorAll('[data-public-seo]').forEach((node) => node.remove()))

  it('renders safe HTML, stable TOC, taxonomy and previous/next navigation', async () => {
    mockedLoadArticle.mockResolvedValue(article)
    const { wrapper } = await mountPage()
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe(article.title)
    expect(wrapper.text()).toContain(article.summary)
    expect(wrapper.get('a[href="#找到节奏"]').text()).toBe('找到节奏')
    expect(wrapper.get('#找到节奏').text()).toBe('找到节奏')
    expect(wrapper.get('a[href="https://outside.example"]').attributes()).toMatchObject({ target: '_blank', rel: 'noopener noreferrer' })
    expect(wrapper.get('a[href="/topics/work-methods"]').text()).toContain('工作方法')
    expect(wrapper.get('a[href="/articles/before"]').text()).toContain('上一篇')
    expect(wrapper.get('a[href="/articles/after"]').text()).toContain('下一篇')
  })

  it('omits attachments when there are none and renders public download metadata when present', async () => {
    mockedLoadArticle.mockResolvedValueOnce(article).mockResolvedValueOnce({
      ...article,
      attachments: [{ mediaId: 15, displayName: '效率工具包.zip', contentType: 'application/zip', byteSize: 1_572_864, downloadUrl: '/api/media/assets/15/download' }]
    })
    const first = await mountPage()
    await flushPromises()
    expect(first.wrapper.find('section[aria-label="文章附件"]').exists()).toBe(false)
    first.wrapper.unmount()

    const second = await mountPage()
    await flushPromises()
    const section = second.wrapper.get('section[aria-label="文章附件"]')
    expect(section.text()).toContain('效率工具包.zip')
    expect(section.text()).toContain('1.5 MiB')
    expect(section.text()).toContain('application/zip')
    expect(section.get('a').attributes()).toMatchObject({ href: '/api/media/assets/15/download', download: '效率工具包.zip', 'aria-label': '下载附件：效率工具包.zip' })
  })

  it('sets canonical, Open Graph and a single Article JSON-LD record', async () => {
    mockedLoadArticle.mockResolvedValue(article)
    const { wrapper } = await mountPage()
    await flushPromises()

    expect(document.querySelector('link[rel="canonical"]')?.getAttribute('href')).toMatch(/\/articles\/steady-work$/)
    expect(document.querySelector('meta[property="og:title"]')?.getAttribute('content')).toBe(article.seoTitle)
    expect(document.querySelector('meta[property="og:image"]')?.getAttribute('content')).toMatch(/\/media\/steady\.jpg$/)
    expect(document.querySelectorAll(`#${ARTICLE_SCHEMA_ID}`)).toHaveLength(1)
    wrapper.unmount()
  })

  it('shows a distinct not-found state for a missing article', async () => {
    mockedLoadArticle
      .mockRejectedValueOnce(new ApiProblem({ title: 'Not Found', status: 404, detail: 'article not found: missing' }))
      .mockResolvedValue(article)
    const { wrapper } = await mountPage('missing')
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('文章未找到')
    expect(wrapper.text()).toContain('返回文章列表')
    expect(wrapper.find('button').exists()).toBe(false)
    wrapper.unmount()
  })

  it('retries a recoverable failure', async () => {
    mockedLoadArticle
      .mockRejectedValueOnce(new ApiProblem({ title: '服务不可用', status: 503, detail: '请稍后再试' }))
      .mockResolvedValueOnce(article)
    const { wrapper } = await mountPage()
    await flushPromises()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('h1').text()).toBe(article.title)
  })

  it('clears the previous article and Article SEO immediately when the slug changes', async () => {
    let resolveNext!: (value: ArticleDetailResponse) => void
    const nextArticle: ArticleDetailResponse = {
      ...article, id: 9, slug: 'next-slug', title: '新的文章', summary: '新的摘要',
      seoTitle: '新的 SEO 标题', coverUrl: null, renderedHtml: '<h2 id="new-section">新章节</h2>'
    }
    mockedLoadArticle
      .mockResolvedValueOnce(article)
      .mockReturnValueOnce(new Promise((resolve) => { resolveNext = resolve }))
      .mockResolvedValue(nextArticle)
    const { wrapper, router } = await mountPage()
    await flushPromises()
    expect(document.getElementById(ARTICLE_SCHEMA_ID)?.textContent).toContain(article.title)

    await router.push('/articles/next-slug')
    await flushPromises()

    expect(wrapper.text()).not.toContain(article.title)
    expect(wrapper.get('[role="status"]').attributes('aria-live')).toBe('polite')
    expect(document.getElementById(ARTICLE_SCHEMA_ID)).toBeNull()
    expect(document.querySelector('link[rel="canonical"]')?.getAttribute('href')).toMatch(/\/articles\/next-slug$/)
    expect(document.querySelector('meta[property="og:image"]')).toBeNull()

    resolveNext(nextArticle)
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe(nextArticle.title)
    expect(document.getElementById(ARTICLE_SCHEMA_ID)?.textContent).toContain(nextArticle.title)
  })
})
