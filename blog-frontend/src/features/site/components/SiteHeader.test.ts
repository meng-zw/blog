import { mount } from '@vue/test-utils'
import { routeLocationKey } from 'vue-router'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import SiteHeader from './SiteHeader.vue'

function mountHeader(path = '/', attachTo?: HTMLElement) {
  return mount(SiteHeader, {
    ...(attachTo ? { attachTo } : {}),
    global: {
      provide: {
        [routeLocationKey as symbol]: { path } as RouteLocationNormalizedLoaded
      }
    }
  })
}

function installMatchMedia(initialMatches = false) {
  let listener: ((event: MediaQueryListEvent) => void) | undefined
  const mediaQuery = {
    matches: initialMatches,
    media: '(min-width: 768px)',
    onchange: null,
    addEventListener: vi.fn((_type: string, next: (event: MediaQueryListEvent) => void) => {
      listener = next
    }),
    removeEventListener: vi.fn((_type: string, next: (event: MediaQueryListEvent) => void) => {
      if (listener === next) listener = undefined
    }),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn()
  } as unknown as MediaQueryList
  vi.stubGlobal('matchMedia', vi.fn(() => mediaQuery))
  return {
    mediaQuery,
    enterDesktop: () => listener?.({ matches: true } as MediaQueryListEvent)
  }
}

describe('mobile site navigation', () => {
  afterEach(() => {
    document.body.style.overflow = ''
    vi.unstubAllGlobals()
  })

  it.each([
    ['/', '/'],
    ['/articles', '/articles'],
    ['/articles/a-post', '/articles'],
    ['/topics/editorial', '/topics'],
    ['/notes', '/notes'],
    ['/tools/focus', '/tools'],
    ['/about', '/about'],
    ['/search', '/search']
  ])('marks only the matching navigation entry current at %s', (path, currentHref) => {
    const wrapper = mountHeader(path)

    expect(wrapper.get('a[aria-current="page"]').attributes('href')).toBe(currentHref)
    expect(wrapper.findAll('a[aria-current="page"]')).toHaveLength(1)
    wrapper.unmount()
  })

  it('does not mark Home current on an unknown or catch-all path', () => {
    const wrapper = mountHeader('/missing-page')

    expect(wrapper.find('a[aria-current="page"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('exposes the disclosure relationship, closes on Escape and returns focus', async () => {
    const wrapper = mountHeader('/', document.body)
    const button = wrapper.get('button[aria-controls="public-navigation"]')

    expect(button.attributes('aria-expanded')).toBe('false')
    await button.trigger('click')
    expect(button.attributes('aria-expanded')).toBe('true')
    expect(document.body.style.overflow).toBe('hidden')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()

    expect(button.attributes('aria-expanded')).toBe('false')
    expect(document.body.style.overflow).toBe('')
    expect(document.activeElement).toBe(button.element)
    wrapper.unmount()
  })

  it('closes after outside interaction and navigation while restoring prior body overflow', async () => {
    document.body.style.overflow = 'clip'
    const wrapper = mountHeader('/', document.body)
    const button = wrapper.get('button[aria-controls="public-navigation"]')

    await button.trigger('click')
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(document.body.style.overflow).toBe('clip')

    await button.trigger('click')
    const articleLink = wrapper.get('a[href="/articles"]')
    articleLink.element.addEventListener('click', (event) => event.preventDefault(), { once: true })
    await articleLink.trigger('click')
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(document.body.style.overflow).toBe('clip')
    wrapper.unmount()
  })

  it('closes an open menu when the viewport enters desktop and removes the listener', async () => {
    document.body.style.overflow = 'clip'
    const { mediaQuery, enterDesktop } = installMatchMedia()
    const wrapper = mountHeader()
    const button = wrapper.get('button[aria-controls="public-navigation"]')

    await button.trigger('click')
    enterDesktop()
    await wrapper.vm.$nextTick()

    expect(button.attributes('aria-expanded')).toBe('false')
    expect(document.body.style.overflow).toBe('clip')
    wrapper.unmount()
    expect(mediaQuery.removeEventListener).toHaveBeenCalledTimes(1)
  })

  it('restores prior body overflow when unmounted while open', async () => {
    document.body.style.overflow = 'clip'
    const wrapper = mountHeader()

    await wrapper.get('button[aria-controls="public-navigation"]').trigger('click')
    expect(document.body.style.overflow).toBe('hidden')
    wrapper.unmount()

    expect(document.body.style.overflow).toBe('clip')
  })
})
