import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useSessionStore } from '../../features/session/store'
import AdminLayout from './AdminLayout.vue'

describe('admin layout mobile navigation', () => {
  let changeListener: ((event: MediaQueryListEvent) => void) | undefined
  const removeMediaListener = vi.fn()

  beforeEach(() => {
    setActivePinia(createPinia())
    useSessionStore().accept({ authenticated: true, username: 'admin', displayName: '小M' })
    removeMediaListener.mockReset()
    vi.stubGlobal('matchMedia', vi.fn(() => ({
      matches: false,
      media: '(min-width: 960px)',
      onchange: null,
      addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => { changeListener = listener },
      removeEventListener: removeMediaListener,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn()
    })))
  })

  afterEach(() => {
    document.body.style.overflow = ''
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  async function mountLayout() {
    const view = { template: '<button data-main-control>页面操作</button>' }
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/', name: 'home', component: view },
      { path: '/admin', name: 'admin-home', component: view },
      { path: '/admin/articles', name: 'admin-articles', component: view },
      { path: '/admin/topics', name: 'admin-topics', component: view },
      { path: '/admin/taxonomy', name: 'admin-taxonomy', component: view },
      { path: '/admin/tools', name: 'admin-tools', component: view },
      { path: '/admin/media', name: 'admin-media', component: view },
      { path: '/admin/settings', name: 'admin-settings', component: view },
      { path: '/admin/account', name: 'admin-account', component: view }
    ] })
    await router.push('/admin')
    await router.isReady()
    const wrapper = mount(AdminLayout, { attachTo: document.body, global: { plugins: [router] } })
    return { wrapper, router }
  }

  async function openDrawer(wrapper: Awaited<ReturnType<typeof mountLayout>>['wrapper']) {
    await wrapper.get('[data-admin-menu-button]').trigger('click')
    await flushPromises()
  }

  it('focuses the first sidebar link and makes only the obscured workspace inert', async () => {
    const { wrapper } = await mountLayout()
    await openDrawer(wrapper)

    expect(document.activeElement).toBe(wrapper.get('.admin-sidebar__brand').element)
    expect(wrapper.get('.admin-layout__workspace').attributes()).toMatchObject({ inert: 'true', 'aria-hidden': 'true' })
    expect(wrapper.get('.admin-sidebar').attributes('inert')).toBeUndefined()
    wrapper.unmount()
  })

  it('wraps Tab and Shift+Tab within the open mobile sidebar', async () => {
    const { wrapper } = await mountLayout()
    await openDrawer(wrapper)
    const first = wrapper.get('.admin-sidebar__brand').element as HTMLElement
    const last = wrapper.get('.admin-sidebar__public').element as HTMLElement

    last.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }))
    expect(document.activeElement).toBe(first)

    first.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }))
    expect(document.activeElement).toBe(last)
    wrapper.unmount()
  })

  it('closes on Escape, restores the workspace and returns focus to the disclosure', async () => {
    const { wrapper } = await mountLayout()
    const trigger = wrapper.get('[data-admin-menu-button]').element
    await openDrawer(wrapper)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper.find('.admin-layout__scrim').exists()).toBe(false)
    expect(wrapper.get('.admin-layout__workspace').attributes('inert')).toBeUndefined()
    expect(wrapper.get('.admin-layout__workspace').attributes('aria-hidden')).toBeUndefined()
    expect(document.activeElement).toBe(trigger)
    wrapper.unmount()
  })

  it('closes from overlay, navigation, and desktop breakpoint without retaining inert state', async () => {
    const { wrapper, router } = await mountLayout()
    const trigger = wrapper.get('[data-admin-menu-button]').element
    await openDrawer(wrapper)
    await wrapper.get('.admin-layout__scrim').trigger('click')
    await flushPromises()
    expect(wrapper.get('.admin-layout__workspace').attributes('inert')).toBeUndefined()
    expect(document.activeElement).toBe(trigger)

    await openDrawer(wrapper)
    await wrapper.get('a[href="/admin/media"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/media')
    expect(wrapper.get('.admin-layout__workspace').attributes('inert')).toBeUndefined()
    expect(document.activeElement).toBe(trigger)

    await openDrawer(wrapper)
    changeListener?.({ matches: true } as MediaQueryListEvent)
    await flushPromises()
    expect(wrapper.get('.admin-layout__workspace').attributes('inert')).toBeUndefined()
    expect(wrapper.get('.admin-layout__workspace').attributes('aria-hidden')).toBeUndefined()
    expect(document.body.style.overflow).toBe('')
    wrapper.unmount()
  })

  it('removes drawer listeners, scroll lock, and inert state on unmount', async () => {
    document.body.style.overflow = 'clip'
    const removeDocumentListener = vi.spyOn(document, 'removeEventListener')
    const { wrapper } = await mountLayout()
    await openDrawer(wrapper)
    expect(document.body.style.overflow).toBe('hidden')

    wrapper.unmount()

    expect(document.body.style.overflow).toBe('clip')
    expect(document.querySelector('.admin-layout__workspace')).toBeNull()
    expect(removeDocumentListener).toHaveBeenCalledWith('keydown', expect.any(Function))
    expect(removeDocumentListener).toHaveBeenCalledWith('pointerdown', expect.any(Function))
    expect(removeMediaListener).toHaveBeenCalledWith('change', expect.any(Function))
  })
})
