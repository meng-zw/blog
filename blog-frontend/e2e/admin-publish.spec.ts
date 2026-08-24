import { expect, test } from '@playwright/test'

test('administrator publishes an article and logout protects the editor', async ({ page }) => {
  const title = `发布流程验收 ${Date.now()}`
  const image = {
    name: 'e2e-image.png',
    mimeType: 'image/png',
    // A valid 1×1 transparent PNG. The backend verifies its signature and decodes it.
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL9WQAAAABJRU5ErkJggg==', 'base64')
  }
  const attachment = { name: '公开说明.txt', mimeType: 'text/plain', buffer: Buffer.from('公开附件验收内容', 'utf8') }

  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill(process.env.E2E_ADMIN_USERNAME ?? 'admin')
  await page.getByLabel('密码').fill(process.env.E2E_ADMIN_PASSWORD ?? 'Admin_test_password_2026')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin(?:\/)?$/)

  await page.goto('/admin/articles/new')
  await page.getByLabel('标题').fill(title)
  await page.getByLabel('摘要').fill('由真实浏览器、真实 API 与真实 MySQL 验证的发布流程。')
  await page.locator('.vditor-ir pre[contenteditable="true"]').fill('# 发布流程验收\n\n这是一篇端到端验收文章。')
  await page.locator('.vditor-toolbar input[type="file"]').setInputFiles(image)
  await expect(page.locator('.vditor-ir')).toContainText('/api/media/assets/')
  await page.getByLabel('文章附件').setInputFiles(attachment)
  await expect(page.getByLabel('已添加附件')).toContainText('公开说明.txt')
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByRole('status')).toContainText('草稿已保存')
  await page.getByRole('button', { name: '发布设置' }).click()
  await page.getByRole('button', { name: '立即发布' }).click()
  await expect(page.getByRole('status')).toContainText('已发布')

  const editorUrl = page.url()
  const articleId = editorUrl.match(/\/admin\/articles\/(\d+)\/edit/)?.[1]
  expect(articleId).toBeTruthy()
  const response = await page.request.get(`/api/admin/articles/${articleId}`)
  expect(response.ok()).toBeTruthy()
  const article = await response.json() as { slug: string, markdownContent: string, attachments: Array<{ mediaId: number, downloadUrl: string }> }
  expect(article.markdownContent).toContain('/api/media/assets/')
  expect(article.attachments).toHaveLength(1)
  await page.goto(`/articles/${article.slug}`)
  await expect(page.getByRole('heading', { name: title })).toBeVisible()

  await page.goto('/admin')
  await page.getByRole('button', { name: '退出' }).click()
  await page.goto(`/articles/${article.slug}`)
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  const imagePath = article.markdownContent.match(/\/api\/media\/assets\/\d+/)?.[0]
  expect(imagePath).toBeTruthy()
  const imageResponse = await page.request.get(imagePath!, { maxRedirects: 0 })
  expect(imageResponse.status()).toBe(302)
  expect(imageResponse.headers().location).toBeTruthy()
  const downloadResponse = await page.request.get(article.attachments[0]!.downloadUrl)
  expect(downloadResponse.ok()).toBeTruthy()
  expect(downloadResponse.headers()['content-disposition']).toContain('attachment')
  await page.goto(editorUrl)
  await expect(page).toHaveURL(/\/admin\/login\?redirect=/)
})
