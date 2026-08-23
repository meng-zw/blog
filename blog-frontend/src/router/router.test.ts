import { describe, expect, it } from 'vitest'

import router, { ROUTE_NAMES } from './index'

describe('application router contract', () => {
  it('does not register visitor identity, publishing or legacy edit routes', () => {
    const forbidden = new Set([
      '/login', '/register', '/profile', '/write', '/share-tool',
      '/article/:id/edit', '/tool/:id/edit'
    ])

    expect(router.getRoutes().filter((route) => forbidden.has(route.path))).toEqual([])
  })

  it('registers lazy public and admin layout shells with typed metadata', () => {
    const records = router.getRoutes()
    const publicLayout = records.find((route) => route.path === '/' && route.children.length > 0)
    const adminLayout = records.find((route) => route.path === '/admin' && route.children.length > 0)
    const home = router.getRoutes().find((route) => route.name === ROUTE_NAMES.home)
    const admin = router.getRoutes().find((route) => route.name === ROUTE_NAMES.adminHome)

    expect(publicLayout?.meta).toMatchObject({ layout: 'public', title: '首页', public: true })
    expect(adminLayout?.meta).toMatchObject({ layout: 'admin', title: '后台概览', requiresAdmin: true })
    expect(typeof publicLayout?.components?.default).toBe('function')
    expect(typeof adminLayout?.components?.default).toBe('function')
    expect(home?.meta).toMatchObject({ layout: 'public', title: '首页', public: true })
    expect(admin?.meta).toMatchObject({ layout: 'admin', title: '后台概览', requiresAdmin: true })
    expect(publicLayout?.children.some((route) => route.name === ROUTE_NAMES.home)).toBe(true)
    expect(adminLayout?.children.some((route) => route.name === ROUTE_NAMES.adminHome)).toBe(true)
  })

  it('uses the public not-found shell for unknown visitor paths', async () => {
    const resolved = router.resolve('/not-a-real-page')

    expect(resolved.name).toBe(ROUTE_NAMES.notFound)
    expect(resolved.meta).toMatchObject({ layout: 'public', title: '页面未找到', public: true })
  })

  it('registers the complete public experience as lazy accessible routes', () => {
    const expected = [
      ROUTE_NAMES.articles, ROUTE_NAMES.articleDetail, ROUTE_NAMES.notes,
      ROUTE_NAMES.topics, ROUTE_NAMES.topicDetail, ROUTE_NAMES.tools,
      ROUTE_NAMES.toolDetail, ROUTE_NAMES.search, ROUTE_NAMES.about, ROUTE_NAMES.notFound
    ]

    for (const name of expected) {
      const route = router.getRoutes().find((record) => record.name === name)
      expect(route?.meta).toMatchObject({ layout: 'public', public: true })
      expect(typeof route?.components?.default).toBe('function')
    }
  })

  it('registers login publicly and every Task 12/13 admin destination behind the guard', () => {
    const login = router.getRoutes().find((route) => route.name === ROUTE_NAMES.adminLogin)
    expect(login?.path).toBe('/admin/login')
    expect(login?.meta).toMatchObject({ layout: 'admin', public: true })
    expect(login?.meta.requiresAdmin).not.toBe(true)

    const protectedNames = [
      ROUTE_NAMES.adminHome, ROUTE_NAMES.adminArticles, ROUTE_NAMES.adminArticleNew,
      ROUTE_NAMES.adminArticleEdit, ROUTE_NAMES.adminTopics, ROUTE_NAMES.adminTaxonomy,
      ROUTE_NAMES.adminTools, ROUTE_NAMES.adminToolNew, ROUTE_NAMES.adminToolEdit,
      ROUTE_NAMES.adminMedia, ROUTE_NAMES.adminSettings, ROUTE_NAMES.adminAccount
    ]
    for (const name of protectedNames) {
      const route = router.getRoutes().find((record) => record.name === name)
      expect(route?.meta).toMatchObject({ layout: 'admin', requiresAdmin: true })
      expect(typeof route?.components?.default).toBe('function')
    }
  })
})
