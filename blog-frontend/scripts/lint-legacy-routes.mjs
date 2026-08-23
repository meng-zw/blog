import { spawnSync } from 'node:child_process'

const forbiddenRoutes = String.raw`/register|/profile|/write|/share-tool|favorite|comments?`
const result = spawnSync(
  'rg',
  ['--line-number', '--glob', '!*.test.*', forbiddenRoutes, 'src/router'],
  { cwd: new URL('..', import.meta.url), encoding: 'utf8' }
)

if (result.error) {
  console.error(`Unable to execute rg: ${result.error.message}`)
  process.exit(2)
}

if (result.status === 0) {
  process.stdout.write(result.stdout)
  console.error('Legacy public/community route references are forbidden.')
  process.exit(1)
}

if (result.status === 1) {
  console.log('No legacy public/community routes found.')
  process.exit(0)
}

process.stderr.write(result.stderr)
process.exit(result.status ?? 2)
