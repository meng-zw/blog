import { expect, test } from '@playwright/test'

test('administrator publishes an article and logout protects the editor', async ({ page }) => {
  const title = `发布流程验收 ${Date.now()}`

  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill(process.env.E2E_ADMIN_USERNAME ?? 'admin')
  await page.getByLabel('密码').fill(process.env.E2E_ADMIN_PASSWORD ?? 'Admin_test_password_2026')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin(?:\/)?$/)

  await page.goto('/admin/articles/new')
  await page.getByLabel('标题').fill(title)
  await page.getByLabel('摘要').fill('由真实浏览器、真实 API 与真实 MySQL 验证的发布流程。')
  await page.locator('.vditor-ir pre[contenteditable="true"]').fill('# 发布流程验收\n\n这是一篇端到端验收文章。')
  await page.getByRole('button', { name: '发布设置' }).click()
  await page.getByRole('button', { name: '立即发布' }).click()
  await expect(page.getByRole('status')).toContainText('已发布')

  const editorUrl = page.url()
  const articleId = editorUrl.match(/\/admin\/articles\/(\d+)\/edit/)?.[1]
  expect(articleId).toBeTruthy()
  const response = await page.request.get(`/api/admin/articles/${articleId}`)
  expect(response.ok()).toBeTruthy()
  const article = await response.json() as { slug: string }
  await page.goto(`/articles/${article.slug}`)
  await expect(page.getByRole('heading', { name: title })).toBeVisible()

  await page.goto('/admin')
  await page.getByRole('button', { name: '退出' }).click()
  await page.goto(editorUrl)
  await expect(page).toHaveURL(/\/admin\/login\?redirect=/)
})
