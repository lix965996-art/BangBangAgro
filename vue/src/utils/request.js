import axios from 'axios'
import router from '@/router'
import { clearStoredAuth, getStoredUserRaw } from '@/utils/authStorage'

function normalizeLocalApiBaseURL(baseURL) {
  if (typeof window === 'undefined' || !baseURL) {
    return baseURL
  }
  try {
    const url = new URL(baseURL)
    const pageHost = window.location.hostname
    const isLocalApi = url.hostname === 'localhost' || url.hostname === '127.0.0.1'
    const isLocalPage = pageHost === 'localhost' || pageHost === '127.0.0.1'
    if (isLocalApi && isLocalPage) {
      url.hostname = pageHost
      return url.toString().replace(/\/$/, '')
    }
  } catch (e) {
    return baseURL
  }
  return baseURL
}

const request = axios.create({
  baseURL: normalizeLocalApiBaseURL(import.meta.env.VUE_APP_API_BASE_URL || 'http://localhost:9090'),
  timeout: 12000
})

let isRedirectingToLogin = false

function clearAuthAndGoLogin() {
  if (isRedirectingToLogin) return
  isRedirectingToLogin = true
  clearStoredAuth()
  if (router.currentRoute && router.currentRoute.value.path !== '/login') {
    router.replace('/login').catch(err => { console.error('Router redirect failed:', err) })
  }
  setTimeout(() => { isRedirectingToLogin = false }, 1000)
}

function handle401(config) {
  if (config && config.silentAuth) {
    return
  }
  console.warn('[Auth] 接口返回 401,清理登录态并跳转登录')
  clearAuthAndGoLogin()
}

request.interceptors.request.use(
  config => {
    if (!(config.data instanceof FormData)) {
      config.headers['Content-Type'] = 'application/json;charset=utf-8'
    } else {
      delete config.headers['Content-Type']
    }

    let user = null
    const userStr = getStoredUserRaw()
    if (userStr) {
      try {
        user = JSON.parse(userStr)
      } catch (e) {
        clearStoredAuth()
      }
    }

    if (user && user.token) {
      config.headers.token = user.token
    }

    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    let res = response.data

    if (response.config.responseType === 'blob') {
      return res
    }

    if (typeof res === 'string') {
      try {
        res = res ? JSON.parse(res) : res
      } catch (e) {
        // keep raw response string when JSON parse fails
      }
    }

    if (res && String(res.code) === '401') {
      handle401(response.config)
      return Promise.reject(new Error('接口未授权(401)'))
    }

    return res
  },
  error => {
    const status = error && error.response ? error.response.status : 0
    const config = (error && error.config) || {}

    if (!status) {
      if (error && error.code === 'ECONNABORTED') {
        return Promise.reject(new Error('请求超时,请稍后重试'))
      }
      return Promise.reject(new Error('网络连接失败，请检查后端服务是否启动'))
    }

    if (status === 401) {
      handle401(config)
    }

    return Promise.reject(error)
  }
)

export default request
