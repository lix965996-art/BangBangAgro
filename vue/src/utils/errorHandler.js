/**
 * 统一的API错误处理工具
 */

import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * 统一的API错误处理方法
 * @param {Error} error - 错误对象
 * @param {string} defaultMessage - 默认错误消息
 * @param {Object} options - 配置选项
 */
export const handleApiError = (error, defaultMessage = '操作失败', options = {}) => {
  const {
    showToast = true,
    showConsole = true,
    customHandler = null
  } = options

  // 如果有自定义处理器，优先使用
  if (typeof customHandler === 'function') {
    customHandler(error)
    return
  }

  let errorMessage = defaultMessage
  let errorType = 'error'

  // 解析错误
  if (error) {
    if (error.response) {
      // 服务器返回了错误状态码
      const { status, data } = error.response

      // 根据状态码处理
      switch (status) {
        case 400:
          errorMessage = data.message || '请求参数错误'
          errorType = 'warning'
          break
        case 401:
          errorMessage = '登录已过期，请重新登录'
          errorType = 'warning'
          // 触发登出逻辑
          setTimeout(() => {
            router.push('/login')
          }, 1500)
          break
        case 403:
          errorMessage = '没有权限访问此资源'
          errorType = 'error'
          break
        case 404:
          errorMessage = '请求的资源不存在'
          errorType = 'warning'
          break
        case 500:
          errorMessage = '服务器内部错误'
          errorType = 'error'
          break
        default:
          errorMessage = data.message || `请求失败 (${status})`
      }
    } else if (error.request) {
      // 请求发出去了，但没收到响应
      errorMessage = '网络连接失败，请检查网络设置'
      errorType = 'error'
    } else {
      // 其他错误（如代码错误）
      errorMessage = error.message || defaultMessage
      errorType = 'error'
    }
  }

  // 控制台输出
  if (showConsole) {
    console.error('API Error:', error)
    console.error('Error Message:', errorMessage)
  }

  // 显示提示
  if (showToast && errorMessage) {
    ElMessage({
      message: errorMessage,
      type: errorType,
      duration: errorType === 'error' ? 5000 : 3000,
      showClose: true
    })
  }

  // 返回错误信息，方便调用者处理
  return {
    type: errorType,
    message: errorMessage,
    originalError: error
  }
}

/**
 * 封装请求方法，自动处理错误
 * @param {Function} requestFn - 请求函数
 * @param {string} defaultMessage - 默认消息
 * @param {Object} options - 配置选项
 */
export const safeRequest = async (requestFn, defaultMessage = '操作失败', options = {}) => {
  try {
    const result = await requestFn()
    return result
  } catch (error) {
    const errorInfo = handleApiError(error, defaultMessage, options)
    throw errorInfo // 继续抛出，让调用者可以捕获
  }
}

/**
 * 为 Vue 组件添加错误处理混入
 */
export const errorMixin = {
  methods: {
    /**
     * 处理 API 错误
     */
    handleApiError(error, defaultMessage = '操作失败', options = {}) {
      return handleApiError(error, defaultMessage, options)
    },

    /**
     * 安全的 API 请求
     */
    safeRequest(requestFn, defaultMessage = '操作失败', options = {}) {
      return safeRequest(requestFn, defaultMessage, options)
    }
  }
}
