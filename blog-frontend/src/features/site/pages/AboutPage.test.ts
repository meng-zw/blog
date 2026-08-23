import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createPublicProfileContext, publicProfileKey } from '../public-profile'
import AboutPage from './AboutPage.vue'
import { loadSiteProfile } from '../api'

vi.mock('../api', () => ({ loadSiteProfile: vi.fn() }))

const mockedLoadProfile = vi.mocked(loadSiteProfile)

describe('about page', () => {
  beforeEach(() => mockedLoadProfile.mockReset())

  it('loads the public profile directly, updates shared branding and keeps the badge square', async () => {
    mockedLoadProfile.mockResolvedValue({
      siteTitle: '山中笔记', subtitle: '且听风吟', nickname: '小M', bio: '保持思考。',
      avatarUrl: '/images/xiao-m-mark.png', githubUrl: 'https://github.com/meng-zw'
    })
    const context = createPublicProfileContext()
    const wrapper = mount(AboutPage, { global: { provide: { [publicProfileKey as symbol]: context } } })
    await flushPromises()

    expect(wrapper.get('h1').text()).toContain('小M')
    expect(wrapper.get('img').classes()).toContain('about-page__badge')
    expect(wrapper.get('a[href="https://github.com/meng-zw"]').attributes()).toMatchObject({ target: '_blank', rel: 'noopener noreferrer' })
    expect(context.profile.value.siteTitle).toBe('山中笔记')
  })
})
