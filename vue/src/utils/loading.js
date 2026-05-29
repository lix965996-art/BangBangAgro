import { createApp, h } from 'vue'
import GlobalLoading from '@/components/GlobalLoading.vue'

/**
 * 全局加载状态管理
 */

let loadingInstance = null
let loadingApp = null
let loadingCount = 0
let removalTimer = null

/**
 * 显示加载动画
 * @param {string} text - 加载提示文字
 * @param {Object} options - 配置选项
 */
export const showLoading = (text = '加载中...', options = {}) => {
  loadingCount++

  // 取消待处理的移除操作
  if (removalTimer) {
    clearTimeout(removalTimer)
    removalTimer = null
  }

  // 避免重复创建
  if (!loadingInstance) {
    const container = document.createElement('div')
    document.body.appendChild(container)

    const propsData = {
      visible: true,
      text,
      ...options
    }

    loadingApp = createApp({
      data() {
        return { ...propsData }
      },
      render() {
        return h(GlobalLoading, {
          visible: this.visible,
          text: this.text,
          showProgress: this.showProgress,
          progress: this.progress,
          progressStatus: this.progressStatus
        })
      }
    })

    loadingInstance = loadingApp.mount(container)
    loadingInstance._container = container
  } else {
    loadingInstance.visible = true
    if (text) {
      loadingInstance.text = text
    }
    if (options.showProgress !== undefined) {
      loadingInstance.showProgress = options.showProgress
    }
    if (options.progress !== undefined) {
      loadingInstance.progress = options.progress
    }
    if (options.progressStatus !== undefined) {
      loadingInstance.progressStatus = options.progressStatus
    }
  }

  return loadingInstance
}

/**
 * 隐藏加载动画
 */
export const hideLoading = () => {
  loadingCount--
  if (loadingCount <= 0 && loadingInstance) {
    loadingInstance.visible = false
    removalTimer = setTimeout(() => {
      if (loadingInstance && !loadingInstance.visible) {
        try {
          if (loadingInstance._container) {
            loadingApp.unmount()
            document.body.removeChild(loadingInstance._container)
          }
        } catch (e) {
          // DOM already removed
        }
        loadingInstance = null
        loadingApp = null
      }
      removalTimer = null
    }, 300) // 等待动画完成
  }
}

/**
 * 显示带进度的加载动画
 * @param {string} text - 提示文字
 * @param {number} progress - 进度 0-100
 * @param {string} status - 状态
 */
export const showProgress = (text = '处理中...', progress = 0, status = '') => {
  showLoading(text, {
    showProgress: true,
    progress,
    progressStatus: status
  })

  return {
    update: (newProgress, newStatus) => {
      if (loadingInstance) {
        loadingInstance.progress = newProgress
        if (newStatus) {
          loadingInstance.progressStatus = newStatus
        }
      }
    },
    hide: hideLoading
  }
}

/**
 * 为异步函数添加加载状态
 * @param {Function} asyncFn - 异步函数
 * @param {string} loadingText - 加载提示文字
 * @param {Object} options - 配置选项
 */
export const withLoading = (asyncFn, loadingText = '加载中...', options = {}) => {
  return async (...args) => {
    showLoading(loadingText, options)
    try {
      const result = await asyncFn(...args)
      return result
    } catch (error) {
      throw error
    } finally {
      hideLoading()
    }
  }
}

// Vue 3 指令：v-loading
export const loadingDirective = {
  mounted(el, binding) {
    const { value, modifiers } = binding

    if (value) {
      el._loadingInstance = showLoading(modifiers.text ? '加载中...' : undefined)
    }
  },
  updated(el, binding) {
    const { value, modifiers } = binding

    if (el._loadingInstance) {
      el._loadingInstance.visible = value

      // 如果文本变化，更新文本
      if (modifiers.text && value) {
        el._loadingInstance.text = '加载中...'
      }
    }
  },
  unmounted(el) {
    if (el._loadingInstance) {
      el._loadingInstance = null
    }
  }
}
