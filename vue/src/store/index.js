import { defineStore } from 'pinia'
import router, { resetRouter } from '@/router'

export const useAppStore = defineStore('app', {
  state: () => ({
    currentPathName: ''
  }),
  actions: {
    setPath() {
      this.currentPathName = localStorage.getItem('currentPathName') || ''
    },
    async logout() {
      this.setPath()
      localStorage.removeItem('user')
      localStorage.removeItem('menus')
      await router.push('/login')
      resetRouter()
    }
  }
})
