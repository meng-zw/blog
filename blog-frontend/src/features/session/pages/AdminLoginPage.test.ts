import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { loginSession } from '../api'
import AdminLoginPage from './AdminLoginPage.vue'
import { ApiProblem } from '../../../shared/api/problem'

vi.mock('../api', () => ({
  getSession: vi.fn(), loginSession: vi.fn(), logoutSession: vi.fn()
}))

const mockedLogin = vi.mocked(loginSession)

async function mountLogin(path = '/admin/login') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'home', component: { template: '<p>home</p>' } },
    { path: '/admin/login', name: 'admin-login', component: AdminLoginPage },
    { path: '/admin', name: 'admin-home', component: { template: '<p>admin</p>' } },
    { path: '/admin/settings', name: 'admin-settings', component: { template: '<p>settings</p>' } }
  ] })
  await router.push(path)
  await router.isReady()
  return { wrapper: mount(AdminLoginPage, { global: { plugins: [pinia, router] } }), router }
}

describe('admin login page', () => {
  beforeEach(() => mockedLogin.mockReset())

  it('has accessible required credential fields and validates before requesting', async () => {
    const { wrapper } = await mountLogin()
    const username = wrapper.get('input[name="username"]')
    const password = wrapper.get('input[name="password"]')

    expect(username.attributes()).toMatchObject({ autocomplete: 'username', required: '', maxlength: '100' })
    expect(password.attributes()).toMatchObject({ autocomplete: 'current-password', required: '', maxlength: '72' })
    await wrapper.get('form').trigger('submit').catch(() => undefined)

    expect(wrapper.get('[role="alert"]').text()).toContain('请输入用户名和密码')
    expect(mockedLogin).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toMatch(/注册|找回密码/)
  })

  it('shows the generic 401 detail and trace while clearing the password', async () => {
    mockedLogin.mockRejectedValueOnce(new ApiProblem({
      title: 'Unauthorized', status: 401, detail: '用户名或密码错误', traceId: 'trace-login-401'
    }))
    const { wrapper } = await mountLogin()
    await wrapper.get('input[name="username"]').setValue('admin')
    await wrapper.get('input[name="password"]').setValue('wrong-password')
    await wrapper.get('form').trigger('submit').catch(() => undefined)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('用户名或密码错误')
    expect(wrapper.get('[role="alert"]').text()).toContain('trace-login-401')
    expect(mockedLogin).toHaveBeenCalledOnce()
    expect((wrapper.get('input[name="password"]').element as HTMLInputElement).value).toBe('')
  })

  it('disables submission while busy and follows only a safe redirect', async () => {
    let resolve!: (value: { authenticated: true; username: string; displayName: string }) => void
    mockedLogin.mockReturnValue(new Promise((done) => { resolve = done }))
    const { wrapper, router } = await mountLogin('/admin/login?redirect=/admin/settings%3Ftab=site%23avatar')
    await wrapper.get('input[name="username"]').setValue('admin')
    await wrapper.get('input[name="password"]').setValue('secret-password')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    resolve({ authenticated: true, username: 'admin', displayName: '小M' })
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/admin/settings?tab=site#avatar')
  })
})
