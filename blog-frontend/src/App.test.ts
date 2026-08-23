import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'

import App from './App.vue'
import { useSessionStore } from './features/session/store'

describe('application session notices', () => {
  it('keeps a failed-logout notice accessible after the admin layout unmounts and allows dismissal', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useSessionStore()
    store.$patch({ logoutNotice: '退出请求未能送达，本机已清除管理身份。' })
    const wrapper = mount(App, {
      global: { plugins: [pinia], stubs: { RouterView: { template: '<main>public home</main>' } } }
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('退出请求未能送达')
    expect(wrapper.text()).toContain('public home')
    await wrapper.get('[role="alert"] button').trigger('click')

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
