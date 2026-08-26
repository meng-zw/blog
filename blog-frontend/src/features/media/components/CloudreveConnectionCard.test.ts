import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CloudreveConnectionResponse } from '../../../shared/api/contracts'
import { authorizeCloudreve, disconnectCloudreve, getCloudreveConnection, navigateToCloudreveAuthorization } from '../api'
import CloudreveConnectionCard from './CloudreveConnectionCard.vue'

vi.mock('../api', () => ({
  getCloudreveConnection: vi.fn(),
  authorizeCloudreve: vi.fn(),
  disconnectCloudreve: vi.fn(),
  navigateToCloudreveAuthorization: vi.fn()
}))

const connected: CloudreveConnectionResponse = {
  configured: true,
  status: 'CONNECTED',
  authorizedSubject: 'user-42',
  authorizedDisplayName: 'Cloudreve 管理员',
  grantedScopes: ['Files.Read', 'Files.Write'],
  accessTokenExpiresAt: '2026-08-24T10:15:00Z',
  refreshTokenExpiresAt: '2026-09-24T10:15:00Z',
  rootPath: '/blog',
  trustedInternalAuthorizationOrigin: null
}

describe('CloudreveConnectionCard', () => {
  beforeEach(() => {
    vi.mocked(getCloudreveConnection).mockReset().mockResolvedValue(connected)
    vi.mocked(authorizeCloudreve).mockReset()
    vi.mocked(disconnectCloudreve).mockReset()
    vi.mocked(navigateToCloudreveAuthorization).mockReset()
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  it('shows configuration-missing, disconnected, connected and reauthorization states without sensitive fields', async () => {
    vi.mocked(getCloudreveConnection).mockResolvedValueOnce({ ...connected, configured: false, status: 'DISCONNECTED' })
    const missing = mount(CloudreveConnectionCard)
    await flushPromises()
    expect(missing.text()).toContain('Cloudreve 尚未配置')
    expect(missing.find('button').exists()).toBe(false)

    vi.mocked(getCloudreveConnection).mockResolvedValueOnce({ ...connected, status: 'DISCONNECTED' })
    const disconnected = mount(CloudreveConnectionCard)
    await flushPromises()
    expect(disconnected.get('button').text()).toBe('连接 Cloudreve')

    const connectedWrapper = mount(CloudreveConnectionCard)
    await flushPromises()
    expect(connectedWrapper.text()).toContain('已连接')
    expect(connectedWrapper.text()).toContain('Cloudreve 管理员')
    expect(connectedWrapper.text()).toContain('Files.Read、Files.Write')
    expect(connectedWrapper.text()).toContain('/blog')
    expect(connectedWrapper.findAll('button').map((button) => button.text())).toEqual(['重新授权', '断开连接'])

    vi.mocked(getCloudreveConnection).mockResolvedValueOnce({ ...connected, status: 'REAUTH_REQUIRED' })
    const reauth = mount(CloudreveConnectionCard)
    await flushPromises()
    expect(reauth.text()).toContain('需要重新授权')
    expect(reauth.get('button').text()).toBe('重新授权')

    for (const wrapper of [missing, disconnected, connectedWrapper, reauth]) {
      expect(wrapper.findAll('input').map((input) => input.attributes('name'))).not.toContain('clientSecret')
      expect(wrapper.text()).not.toMatch(/access[_ -]?token|refresh[_ -]?token|client secret|verifier|state/i)
    }
  })

  it('announces loading and fixed errors without displaying internal error details', async () => {
    let resolveStatus!: (value: CloudreveConnectionResponse) => void
    vi.mocked(getCloudreveConnection).mockImplementationOnce(() => new Promise((resolve) => { resolveStatus = resolve }))
    const loading = mount(CloudreveConnectionCard)
    expect(loading.get('[aria-live="polite"]').text()).toContain('正在读取 Cloudreve 连接状态')
    resolveStatus(connected)
    await flushPromises()

    vi.mocked(getCloudreveConnection).mockRejectedValueOnce(new Error('provider response with secret details'))
    const failed = mount(CloudreveConnectionCard)
    await flushPromises()
    expect(failed.get('[role="alert"] p').text()).toBe('无法读取 Cloudreve 连接状态，请检查网络后重试。')
    expect(failed.text()).not.toContain('secret details')

    let resolveRetry!: (value: CloudreveConnectionResponse) => void
    vi.mocked(getCloudreveConnection).mockImplementationOnce(() => new Promise((resolve) => { resolveRetry = resolve }))
    await failed.get('button[aria-label="重试读取 Cloudreve 连接状态"]').trigger('click')
    expect(failed.get('section').attributes('aria-busy')).toBe('true')
    resolveRetry(connected)
    await flushPromises()
    expect(getCloudreveConnection).toHaveBeenCalledTimes(3)
    expect(failed.text()).toContain('Cloudreve 管理员')
  })

  it('uses validated OAuth navigation and reports a fixed authorization error', async () => {
    vi.mocked(authorizeCloudreve).mockResolvedValueOnce('https://cloud.example/session/authorize')
    const wrapper = mount(CloudreveConnectionCard)
    await flushPromises()

    await wrapper.get('button').trigger('click')
    expect(authorizeCloudreve).toHaveBeenCalledOnce()
    expect(navigateToCloudreveAuthorization).toHaveBeenCalledWith('https://cloud.example/session/authorize')

    vi.mocked(authorizeCloudreve).mockRejectedValueOnce(new Error('state=secret'))
    const failed = mount(CloudreveConnectionCard)
    await flushPromises()
    await failed.get('button').trigger('click')
    await flushPromises()
    expect(failed.get('[role="alert"]').text()).toBe('无法发起 Cloudreve 授权，请检查配置后重试。')
    expect(failed.text()).not.toContain('state=secret')
  })

  it('requires confirmation before disconnecting and announces the result', async () => {
    const wrapper = mount(CloudreveConnectionCard)
    await flushPromises()
    vi.mocked(window.confirm).mockReturnValueOnce(false)
    await wrapper.findAll('button').at(-1)!.trigger('click')
    expect(disconnectCloudreve).not.toHaveBeenCalled()

    await wrapper.findAll('button').at(-1)!.trigger('click')
    await flushPromises()
    expect(disconnectCloudreve).toHaveBeenCalledOnce()
    expect(wrapper.get('[aria-live="polite"]').text()).toContain('Cloudreve 已断开连接。')
  })

  it('announces only fixed callback outcomes', async () => {
    const connectedCallback = mount(CloudreveConnectionCard, { props: { callbackOutcome: 'connected' } })
    await flushPromises()
    expect(connectedCallback.get('[aria-live="polite"]').text()).toContain('Cloudreve 已连接。')

    const failedCallback = mount(CloudreveConnectionCard, { props: { callbackOutcome: 'authorization_failed' } })
    await flushPromises()
    expect(failedCallback.get('[aria-live="polite"]').text()).toContain('Cloudreve 授权未完成，请重试。')

    const untrustedCallback = mount(CloudreveConnectionCard, { props: { callbackOutcome: 'state=secret' } })
    await flushPromises()
    expect(untrustedCallback.text()).not.toContain('state=secret')
  })
})
