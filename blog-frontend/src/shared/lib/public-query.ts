import type { LocationQuery, LocationQueryRaw } from 'vue-router'

export function firstQueryValue(value: LocationQuery[string]): string {
  const first = Array.isArray(value) ? value[0] : value
  return typeof first === 'string' ? first : ''
}

export function cleanQueryText(value: LocationQuery[string], maxLength: number): string {
  return firstQueryValue(value).normalize('NFKC').trim().slice(0, maxLength)
}

export function boundedQueryInt(value: LocationQuery[string], fallback: number, minimum: number, maximum: number): number {
  const parsed = Number.parseInt(firstQueryValue(value), 10)
  if (!Number.isFinite(parsed)) return fallback
  return Math.min(maximum, Math.max(minimum, parsed))
}

export function compactQuery(entries: Readonly<Record<string, string | number | undefined>>): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  for (const key of Object.keys(entries).sort()) {
    const value = entries[key]
    if (value === undefined || value === '') continue
    query[key] = String(value)
  }
  return query
}

export function sameQuery(actual: LocationQuery, expected: LocationQueryRaw): boolean {
  const actualParams = new URLSearchParams()
  for (const key of Object.keys(actual).sort()) {
    const value = firstQueryValue(actual[key])
    if (value) actualParams.set(key, value)
  }
  const expectedParams = new URLSearchParams()
  for (const key of Object.keys(expected).sort()) {
    const value = expected[key]
    if (typeof value === 'string' || typeof value === 'number') expectedParams.set(key, String(value))
  }
  return actualParams.toString() === expectedParams.toString()
}

export function queryPath(path: string, query: LocationQueryRaw): string {
  const params = new URLSearchParams()
  for (const key of Object.keys(query).sort()) {
    const value = query[key]
    if (typeof value === 'string' || typeof value === 'number') params.set(key, String(value))
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}
