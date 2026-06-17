const AUTH_KEYS = ['user', 'menus', 'currentPathName']

export function purgeLegacyAuth() {
  AUTH_KEYS.forEach(key => localStorage.removeItem(key))
}

export function clearStoredAuth() {
  AUTH_KEYS.forEach(key => {
    sessionStorage.removeItem(key)
    localStorage.removeItem(key)
  })

  document.cookie.split(';').forEach(cookie => {
    const name = cookie.split('=')[0].trim()
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
    }
  })
}

export function setStoredAuth(user, menus) {
  purgeLegacyAuth()
  sessionStorage.setItem('user', JSON.stringify(user || {}))
  sessionStorage.setItem('menus', JSON.stringify(menus || []))
}

export function getStoredUserRaw() {
  return sessionStorage.getItem('user')
}

export function getStoredUser() {
  const userStr = getStoredUserRaw()
  if (!userStr) return null
  try {
    return JSON.parse(userStr)
  } catch (e) {
    clearStoredAuth()
    return null
  }
}

export function setStoredUser(user) {
  purgeLegacyAuth()
  sessionStorage.setItem('user', JSON.stringify(user || {}))
}

export function getStoredMenusRaw() {
  return sessionStorage.getItem('menus')
}

export function setCurrentPathName(name) {
  purgeLegacyAuth()
  sessionStorage.setItem('currentPathName', name || '')
}

export function getCurrentPathName() {
  return sessionStorage.getItem('currentPathName') || ''
}
