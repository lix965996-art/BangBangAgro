import { defineStore } from 'pinia'
import router, { resetRouter } from '@/router'
import { clearStoredAuth, getCurrentPathName } from '@/utils/authStorage'

export const useAppStore = defineStore('app', {
  state: () => ({
    currentPathName: ''
  }),
  actions: {
    setPath() {
      this.currentPathName = getCurrentPathName()
    },
    async logout() {
      this.setPath()
      clearStoredAuth()
      await router.push('/login')
      resetRouter()
    }
  }
})
