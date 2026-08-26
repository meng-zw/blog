import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { SiteProfileResponse } from '../../../shared/api/contracts'
import { loadAdminSettings, updateAdminSettings } from '../admin-api'
import { getCloudreveConnection } from '../../media/api'
import { uploadMedia } from '../../media/uploader'
import { readSharedPublicProfile, resetSharedPublicProfile } from '../public-profile'
import AdminSettingsPage from './AdminSettingsPage.vue'

vi.mock('../admin-api', () => ({ loadAdminSettings: vi.fn(), updateAdminSettings: vi.fn() }))
vi.mock('../../media/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../media/api')>()
  return { ...actual, getCloudreveConnection: vi.fn(), authorizeCloudreve: vi.fn(), disconnectCloudreve: vi.fn(), navigateToCloudreveAuthorization: vi.fn() }
})
vi.mock('../../media/uploader', () => ({ uploadMedia: vi.fn() }))

const profile: SiteProfileResponse & { avatarMediaId: number | null } = {
  siteTitle: '小M的思与行', subtitle: '中庸之道', nickname: '小M', bio: '中庸之道',
  githubUrl: 'https://github.com/meng-zw', avatarUrl: '/api/media/existing.png', avatarMediaId: 42
}

describe('admin settings page', () => {
  beforeEach(() => {
    vi.mocked(loadAdminSettings).mockReset().mockResolvedValue(profile)
    vi.mocked(updateAdminSettings).mockReset()
    vi.mocked(uploadMedia).mockReset()
    vi.mocked(getCloudreveConnection).mockReset().mockResolvedValue({
      configured: true, status: 'DISCONNECTED', authorizedSubject: null, authorizedDisplayName: null,
      grantedScopes: [], accessTokenExpiresAt: null, refreshTokenExpiresAt: null, rootPath: '/blog', trustedInternalAuthorizationOrigin: null
    })
    resetSharedPublicProfile()
    window.history.replaceState({}, '', '/admin/settings')
  })

  it('loads only approved profile fields and previews the full square badge', async () => {
    vi.mocked(updateAdminSettings).mockResolvedValue(profile)
    const wrapper = mount(AdminSettingsPage)
    await flushPromises()

    expect((wrapper.get('input[name="siteTitle"]').element as HTMLInputElement).value).toBe('小M的思与行')
    expect(wrapper.findAll('input').map((input) => input.attributes('name')).filter(Boolean)).toEqual([
      'siteTitle', 'subtitle', 'nickname', 'githubUrl', 'avatar'
    ])
    expect(wrapper.get('.admin-avatar-preview').classes()).toContain('admin-avatar-preview--square')
    expect(wrapper.get('.admin-avatar-preview img').attributes('src')).toBe('/api/media/existing.png')
    expect(wrapper.get('input[name="avatar"]').attributes('accept')).toBe('image/png,image/jpeg,image/gif')
    expect(wrapper.find('[name="analytics"]').exists()).toBe(false)

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(updateAdminSettings).toHaveBeenCalledWith(expect.objectContaining({ avatarMediaId: 42 }))
  })

  it('uploads an avatar, saves its media id and propagates the returned public profile', async () => {
    vi.mocked(uploadMedia).mockResolvedValue({
      mediaId: 23, filename: 'new-badge.png', contentType: 'image/png', byteSize: 10, width: 512, height: 512, status: 'READY', purpose: 'AVATAR',
      url: '/api/media/new-badge.png'
    })
    const updated = { ...profile, siteTitle: '山中笔记', avatarUrl: '/api/media/new-badge.png' }
    vi.mocked(updateAdminSettings).mockResolvedValue(updated)
    const wrapper = mount(AdminSettingsPage)
    await flushPromises()

    await wrapper.get('input[name="siteTitle"]').setValue('山中笔记')
    const file = new File(['badge'], 'badge.png', { type: 'image/png' })
    Object.defineProperty(wrapper.get('input[name="avatar"]').element, 'files', { value: [file] })
    await wrapper.get('input[name="avatar"]').trigger('change')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(updateAdminSettings).toHaveBeenCalledWith({
      siteTitle: '山中笔记', subtitle: '中庸之道', nickname: '小M', bio: '中庸之道',
      githubUrl: 'https://github.com/meng-zw', avatarMediaId: 23
    })
    expect(wrapper.get('.admin-avatar-preview img').attributes('src')).toBe('/api/media/new-badge.png')
    expect(readSharedPublicProfile().siteTitle).toBe('山中笔记')
    expect(readSharedPublicProfile()).not.toHaveProperty('avatarMediaId')
  })

  it('passes only the fixed Cloudreve callback outcome from the URL to the connection card', async () => {
    window.history.replaceState({}, '', '/admin/settings?cloudreve=connected&state=secret')
    const wrapper = mount(AdminSettingsPage)
    await flushPromises()

    expect(wrapper.get('[aria-live="polite"]').text()).toContain('Cloudreve 已连接。')
    expect(wrapper.text()).not.toContain('state=secret')
  })
})
