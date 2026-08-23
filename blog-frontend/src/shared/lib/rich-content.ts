export interface TocItem {
  id: string
  text: string
  level: 2 | 3
}

function headingId(text: string, index: number): string {
  const normalized = text.normalize('NFKC').trim().toLowerCase()
    .replace(/[^\p{Letter}\p{Number}\s-]/gu, '')
    .replace(/[\s-]+/g, '-')
    .replace(/^-|-$/g, '')
  return normalized || `section-${index + 1}`
}

export function prepareRichContent(source: string): { html: string; toc: TocItem[] } {
  const documentNode = new DOMParser().parseFromString(source, 'text/html')
  const idCounts = new Map<string, number>()
  documentNode.body.querySelectorAll<HTMLElement>('[id]').forEach((element) => {
    idCounts.set(element.id, (idCounts.get(element.id) ?? 0) + 1)
  })
  const used = new Set(idCounts.keys())
  const toc: TocItem[] = []
  documentNode.body.querySelectorAll<HTMLHeadingElement>('h2, h3').forEach((heading, index) => {
    const backendId = heading.id
    const preserveBackendId = Boolean(backendId)
      && backendId.trim() === backendId
      && !/[\s\u0000-\u001f\u007f]/u.test(backendId)
      && idCounts.get(backendId) === 1
    let id = backendId
    if (!preserveBackendId) {
      const base = headingId(heading.textContent ?? '', index)
      id = base
      for (let suffix = 2; used.has(id); suffix += 1) id = `${base}-${suffix}`
      used.add(id)
    }
    heading.id = id
    toc.push({ id, text: heading.textContent?.trim() || `第 ${index + 1} 节`, level: heading.tagName === 'H2' ? 2 : 3 })
  })
  return { html: documentNode.body.innerHTML, toc }
}
