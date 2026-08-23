import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { logoutSession } from '../../features/session/api'
import { useSessionStore } from '../../features/session/store'
import AdminTopbar from './AdminTopbar.vue'

vi.mock('../../features/session/api', () => ({
  getSession: vi.fn(), loginSession: vi.fn(), logoutSession: vi.fn(), changePassword: vi.fn()
}))

async function setup(authenticated = true) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useSessionStore()
  if (authenticated) store.accept({ authenticated: true, username: 'admin', displayName: '小M' })
  else store.accept({ authenticated: false, username: null, displayName: null })
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'home', component: { template: '<p />' }, meta: { layout: 'public', title: '首页', public: true } },
    { path: '/admin', name: 'admin-home', component: { template: '<p />' }, meta: { layout: 'admin', title: '后台概览', requiresAdmin: true } }
  ] })
  await router.push('/admin')
  await router.isReady()
  const wrapper = mount(AdminTopbar, { props: { menuOpen: false }, global: { plugins: [pinia, router] } })
  return { wrapper, router, store }
}

describe('admin topbar logout', () => {
  beforeEach(() => vi.mocked(logoutSession).mockReset())

  it('destroys an authenticated session, clears identity and returns to the public homepage', async () => {
    vi.mocked(logoutSession).mockResolvedValue()
    const { wrapper, router, store } = await setup()

    await wrapper.get('.admin-topbar__actions button').trigger('click')
    await flushPromises()

    expect(logoutSession).toHaveBeenCalledOnce()
    expect(store.state).toBe('anonymous')
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('announces whether the mobile navigation disclosure will open or close', async () => {
    const { wrapper } = await setup()
    const menu = wrapper.get('[data-admin-menu-button]')
    expect(menu.attributes('aria-label')).toBe('打开后台导航')

    await wrapper.setProps({ menuOpen: true })
    expect(menu.attributes('aria-label')).toBe('关闭后台导航')
  })

  it('returns home without a network request when a later 401 already made the state anonymous', async () => {
    const { wrapper, router } = await setup(false)

    await wrapper.get('.admin-topbar__actions button').trigger('click')
    await flushPromises()

    expect(logoutSession).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('surfaces network failure while still clearing stale sensitive identity', async () => {
    vi.mocked(logoutSession).mockRejectedValueOnce(new Error('offline'))
    const { wrapper, router, store } = await setup()

    await wrapper.get('.admin-topbar__actions button').trigger('click')
    await flushPromises()

    expect(store.state).toBe('anonymous')
    expect(store.session).toBeNull()
    expect(store.logoutNotice).toContain('本机已清除管理身份')
    expect(router.currentRoute.value.fullPath).toBe('/')
  })
})
