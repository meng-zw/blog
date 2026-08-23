import { onBeforeUnmount, watchEffect } from 'vue'

export const CANONICAL_ID = 'public-seo-canonical'
export const ARTICLE_SCHEMA_ID = 'public-seo-article-jsonld'
const DESCRIPTION_ID = 'public-seo-description'
const OG_PREFIX = 'public-seo-og-'

export interface ArticleSeo {
  headline: string
  description: string
  datePublished: string
  image?: string | null
}

export interface SeoConfig {
  title: string
  description: string
  path: string
  type?: 'website' | 'article'
  image?: string | null
  article?: ArticleSeo
}

function configuredOrigin(): string | null {
  const configured = import.meta.env.VITE_PUBLIC_SITE_URL?.trim()
  if (!configured) return null
  try {
    const url = new URL(configured)
    return url.protocol === 'https:' ? url.origin : null
  } catch {
    return null
  }
}

function publicOrigin(): string {
  return configuredOrigin() ?? window.location.origin
}

function absoluteUrl(value: string): string {
  return new URL(value, publicOrigin()).toString()
}

function ownedElement<K extends keyof HTMLElementTagNameMap>(tag: K, id: string): HTMLElementTagNameMap[K] {
  const existing = document.getElementById(id)
  if (existing?.tagName.toLowerCase() === tag) return existing as HTMLElementTagNameMap[K]
  existing?.remove()
  const element = document.createElement(tag)
  element.id = id
  element.dataset.publicSeo = 'true'
  document.head.append(element)
  return element
}

function meta(id: string, attribute: 'name' | 'property', key: string, content: string): void {
  if (!document.getElementById(id)) {
    const existing = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
    if (existing) {
      existing.id = id
      existing.dataset.publicSeo = 'true'
    }
  }
  const element = ownedElement('meta', id)
  element.setAttribute(attribute, key)
  element.content = content
}

function removeOwned(id: string): void {
  document.getElementById(id)?.remove()
}

function applySeo(config: SeoConfig): void {
  const canonical = absoluteUrl(config.path)
  document.title = config.title
  const link = ownedElement('link', CANONICAL_ID)
  link.rel = 'canonical'
  link.href = canonical
  meta(DESCRIPTION_ID, 'name', 'description', config.description)
  meta(`${OG_PREFIX}title`, 'property', 'og:title', config.title)
  meta(`${OG_PREFIX}description`, 'property', 'og:description', config.description)
  meta(`${OG_PREFIX}type`, 'property', 'og:type', config.type ?? 'website')
  meta(`${OG_PREFIX}url`, 'property', 'og:url', canonical)
  if (config.image) meta(`${OG_PREFIX}image`, 'property', 'og:image', absoluteUrl(config.image))
  else removeOwned(`${OG_PREFIX}image`)

  if (!config.article) {
    removeOwned(ARTICLE_SCHEMA_ID)
    return
  }
  const script = ownedElement('script', ARTICLE_SCHEMA_ID)
  script.type = 'application/ld+json'
  script.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: config.article.headline,
    description: config.article.description,
    datePublished: config.article.datePublished,
    ...(config.article.image ? { image: absoluteUrl(config.article.image) } : {}),
    author: { '@type': 'Person', name: '小M' },
    publisher: { '@type': 'Organization', name: '小M的思与行' },
    mainEntityOfPage: canonical
  })
}

export function clearSeo(): void {
  document.head.querySelectorAll<HTMLElement>('[data-public-seo]').forEach((element) => element.remove())
}

export function useSeo(config: () => SeoConfig): void {
  watchEffect(() => applySeo(config()))
  onBeforeUnmount(clearSeo)
}
