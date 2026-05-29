import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { useAppStore } from '@/store'

const baseRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/404',
    name: '404',
    component: () => import('../views/404.vue')
  }
]

const staticManageChildren = [
  { path: 'chat', name: 'Chat', component: () => import('../views/Chat.vue') },
  { path: 'person', name: 'Person', component: () => import('../views/Person.vue') },
  { path: 'password', name: 'Password', component: () => import('../views/Password.vue') },
  { path: 'home', name: 'Home', component: () => import('../views/Home.vue') },
  { path: 'aether-monitor', name: 'AetherMonitor', component: () => import('../views/AetherMonitor.vue') },
  { path: 'fruit-detect', name: 'FruitDetect', component: () => import('../views/FruitDetect.vue') },
  { path: 'farm-map-gaode', name: 'FarmMapGaode', component: () => import('../views/FarmMapGaode.vue') },
  { path: 'farmmap3d', name: 'FarmMap3D', component: () => import('../views/FarmMap3D.vue') },
  { path: 'alert-center', name: '预警与研判中心', component: () => import('../views/BusinessAnalysis.vue') },
  { path: 'auto-patrol', name: '无人农场·自主巡检', component: () => import('../views/AutoPatrol.vue') },
  { path: 'unmanned-dashboard', name: '无人农场总控仪表盘', component: () => import('../views/UnmannedFarmDashboard.vue') },
  { path: 'business-analysis', redirect: '/alert-center' },
  { path: 'dashbordnew', name: '监测分析看板', component: () => import('../views/DashbordNew.vue') },
  { path: 'statistic', name: '地块数据总览', component: () => import('../views/Statistic.vue') },
  { path: 'farmland', name: '地块资产图谱', component: () => import('../views/Farmland.vue') },
  { path: 'supply-center', name: '供给协同中心', component: () => import('../views/SupplyCenter.vue') },
  { path: 'market-center', name: '产销协同中心', component: () => import('../views/MarketCenter.vue') },
  { path: 'inventory', redirect: '/supply-center' },
  { path: 'purchase', redirect: '/supply-center' },
  { path: 'sales', redirect: '/market-center' },
  { path: 'online-sale', redirect: '/market-center' },
  { path: 'notice', name: '系统公告', component: () => import('../views/Notice.vue') },
  { path: 'user', name: '用户管理', component: () => import('../views/User.vue') },
  { path: 'role', name: '数据报表中心', component: () => import('../views/Role.vue') },
  { path: 'iot-dashboard', name: '总控页（备用）', component: () => import('../views/iot/IoTDashboard.vue') },
  { path: 'iot-monitor', name: '监测页（备用）', component: () => import('../views/iot/IoTMonitor.vue') },
  { path: 'iot-vision', name: '巡检页（备用）', component: () => import('../views/iot/IoTVision.vue') },
  { path: 'iot-alert-center', name: '闭环页（备用）', component: () => import('../views/iot/IoTAlertCenter.vue') }
]

const viewModules = import.meta.glob('../views/**/*.vue')

function resolveViewComponent(pagePath) {
  const cleanPath = String(pagePath || '').trim()
  if (!cleanPath || cleanPath.includes('..') || cleanPath.startsWith('/') || cleanPath.includes('\\')) {
    return null
  }
  return viewModules[`../views/${cleanPath}.vue`] || null
}

function buildManageRoute() {
  return {
    path: '/',
    name: 'Manage',
    component: () => import('../views/Manage.vue'),
    redirect: '/home',
    children: [...staticManageChildren]
  }
}

const router = createRouter({
  history: import.meta.env.VUE_APP_ROUTER_MODE === 'hash'
    ? createWebHashHistory(import.meta.env.BASE_URL)
    : createWebHistory(import.meta.env.BASE_URL),
  routes: baseRoutes
})

// 记录已动态添加的路由名称，用于 resetRouter
const dynamicRouteNames = new Set()

export const resetRouter = () => {
  dynamicRouteNames.forEach(name => {
    router.removeRoute(name)
  })
  dynamicRouteNames.clear()
}

export const setRoutes = () => {
  const manageRoute = buildManageRoute()

  const currentRouteNames = router.getRoutes().map(item => item.name)
  if (!currentRouteNames.includes('Manage')) {
    router.addRoute(manageRoute)
    dynamicRouteNames.add('Manage')
  }

  const storeMenus = localStorage.getItem('menus')
  if (!storeMenus) {
    return
  }

  let menus = []
  try {
    menus = JSON.parse(storeMenus)
  } catch (error) {
    console.error('解析菜单数据失败:', error)
    return
  }

  const existingPaths = manageRoute.children.map(item => item.path)
  const existingNames = manageRoute.children.map(item => item.name)

  menus.forEach(item => {
    if (item.path) {
      const routePath = item.path.replace('/', '')
      const pagePath = String(item.pagePath || '')
      const isDeprecatedBigScreen = routePath === 'bigscreen' || /BigScreen/i.test(pagePath)
      if (isDeprecatedBigScreen) {
        return
      }
      const component = resolveViewComponent(pagePath)
      if (!component) {
        return
      }
      if (!existingPaths.includes(routePath) && !existingNames.includes(item.name)) {
        manageRoute.children.push({
          path: routePath,
          name: item.name,
          component
        })
        existingPaths.push(routePath)
        existingNames.push(item.name)
      }
    } else if (item.children && item.children.length) {
      item.children.forEach(subItem => {
        if (!subItem.path) {
          return
        }
        const routePath = subItem.path.replace('/', '')
        const pagePath = String(subItem.pagePath || '')
        const isDeprecatedBigScreen = routePath === 'bigscreen' || /BigScreen/i.test(pagePath)
        if (isDeprecatedBigScreen) {
          return
        }
        const component = resolveViewComponent(pagePath)
        if (!component) {
          return
        }
        if (!existingPaths.includes(routePath) && !existingNames.includes(subItem.name)) {
          manageRoute.children.push({
            path: routePath,
            name: subItem.name,
            component
          })
          existingPaths.push(routePath)
          existingNames.push(subItem.name)
        }
      })
    }
  })
}

// 仅在已登录状态下初始化路由（页面刷新场景）
const storedMenus = localStorage.getItem('menus')
if (storedMenus) {
  setRoutes()
}

router.beforeEach((to, from, next) => {
  localStorage.setItem('currentPathName', to.name || '')
  const store = useAppStore()
  store.setPath()

  // 公开页面无需认证
  const publicPages = ['/login', '/register', '/404']
  const isPublic = publicPages.includes(to.path)

  // 检查 token 是否存在
  let hasToken = false
  try {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      const user = JSON.parse(userStr)
      hasToken = !!(user && user.token)
    }
  } catch (e) {
    hasToken = false
  }

  if (!isPublic && !hasToken) {
    // 未登录访问受保护页面，清除残留数据并跳转登录
    localStorage.removeItem('user')
    localStorage.removeItem('menus')
    next('/login')
    return
  }

  if (!to.matched.length) {
    const menus = localStorage.getItem('menus')
    if (!menus) {
      next('/login')
    } else {
      setRoutes()
      next({ ...to, replace: true })
    }
  } else {
    next()
  }
})

export default router
