import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import { ARTICLE_SCHEMA_ID, CANONICAL_ID, useSeo } from './seo'

const SeoHarness = defineComponent({
  setup() {
    const article = ref(true)
    useSeo(() => article.value ? {
      title: '第一篇 · 小M的思与行',
      description: '第一篇摘要',
      path: '/articles/first',
      type: 'article',
      image: '/media/cover.jpg',
      article: {
        headline: '第一篇',
        description: '第一篇摘要',
        datePublished: '2026-08-20T08:00:00Z',
        image: '/media/cover.jpg'
      }
    } : {
      title: '工具 · 小M的思与行',
      description: '工具列表',
      path: '/tools',
      type: 'website'
    })
    return { article }
  },
  template: '<div />'
})

describe('SEO metadata', () => {
  afterEach(() => {
    document.head.querySelectorAll('[data-public-seo]').forEach((node) => node.remove())
  })

  it('replaces canonical, Open Graph and Article JSON-LD without duplicates or stale schema', async () => {
    const wrapper = mount(SeoHarness)
    await nextTick()

    expect(document.getElementById(CANONICAL_ID)?.getAttribute('href')).toMatch(/\/articles\/first$/)
    expect(document.querySelector('meta[property="og:type"]')?.getAttribute('content')).toBe('article')
    const schema = JSON.parse(document.getElementById(ARTICLE_SCHEMA_ID)?.textContent ?? '{}') as Record<string, unknown>
    expect(schema).toMatchObject({ '@type': 'Article', headline: '第一篇', author: { name: '小M' } })
    expect(document.querySelectorAll(`#${ARTICLE_SCHEMA_ID}`)).toHaveLength(1)

    wrapper.vm.article = false
    await nextTick()

    expect(document.getElementById(CANONICAL_ID)?.getAttribute('href')).toMatch(/\/tools$/)
    expect(document.querySelector('meta[property="og:type"]')?.getAttribute('content')).toBe('website')
    expect(document.getElementById(ARTICLE_SCHEMA_ID)).toBeNull()
    expect(document.querySelectorAll('meta[property="og:title"]')).toHaveLength(1)

    wrapper.unmount()
    expect(document.querySelector('[data-public-seo]')).toBeNull()
  })
})
