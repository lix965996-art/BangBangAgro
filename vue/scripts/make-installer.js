const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')

const projectRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(projectRoot, '..')
const makensisPath = path.join(
  repoRoot,
  'temp',
  'NSIS_SetupSkin',
  'NSIS_SetupSkin-master',
  'NSIS',
  'makensis.exe'
)
const scriptPath = path.join(projectRoot, 'installer', 'bangbangagro.nsi')

if (!fs.existsSync(makensisPath)) {
  console.error(`makensis.exe not found: ${makensisPath}`)
  process.exit(1)
}

const result = spawnSync(makensisPath, [scriptPath], {
  cwd: projectRoot,
  stdio: 'inherit'
})

if (result.error) {
  console.error(result.error)
  process.exit(1)
}

process.exit(result.status || 0)
