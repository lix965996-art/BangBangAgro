const { app, BrowserWindow, shell } = require('electron')
const path = require('path')

const appIcon = path.join(__dirname, '..', 'build', 'icon.ico')

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1100,
    minHeight: 720,
    show: false,
    icon: appIcon,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  mainWindow.once('ready-to-show', () => {
    mainWindow.show()
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'))
}

if (process.platform === 'win32') {
  app.setAppUserModelId('com.bangbangagro.desktop')
}

app.whenReady().then(() => {
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
