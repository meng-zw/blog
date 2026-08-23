export type FieldErrors = Readonly<Record<string, readonly string[]>>

export interface ApiProblemInit {
  type?: string
  title: string
  status: number
  detail: string
  instance?: string
  traceId?: string
  errors?: FieldErrors
}

export class ApiProblem extends Error {
  readonly type?: string
  readonly title: string
  readonly status: number
  readonly detail: string
  readonly instance?: string
  readonly traceId?: string
  readonly errors?: FieldErrors

  constructor(problem: ApiProblemInit) {
    super(problem.detail || problem.title)
    this.name = 'ApiProblem'
    this.type = problem.type
    this.title = problem.title
    this.status = problem.status
    this.detail = problem.detail
    this.instance = problem.instance
    this.traceId = problem.traceId
    this.errors = problem.errors
  }
}

export type ApiRequestErrorKind = 'network' | 'invalid-response'

export class ApiRequestError extends Error {
  readonly kind: ApiRequestErrorKind
  readonly status?: number

  constructor(kind: ApiRequestErrorKind, status?: number) {
    super(kind === 'network' ? 'Network request failed' : 'The server returned an invalid response')
    this.name = 'ApiRequestError'
    this.kind = kind
    this.status = status
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringValue(record: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record[key]
    if (typeof value === 'string' && value.trim()) return value
  }
  return undefined
}

function statusValue(value: unknown, fallbackStatus: number): number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 100 && value <= 599
    ? value
    : fallbackStatus
}

function fieldErrors(value: unknown): FieldErrors | undefined {
  if (!isRecord(value)) return undefined
  const parsed: Record<string, readonly string[]> = {}
  for (const [field, messages] of Object.entries(value)) {
    if (typeof messages === 'string' && messages.trim()) {
      parsed[field] = [messages]
      continue
    }
    if (Array.isArray(messages)) {
      const strings = messages.filter((message): message is string => typeof message === 'string' && Boolean(message.trim()))
      if (strings.length) parsed[field] = strings
    }
  }
  return Object.keys(parsed).length ? parsed : undefined
}

export function apiProblemFrom(value: unknown, fallbackStatus: number): ApiProblem {
  if (!isRecord(value)) {
    return new ApiProblem({
      title: 'Request failed',
      status: fallbackStatus,
      detail: 'The request could not be completed.'
    })
  }

  return new ApiProblem({
    type: stringValue(value, 'type'),
    title: stringValue(value, 'title', 'error') ?? 'Request failed',
    status: statusValue(value.status, fallbackStatus),
    detail: stringValue(value, 'detail', 'message') ?? 'The request could not be completed.',
    instance: stringValue(value, 'instance'),
    traceId: stringValue(value, 'traceId', 'trace_id'),
    errors: fieldErrors(value.errors)
  })
}
