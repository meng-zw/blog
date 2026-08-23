import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'

import AppEmpty from './AppEmpty.vue'
import AppError from './AppError.vue'

describe('shared application states', () => {
  it('announces an error and emits retry from an accessible control', async () => {
    const { emitted } = render(AppError, {
      props: { title: '加载失败', detail: '请检查网络连接', retryLabel: '重新加载' }
    })

    expect(screen.getByRole('alert').textContent).toContain('加载失败')
    expect(screen.getByText('请检查网络连接')).toBeTruthy()
    await fireEvent.click(screen.getByRole('button', { name: '重新加载' }))
    expect(emitted().retry).toHaveLength(1)
  })

  it('announces an empty state and emits its optional action', async () => {
    const { emitted } = render(AppEmpty, {
      props: { title: '暂无内容', description: '稍后再来看看', actionLabel: '返回首页' }
    })

    expect(screen.getByRole('status').textContent).toContain('暂无内容')
    await fireEvent.click(screen.getByRole('button', { name: '返回首页' }))
    expect(emitted().action).toHaveLength(1)
  })
})
