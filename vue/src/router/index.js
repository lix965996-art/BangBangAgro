import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '@/store'

Vue.use(VueRouter)

const routerMode = process.env.VUE_APP_ROUTER_MODE || 'history'

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
  { path: 'person', name: '个人信息', component: () => import('../views/Person.vue') },
  { path: 'password', name: '修改密码', component: () => import('../views/Password.vue') },
  { path: 'home', name: '首页总览', component: () => import('../views/Home.vue') },
  { path: 'aether-monitor', name: '环境监测与联动', component: () => import('../views/AetherMonitor.vue') },
  { path: 'fruit-detect', name: '视觉巡检与异常识别', component: () => import('../views/FruitDetect.vue') },
  { path: 'farm-map-gaode', name: 'GIS 地块指挥', component: () => import('../views/FarmMapGaode.vue') },
  { path: 'farmmap3d', name: '3D 数字孪生', component: () => import('../views/FarmMap3D.vue') },
  { path: 'alert-center', name: '预警与研判中心', component: () => import('../views/BusinessAnalysis.vue') },
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
  { path: 'role', name: '角色管理', component: () => import('../views/Role.vue') },
  { path: 'iot-dashboard', name: '总控页（备用）', component: () => import('../views/iot/IoTDashboard.vue') },
  { path: 'iot-monitor', name: '监测页（备用）', component: () => import('../views/iot/IoTMonitor.vue') },
  { path: 'iot-vision', name: '巡检页（备用）', component: () => import('../views/iot/IoTVision.vue') },
  { path: 'iot-alert-center', name: '闭环页（备用）', component: () => import('../views/iot/IoTAlertCenter.vue') }
]

function buildManageRoute() {
  return {
    path: '/',
    name: 'Manage',
    component: () => import('../views/Manage.vue'),
    redirect: '/home',
    children: [...staticManageChildren]
  }
}

const router = new VueRouter({
  mode: routerMode,
  routes: baseRoutes
})

export const resetRouter = () => {
  router.matcher = new VueRouter({
    mode: routerMode,
    routes: baseRoutes
  }).matcher
}

export const setRoutes = () => {
  // 确保 Manage 路由始终存在
  const manageRoute = buildManageRoute()

  const currentRouteNames = router.getRoutes().map(item => item.name)
  if (!currentRouteNames.includes('Manage')) {
    router.addRoute(manageRoute)
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

  // 添加动态菜单项
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
      if (!existingPaths.includes(routePath) && !existingNames.includes(item.name)) {
        manageRoute.children.push({
          path: routePath,
          name: item.name,
          component: () => import('../views/' + pagePath + '.vue')
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
        if (!existingPaths.includes(routePath) && !existingNames.includes(subItem.name)) {
          manageRoute.children.push({
            path: routePath,
            name: subItem.name,
            component: () => import('../views/' + pagePath + '.vue')
          })
          existingPaths.push(routePath)
          existingNames.push(subItem.name)
        }
      })
    }
  })
}

setRoutes()

router.beforeEach((to, from, next) => {
  localStorage.setItem('currentPathName', to.name || '')
  store.commit('setPath')
  if (!to.matched.length) {
    const menus = localStorage.getItem('menus')
    if (!menus) {
      next('/login')
    } else {
      next('/404')
    }
  } else {
    next()
  }
})

export default router
