import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dockerfile = readFileSync(resolve(root, 'Dockerfile'), 'utf8')
const template = readFileSync(resolve(root, 'nginx/default.conf.template'), 'utf8')
const validator = resolve(root, 'nginx/validate-media-csp.sh')

if (!/NGINX_ENVSUBST_FILTER=.*BACKEND_HOST.*BACKEND_PORT.*MEDIA_UPLOAD_ORIGIN.*MEDIA_PUBLIC_ORIGINS/.test(dockerfile)) {
  throw new Error('Docker image must restrict envsubst to backend and non-secret media origins')
}
const rendered = template.replaceAll('${BACKEND_HOST}', 'api').replaceAll('${BACKEND_PORT}', '8081')
  .replaceAll('${MEDIA_UPLOAD_ORIGIN}', 'https://account.r2.cloudflarestorage.com')
  .replaceAll('${MEDIA_PUBLIC_ORIGINS}', 'https://images.example.com https://legacy-images.example.com')
for (const variable of ['$uri', '$host', '$remote_addr', '$scheme', '$server_port']) {
  if (!rendered.includes(variable)) throw new Error(`Nginx runtime variable was lost: ${variable}`)
}
if (rendered.includes('${BACKEND_')) throw new Error('Backend placeholders remain after rendering')
if (!rendered.includes('proxy_pass http://api:8081')) throw new Error('Backend target was not rendered')
if (!rendered.includes("connect-src 'self' https://account.r2.cloudflarestorage.com")) {
  throw new Error('R2 upload origin is missing from the rendered connect-src policy')
}
if (!rendered.includes("img-src 'self' data: https://images.example.com https://legacy-images.example.com")) {
  throw new Error('R2 public origins are missing from the rendered img-src policy')
}
if (/connect-src[^;]*\*/.test(rendered) || /img-src[^;]*\*/.test(rendered)) {
  throw new Error('Media CSP must not use wildcard sources')
}
const validOrigins = spawnSync('sh', [validator], { env: { ...process.env,
  MEDIA_UPLOAD_ORIGIN: 'https://account.r2.cloudflarestorage.com',
  MEDIA_PUBLIC_ORIGINS: 'https://images.example.com https://legacy-images.example.com' } })
if (validOrigins.status !== 0) throw new Error('Exact HTTPS media origins must pass startup validation')
const wildcardOrigin = spawnSync('sh', [validator], { env: { ...process.env,
  MEDIA_UPLOAD_ORIGIN: 'https://*.r2.cloudflarestorage.com', MEDIA_PUBLIC_ORIGINS: '' } })
if (wildcardOrigin.status === 0) throw new Error('Wildcard media origins must fail startup validation')
console.log('nginx template contract passed')
