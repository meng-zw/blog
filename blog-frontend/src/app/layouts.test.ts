import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AdminLayout from './admin/AdminLayout.vue'
import PublicLayout from './public/PublicLayout.vue'

const global = {
  stubs: {
    RouterView: { template: '<p>route content</p>' },
    AdminSidebar: true,
    AdminTopbar: true
  }
}

describe('application layouts', () => {
  it.each([
    ['public', PublicLayout],
    ['admin', AdminLayout]
  ] as const)('gives the %s shell a skip link and content landmark', (_name, component) => {
    const wrapper = mount(component, { global })

    expect(wrapper.get('a[href="#main-content"]').text()).toBe('跳到主要内容')
    expect(wrapper.get('main#main-content').attributes('tabindex')).toBe('-1')
    expect(wrapper.get('main#main-content').text()).toContain('route content')
  })
})
