import { afterEach, describe, expect, it, vi } from 'vitest'

import { http } from '../../shared/api/http'
import { listTopics } from './api'

describe('topics API', () => {
  afterEach(() => { vi.restoreAllMocks() })

  it('sends backend page and size query values with the caller signal', async () => {
    const response = { items: [], page: 2, size: 12, total: 0, totalPages: 0 }
    const get = vi.spyOn(http, 'get').mockResolvedValue(response)
    const controller = new AbortController()

    await expect(listTopics({ page: 2, size: 12 }, controller.signal)).resolves.toEqual(response)
    expect(get).toHaveBeenCalledWith('/public/topics', {
      query: { page: 2, size: 12 }, signal: controller.signal
    })
  })
})
