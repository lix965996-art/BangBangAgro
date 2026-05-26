import mapConfig from '@/config/map.config.js'

const PRIMARY_SCRIPT_ID = 'amap-sdk-script'
const LEGACY_SCRIPT_IDS = ['amap-script', 'farm-location-amap']

let loaderPromise = null

const allScriptIds = () => [PRIMARY_SCRIPT_ID, ...LEGACY_SCRIPT_IDS]

const removeKnownScripts = () => {
  allScriptIds().forEach((id) => {
    const script = document.getElementById(id)
    if (script) {
      script.remove()
    }
  })
}

const buildScriptUrl = (key, version, plugins) => {
  let url = `https://webapi.amap.com/maps?v=${encodeURIComponent(version)}&key=${encodeURIComponent(key)}`
  if (plugins && plugins.length > 0) {
    url += `&plugin=${encodeURIComponent(plugins.join(','))}`
  }
  return url
}

const ensureSecurityConfig = (securityCode) => {
  if (!securityCode) {
    return
  }

  window._AMapSecurityConfig = {
    ...(window._AMapSecurityConfig || {}),
    securityJsCode: securityCode
  }
}

const ensurePlugins = (plugins) => {
  if (!Array.isArray(plugins) || plugins.length === 0 || !window.AMap || typeof window.AMap.plugin !== 'function') {
    return Promise.resolve(window.AMap)
  }

  return new Promise((resolve, reject) => {
    try {
      window.AMap.plugin(plugins, () => resolve(window.AMap))
    } catch (error) {
      reject(error)
    }
  })
}

export const resetAmapLoader = () => {
  loaderPromise = null
  delete window.AMap
  delete window._AMapSecurityConfig
  window.__AMAP_LOADED_KEY__ = undefined
  window.__AMAP_LOADED_SECURITY__ = undefined
  removeKnownScripts()
}

export const loadAmapSdk = async (options = {}) => {
  // 优先使用环境变量作为单一配置源，避免 key/scode 跨文件不一致
  const key = options.key || process.env.VUE_APP_AMAP_JS_KEY || mapConfig.amap.jsKey || ''
  const securityCode = options.forceNoSecurity
    ? ''
    : (options.securityCode || process.env.VUE_APP_AMAP_SECURITY_CODE || mapConfig.amap.securityCode || '')
  const version = options.version || mapConfig.amap.version || '2.0'
  const plugins = Array.from(new Set([...(options.plugins || [])].filter(Boolean)))

  if (!key) {
    throw new Error('AMAP_KEY_MISSING')
  }

  // 如果 key 或 scode 发生变化，强制清除重新加载
  if (
    window.AMap &&
    window.AMap.Map &&
    (window.__AMAP_LOADED_KEY__ !== key || window.__AMAP_LOADED_SECURITY__ !== securityCode)
  ) {
    console.log('[amapLoader] 检测到 Key/SCode 变更，清除旧 SDK 重新加载')
    delete window.AMap
    delete window._AMapSecurityConfig
    window.__AMAP_LOADED_KEY__ = undefined
    window.__AMAP_LOADED_SECURITY__ = undefined
    removeKnownScripts()
    loaderPromise = null
  }

  ensureSecurityConfig(securityCode)

  if (window.AMap && window.AMap.Map) {
    return ensurePlugins(plugins)
  }

  if (!loaderPromise) {
    loaderPromise = new Promise((resolve, reject) => {
      const existingScript = allScriptIds()
        .map((id) => document.getElementById(id))
        .find(Boolean)

      const handleLoad = () => {
        if (window.AMap && window.AMap.Map) {
          window.__AMAP_LOADED_KEY__ = key
          window.__AMAP_LOADED_SECURITY__ = securityCode
          resolve(window.AMap)
          return
        }

        loaderPromise = null
        reject(new Error('AMAP_SDK_UNAVAILABLE'))
      }

      const handleError = () => {
        loaderPromise = null
        removeKnownScripts()
        reject(new Error('AMAP_NETWORK_ERROR'))
      }

      if (existingScript) {
        existingScript.addEventListener('load', handleLoad, { once: true })
        existingScript.addEventListener('error', handleError, { once: true })
        return
      }

      const script = document.createElement('script')
      script.id = PRIMARY_SCRIPT_ID
      script.async = true
      script.src = buildScriptUrl(key, version, plugins)
      script.addEventListener('load', handleLoad, { once: true })
      script.addEventListener('error', handleError, { once: true })
      document.head.appendChild(script)
    })
  }

  const amap = await loaderPromise
  await ensurePlugins(plugins)
  return amap
}
