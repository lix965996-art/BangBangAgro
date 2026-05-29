const path = require('path')
const rcedit = require('rcedit')

const projectRoot = path.resolve(__dirname, '..')
const iconPath = path.join(projectRoot, 'build', 'icon.ico')
const targets = [
  path.join(projectRoot, 'dist_electron', 'win-unpacked', 'BangBangAgro.exe')
].filter((target) => require('fs').existsSync(target))

Promise.all(targets.map((target) => rcedit(target, { icon: iconPath })))
  .then(() => {
    targets.forEach((target) => console.log(`Updated icon: ${target}`))
  })
  .catch((error) => {
    console.error(error)
    process.exit(1)
  })
