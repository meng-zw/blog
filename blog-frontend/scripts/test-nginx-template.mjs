import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dockerfile = readFileSync(resolve(root, 'Dockerfile'), 'utf8')
const template = readFileSync(resolve(root, 'nginx/default.conf.template'), 'utf8')

if (!/NGINX_ENVSUBST_FILTER=.*BACKEND_HOST.*BACKEND_PORT/.test(dockerfile)) {
  throw new Error('Docker image must restrict envsubst to BACKEND_HOST and BACKEND_PORT')
}
const rendered = template.replaceAll('${BACKEND_HOST}', 'api').replaceAll('${BACKEND_PORT}', '8081')
for (const variable of ['$uri', '$host', '$remote_addr', '$scheme', '$server_port']) {
  if (!rendered.includes(variable)) throw new Error(`Nginx runtime variable was lost: ${variable}`)
}
if (rendered.includes('${BACKEND_')) throw new Error('Backend placeholders remain after rendering')
if (!rendered.includes('proxy_pass http://api:8081')) throw new Error('Backend target was not rendered')
console.log('nginx template contract passed')
