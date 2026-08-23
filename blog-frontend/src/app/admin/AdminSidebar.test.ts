import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import AdminSidebar from './AdminSidebar.vue'

describe('admin sidebar', () => {
  let changeListener: ((event: MediaQueryListEvent) => void) | undefined

  beforeEach(() => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({
      matches: false,
      media: '(min-width: 960px)',
      onchange: null,
      addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => { changeListener = listener },
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn()
    })))
  })

  afterEach(() => {
    document.body.style.overflow = ''
    document.querySelector('[data-test-menu-button]')?.remove()
    vi.unstubAllGlobals()
  })

  async function mountSidebar() {
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/', name: 'home', component: { template: '<p />' } },
      { path: '/admin', name: 'admin-home', component: { template: '<p />' } },
      { path: '/admin/articles', name: 'admin-articles', component: { template: '<p />' } },
      { path: '/admin/topics', name: 'admin-topics', component: { template: '<p />' } },
      { path: '/admin/taxonomy', name: 'admin-taxonomy', component: { template: '<p />' } },
      { path: '/admin/tools', name: 'admin-tools', component: { template: '<p />' } },
      { path: '/admin/media', name: 'admin-media', component: { template: '<p />' } },
      { path: '/admin/settings', name: 'admin-settings', component: { template: '<p />' } },
      { path: '/admin/account', name: 'admin-account', component: { template: '<p />' } }
    ] })
    await router.push('/admin/media')
    await router.isReady()
    const wrapper = mount(AdminSidebar, { props: { open: true }, global: { plugins: [router] } })
    return { wrapper, router }
  }

  it('marks the active destination and exposes all eight admin destinations', async () => {
    const { wrapper } = await mountSidebar()
    expect(wrapper.get('a[href="/admin/media"]').attributes('aria-current')).toBe('page')
    expect(wrapper.findAll('nav a')).toHaveLength(8)
  })

  it('closes on Escape, route changes and the desktop breakpoint, restoring scroll on unmount', async () => {
    document.body.style.overflow = 'clip'
    const { wrapper, router } = await mountSidebar()
    expect(document.body.style.overflow).toBe('hidden')

    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.setProps({ open: true })
    await router.push('/admin/settings')
    await flushPromises()
    changeListener?.({ matches: true } as MediaQueryListEvent)
    const closeEvents = wrapper.emitted('close')?.length
    wrapper.unmount()

    expect(closeEvents).toBeGreaterThanOrEqual(4)
    expect(document.body.style.overflow).toBe('clip')
  })

  it('removes the closed mobile drawer from the accessibility tree and restores disclosure focus', async () => {
    const menuButton = document.createElement('button')
    menuButton.dataset.adminMenuButton = ''
    menuButton.dataset.testMenuButton = ''
    document.body.append(menuButton)
    const { wrapper } = await mountSidebar()

    await wrapper.setProps({ open: false })
    await flushPromises()

    expect(wrapper.get('aside').attributes('inert')).toBe('true')
    expect(wrapper.get('aside').attributes('aria-hidden')).toBe('true')
    expect(document.activeElement).toBe(menuButton)

    changeListener?.({ matches: true } as MediaQueryListEvent)
    await flushPromises()
    expect(wrapper.get('aside').attributes('inert')).toBeUndefined()
    expect(wrapper.get('aside').attributes('aria-hidden')).toBeUndefined()
  })
})
