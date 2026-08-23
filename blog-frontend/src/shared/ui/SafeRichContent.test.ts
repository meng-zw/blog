import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SafeRichContent from './SafeRichContent.vue'

describe('SafeRichContent', () => {
  it('hardens external links without changing internal links after render and update', async () => {
    const wrapper = mount(SafeRichContent, {
      props: {
        html: '<p><a href="/about">站内</a><a href="https://outside.example/path">站外</a></p>'
      }
    })

    expect(wrapper.get('a[href="/about"]').attributes('target')).toBeUndefined()
    expect(wrapper.get('a[href="/about"]').attributes('rel')).toBeUndefined()
    expect(wrapper.get('a[href="https://outside.example/path"]').attributes()).toMatchObject({
      target: '_blank',
      rel: 'noopener noreferrer'
    })

    await wrapper.setProps({
      html: '<h2 id="next">更新</h2><a href="https://another.example">另一个站外链接</a>'
    })

    expect(wrapper.get('a').attributes()).toMatchObject({
      target: '_blank',
      rel: 'noopener noreferrer'
    })
  })
})
