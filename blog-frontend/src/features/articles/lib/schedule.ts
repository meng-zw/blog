export function shanghaiIso(local: string, now = Date.now()): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(local)) return null
  const iso = `${local}:00+08:00`
  return Date.parse(iso) > now ? iso : null
}
