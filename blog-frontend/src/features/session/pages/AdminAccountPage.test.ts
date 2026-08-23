import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { changePassword } from '../api'
import AdminAccountPage from './AdminAccountPage.vue'

vi.mock('../api', () => ({
  getSession: vi.fn(), loginSession: vi.fn(), logoutSession: vi.fn(), changePassword: vi.fn()
}))

describe('admin account page', () => {
  beforeEach(() => {
    vi.mocked(changePassword).mockReset()
  })

  it('uses password autocomplete and enforces the 12 to 72 character confirmation contract', async () => {
    const wrapper = mount(AdminAccountPage, { attachTo: document.body })
    expect(wrapper.get('input[name="currentPassword"]').attributes('autocomplete')).toBe('current-password')
    expect(wrapper.get('input[name="newPassword"]').attributes()).toMatchObject({ minlength: '12', maxlength: '72', autocomplete: 'new-password' })

    await wrapper.get('input[name="currentPassword"]').setValue('current-secret')
    await wrapper.get('input[name="newPassword"]').setValue('short')
    await wrapper.get('input[name="confirmation"]').setValue('different')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('12 至 72')
    expect(changePassword).not.toHaveBeenCalled()
    for (const input of wrapper.findAll('input[type="password"]')) {
      expect((input.element as HTMLInputElement).value).toBe('')
    }
    expect(document.activeElement).toBe(wrapper.get('input[name="newPassword"]').element)
    wrapper.unmount()
  })

  it('clears secrets after confirmation validation and focuses the confirmation field', async () => {
    const wrapper = mount(AdminAccountPage, { attachTo: document.body })
    await wrapper.get('input[name="currentPassword"]').setValue('current-secret')
    await wrapper.get('input[name="newPassword"]').setValue('new-password-2026')
    await wrapper.get('input[name="confirmation"]').setValue('different-password-2026')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('不一致')
    for (const input of wrapper.findAll('input[type="password"]')) {
      expect((input.element as HTMLInputElement).value).toBe('')
    }
    expect(document.activeElement).toBe(wrapper.get('input[name="confirmation"]').element)
    wrapper.unmount()
  })

  it('clears all entered secrets when the current password is missing and focuses it', async () => {
    const wrapper = mount(AdminAccountPage, { attachTo: document.body })
    await wrapper.get('input[name="newPassword"]').setValue('new-password-2026')
    await wrapper.get('input[name="confirmation"]').setValue('new-password-2026')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('当前密码')
    expect(changePassword).not.toHaveBeenCalled()
    for (const input of wrapper.findAll('input[type="password"]')) {
      expect((input.element as HTMLInputElement).value).toBe('')
    }
    expect(document.activeElement).toBe(wrapper.get('input[name="currentPassword"]').element)
    wrapper.unmount()
  })

  it('clears secrets after a server error and returns focus to the current password', async () => {
    vi.mocked(changePassword).mockRejectedValue(new Error('offline'))
    const wrapper = mount(AdminAccountPage, { attachTo: document.body })
    await wrapper.get('input[name="currentPassword"]').setValue('current-secret')
    await wrapper.get('input[name="newPassword"]').setValue('new-password-2026')
    await wrapper.get('input[name="confirmation"]').setValue('new-password-2026')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('检查网络')
    for (const input of wrapper.findAll('input[type="password"]')) {
      expect((input.element as HTMLInputElement).value).toBe('')
    }
    expect(document.activeElement).toBe(wrapper.get('input[name="currentPassword"]').element)
    wrapper.unmount()
  })

  it('sends the typed request and clears every password field after success', async () => {
    vi.mocked(changePassword).mockResolvedValue()
    const wrapper = mount(AdminAccountPage)
    await wrapper.get('input[name="currentPassword"]').setValue('current-secret')
    await wrapper.get('input[name="newPassword"]').setValue('new-password-2026')
    await wrapper.get('input[name="confirmation"]').setValue('new-password-2026')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: 'current-secret', newPassword: 'new-password-2026', confirmation: 'new-password-2026'
    })
    for (const input of wrapper.findAll('input[type="password"]')) {
      expect((input.element as HTMLInputElement).value).toBe('')
    }
    expect(wrapper.get('[role="status"]').text()).toContain('密码已更新')
  })
})
