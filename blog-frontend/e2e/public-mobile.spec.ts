import { expect, test, type Page } from '@playwright/test'

test.use({ viewport: { width: 390, height: 844 } })

async function login(page: Page): Promise<void> {
  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill(process.env.E2E_ADMIN_USERNAME ?? 'admin')
  await page.getByLabel('密码').fill(process.env.E2E_ADMIN_PASSWORD ?? 'Admin_test_password_2026')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin(?:\/)?$/)
}

async function csrfHeader(page: Page): Promise<Record<string, string>> {
  const token = (await page.context().cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')?.value
  if (!token) throw new Error('Authenticated session did not expose an XSRF-TOKEN cookie')
  return { 'X-XSRF-TOKEN': decodeURIComponent(token) }
}

test('fresh database exposes a real published tool to mobile anonymous visitors', async ({ page }) => {
  const suffix = `${Date.now()}-${test.info().workerIndex}`
  const name = `端到端效率工具 ${suffix}`
  const slug = `e2e-tool-${suffix}`
  const articleTitle = `移动端验收文章 ${suffix}`
  const articleSlug = `e2e-article-${suffix}`
  let toolId: number | undefined
  let articleId: number | undefined
  try {
    await login(page)
    const headers = await csrfHeader(page)
    const created = await page.request.post('/api/admin/tools', { headers, data: {
      name, slug, summary: '由真实后台 API 创建的移动端验收工具。',
      description_markdown: '# 端到端效率工具\n\n验证公开工具卡片与详情路由。',
      official_url: 'https://example.com/e2e-tool', cover_media_id: null,
      category_id: null, tag_ids: [], featured: true
    } })
    const createdBody = await created.text()
    expect(created.ok(), createdBody).toBeTruthy()
    toolId = (JSON.parse(createdBody) as { id: number }).id
    const published = await page.request.post(`/api/admin/tools/${toolId}/publish`, { headers })
    expect(published.ok(), await published.text()).toBeTruthy()
    const articleCreated = await page.request.post('/api/admin/articles', { headers, data: {
      title: articleTitle, slug: articleSlug, summary: '移动端公开文章卡片验收。',
      markdown_content: '# 移动端验收文章\n\n真实公开内容。', content_type: 'ARTICLE',
      cover_media_id: null, category_id: null, topic_id: null, tag_ids: [],
      seo_title: null, seo_description: null
    } })
    const articleBody = await articleCreated.text()
    expect(articleCreated.ok(), articleBody).toBeTruthy()
    articleId = (JSON.parse(articleBody) as { id: number }).id
    const articlePublished = await page.request.post(`/api/admin/articles/${articleId}/publish`, { headers })
    expect(articlePublished.ok(), await articlePublished.text()).toBeTruthy()

    await page.getByRole('button', { name: '退出' }).click()
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /在思考中前行/ })).toBeVisible()
    await expect(page.getByRole('link', { name: articleTitle })).toBeVisible()
    await expect(page.getByRole('link', { name })).toBeVisible()
    await page.getByRole('link', { name }).click()
    await expect(page).toHaveURL(new RegExp(`/tools/${slug}$`))
    await expect(page.getByRole('heading', { name })).toBeVisible()
    await page.getByRole('button', { name: /打开导航/ }).click()
    await page.getByRole('link', { name: '关于' }).click()
    await expect(page.getByRole('link', { name: /GitHub/ })).toHaveAttribute('href', 'https://github.com/meng-zw')
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
    await expect(page.getByText(/注册|评论|订阅/)).toHaveCount(0)
  } finally {
    if (toolId || articleId) {
      await login(page)
      const headers = await csrfHeader(page)
      if (articleId) await page.request.post(`/api/admin/articles/${articleId}/archive`, { headers })
      if (toolId) {
        await page.request.post(`/api/admin/tools/${toolId}/archive`, { headers })
        const removed = await page.request.delete(`/api/admin/tools/${toolId}`, { headers })
        expect(removed.ok(), await removed.text()).toBeTruthy()
      }
    }
  }
})
