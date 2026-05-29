import axios from 'axios'
import router from "@/router"

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
  timeout: 60000
})

let isRedirectingToLogin = false
function clearAuthAndGoLogin() {
  if (isRedirectingToLogin) return
  isRedirectingToLogin = true
  localStorage.removeItem('user')
  localStorage.removeItem('menus')
  if (router.currentRoute && router.currentRoute.value.path !== '/login') {
    router.push('/login').catch(err => { console.error('Router redirect failed:', err) })
  }
  setTimeout(() => { isRedirectingToLogin = false }, 1000)
}

request.interceptors.request.use(
  config => {
    if (!(config.data instanceof FormData)) {
      config.headers['Content-Type'] = 'application/json;charset=utf-8'
    } else {
      delete config.headers['Content-Type']
    }

    let user = null
    const userStr = localStorage.getItem('user')
    if (userStr) {
      try {
        user = JSON.parse(userStr)
      } catch (e) {
        localStorage.removeItem('user')
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

    if (res && res.code === '401') {
      clearAuthAndGoLogin()
    }

    return res
  },
  error => {
    const status = error && error.response ? error.response.status : 0

    if (!status) {
      return Promise.reject(new Error('网络连接失败，请检查后端服务是否启动'))
    }

    if (status === 401) {
      clearAuthAndGoLogin()
    }

    return Promise.reject(error)
  }
)

export default request
