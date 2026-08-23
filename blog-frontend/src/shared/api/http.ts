import { ApiProblem, ApiRequestError, apiProblemFrom } from './problem'

export type QueryPrimitive = string | number | boolean | Date | null | undefined
export type QueryValue = QueryPrimitive | readonly QueryPrimitive[]
export type Query = Readonly<Record<string, QueryValue>>

export interface RequestOptions {
  query?: Query
  headers?: HeadersInit
  signal?: AbortSignal
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'
type UnauthorizedHandler = (problem: ApiProblem) => void

const UNSAFE_METHODS: ReadonlySet<HttpMethod> = new Set(['POST', 'PUT', 'DELETE'])
let unauthorizedHandler: UnauthorizedHandler | null = null

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

function appendQueryValue(search: URLSearchParams, key: string, value: QueryPrimitive): void {
  if (value === undefined || value === null) return
  search.append(key, value instanceof Date ? value.toISOString() : String(value))
}

function isQueryArray(value: QueryValue): value is readonly QueryPrimitive[] {
  return Array.isArray(value)
}

function requestUrl(path: string, query?: Query): string {
  const withLeadingSlash = path.startsWith('/') ? path : `/${path}`
  const basePath = withLeadingSlash === '/api' || withLeadingSlash.startsWith('/api/')
    ? withLeadingSlash
    : `/api${withLeadingSlash}`
  if (!query) return basePath

  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (isQueryArray(value)) {
      for (const item of value) appendQueryValue(search, key, item)
    } else {
      appendQueryValue(search, key, value)
    }
  }
  const encoded = search.toString()
  return encoded ? `${basePath}?${encoded}` : basePath
}

function csrfToken(): string | undefined {
  if (typeof document === 'undefined') return undefined
  for (const part of document.cookie.split(';')) {
    const separator = part.indexOf('=')
    const name = (separator < 0 ? part : part.slice(0, separator)).trim()
    if (name !== 'XSRF-TOKEN') continue
    const encoded = separator < 0 ? '' : part.slice(separator + 1)
    try {
      return decodeURIComponent(encoded)
    } catch {
      return encoded
    }
  }
  return undefined
}

function isPassThroughBody(value: unknown): value is BodyInit {
  if (typeof FormData !== 'undefined' && value instanceof FormData) return true
  if (typeof URLSearchParams !== 'undefined' && value instanceof URLSearchParams) return true
  if (typeof Blob !== 'undefined' && value instanceof Blob) return true
  if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) return true
  if (typeof ReadableStream !== 'undefined' && value instanceof ReadableStream) return true
  return false
}

function contentTypeIsJson(value: string | null): boolean {
  if (!value) return false
  const mime = value.split(';', 1)[0]?.trim().toLowerCase()
  return mime === 'application/json' || Boolean(mime?.endsWith('+json'))
}

function mapJsonKeys(value: unknown, keyMapper: (key: string) => string): unknown {
  if (Array.isArray(value)) return value.map((item) => mapJsonKeys(item, keyMapper))
  if (value === null || typeof value !== 'object' || value instanceof Date) return value

  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [keyMapper(key), mapJsonKeys(item, keyMapper)])
  )
}

function toCamelCase(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, character: string) => character.toUpperCase())
}

function toSnakeCase(key: string): string {
  return key.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204 || response.status === 205) return undefined as T

  const json = contentTypeIsJson(response.headers.get('Content-Type'))
  if (!response.ok) {
    if (!json) {
      const problem = apiProblemFrom(undefined, response.status)
      if (problem.status === 401) unauthorizedHandler?.(problem)
      throw problem
    }
    let payload: unknown
    try {
      payload = await response.json()
    } catch {
      const problem = apiProblemFrom(undefined, response.status)
      if (problem.status === 401) unauthorizedHandler?.(problem)
      throw problem
    }
    const problem = apiProblemFrom(mapJsonKeys(payload, toCamelCase), response.status)
    if (problem.status === 401) unauthorizedHandler?.(problem)
    throw problem
  }

  if (json) {
    try {
      const payload: unknown = await response.json()
      return mapJsonKeys(payload, toCamelCase) as T
    } catch {
      throw new ApiRequestError('invalid-response', response.status)
    }
  }

  try {
    return await response.text() as T
  } catch {
    throw new ApiRequestError('invalid-response', response.status)
  }
}

async function request<T>(method: HttpMethod, path: string, body: unknown, options: RequestOptions): Promise<T> {
  const headers = new Headers(options.headers)
  const token = UNSAFE_METHODS.has(method) ? csrfToken() : undefined
  if (token && !headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', token)

  let requestBody: BodyInit | undefined
  if (body !== undefined) {
    if (isPassThroughBody(body)) {
      requestBody = body
    } else if (typeof body === 'string' && headers.has('Content-Type')
      && !contentTypeIsJson(headers.get('Content-Type'))) {
      requestBody = body
    } else {
      requestBody = JSON.stringify(mapJsonKeys(body, toSnakeCase))
      if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    }
  }

  let response: Response
  try {
    response = await fetch(requestUrl(path, options.query), {
      method,
      credentials: 'include',
      headers,
      body: requestBody,
      signal: options.signal
    })
  } catch (error: unknown) {
    if (error instanceof ApiProblem || error instanceof ApiRequestError) throw error
    throw new ApiRequestError('network')
  }
  return parseResponse<T>(response)
}

export const http = {
  get<T>(path: string, options: RequestOptions = {}): Promise<T> {
    return request<T>('GET', path, undefined, options)
  },
  post<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    return request<T>('POST', path, body, options)
  },
  put<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    return request<T>('PUT', path, body, options)
  },
  delete<T>(path: string, options: RequestOptions = {}): Promise<T> {
    return request<T>('DELETE', path, undefined, options)
  }
}

export default http
