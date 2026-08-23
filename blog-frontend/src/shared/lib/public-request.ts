import { onBeforeUnmount, ref } from 'vue'
import type { Ref } from 'vue'

import { ApiProblem } from '../api/problem'

export interface DisplayError {
  title: string
  detail: string
  traceId?: string
  status?: number
}

export interface PublicRequest<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<DisplayError | null>
  run(loader: (signal: AbortSignal) => Promise<T>): Promise<void>
  cancel(clearData?: boolean): void
}

function toDisplayError(cause: unknown): DisplayError {
  if (cause instanceof ApiProblem) {
    return {
      title: cause.title || '加载失败',
      detail: cause.detail || '内容暂时无法加载。',
      traceId: cause.traceId,
      status: cause.status
    }
  }
  return { title: '加载失败', detail: '内容暂时无法加载，请稍后重试。' }
}

export function usePublicRequest<T>(): PublicRequest<T> {
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<DisplayError | null>(null)
  let controller: AbortController | null = null
  let requestId = 0

  async function run(loader: (signal: AbortSignal) => Promise<T>): Promise<void> {
    controller?.abort()
    const activeController = new AbortController()
    controller = activeController
    const activeId = ++requestId
    loading.value = true
    error.value = null
    try {
      const response = await loader(activeController.signal)
      if (activeId !== requestId || activeController.signal.aborted) return
      data.value = response
    } catch (cause: unknown) {
      if (activeId !== requestId || activeController.signal.aborted) return
      data.value = null
      error.value = toDisplayError(cause)
    } finally {
      if (activeId === requestId && !activeController.signal.aborted) loading.value = false
    }
  }

  function cancel(clearData = false): void {
    requestId += 1
    controller?.abort()
    controller = null
    loading.value = false
    error.value = null
    if (clearData) data.value = null
  }

  onBeforeUnmount(() => {
    cancel()
  })

  return { data, loading, error, run, cancel }
}
