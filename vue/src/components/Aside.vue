<template>
  <div class="aside-container">
    <div class="logo-box">
      <img src="../assets/Logo.jpg" alt="帮帮农" class="logo-img" />
      <transition name="fade">
        <div v-show="!isCollapse" class="logo-text-group">
          <span class="logo-title">帮帮农</span>
          <span class="logo-sub">设施农业数字化协同系统</span>
        </div>
      </transition>
    </div>

    <el-menu
      :default-active="$route.path"
      :default-openeds="defaultOpeneds"
      :collapse="isCollapse"
      :collapse-transition="false"
      background-color="#f8faf9"
      text-color="#475569"
      active-text-color="#059669"
      router
      class="custom-menu"
    >
      <el-menu-item
        v-for="item in mainMenus"
        :key="item.path"
        :index="item.path"
      >
        <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
        <template #title>
          <span class="menu-item-title">
            {{ item.title }}
            <el-badge
              v-if="item.path === '/chat' && chatUnread > 0"
              :value="chatUnread > 99 ? '99+' : chatUnread"
              class="chat-badge"
            />
          </span>
        </template>
      </el-menu-item>

      <div v-show="!isCollapse" class="section-divider"></div>

      <el-submenu index="more-features" class="more-submenu">
        <template #title>
          <el-icon class="menu-icon"><More /></el-icon>
          <span>更多功能</span>
        </template>

        <template v-for="group in supportGroups" :key="`${group.index}-label`">
          <div class="submenu-group-label">
            {{ group.title }}
          </div>
          <el-menu-item
            v-for="item in group.children"
            :key="item.path"
            :index="item.path"
            class="secondary-item"
          >
            <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
            <template #title>
              <span>{{ item.title }}</span>
            </template>
          </el-menu-item>
        </template>
      </el-submenu>
    </el-menu>
  </div>
</template>

<script>
import {
  Bell,
  Box,
  ChatDotSquare,
  Connection,
  Cpu,
  DataAnalysis,
  DataLine,
  HomeFilled,
  LocationInformation,
  MapLocation,
  More,
  OfficeBuilding,
  PictureRounded,
  PieChart,
  Sell,
  User
} from '@element-plus/icons-vue'
import { getStoredUserRaw } from '@/utils/authStorage'

// 主菜单顺序按"评委读得懂的故事主线"排:
// 工作台(起点) → AI 农艺师(核心卖点) → 预警研判(闭环) → 3D 数字孪生(差异化)
//             → 环境监测 → 视觉巡检 → 消息
const mainMenus = [
  { path: '/home', icon: HomeFilled, title: '工作台' },
  { path: '/auto-patrol', icon: Connection, title: '无人农场指挥中心' },
  { path: '/alert-center', icon: DataAnalysis, title: '预警研判中心' },
  { path: '/farmmap3d', icon: OfficeBuilding, title: '3D 数字孪生' },
  { path: '/aether-monitor', icon: Cpu, title: '环境监测' },
  { path: '/fruit-detect', icon: PictureRounded, title: '视觉巡检' },
  { path: '/chat', icon: ChatDotSquare, title: '消息中心' }
]

// 二级"更多功能"按业务域分组,把使用频次较低或属性接近的兜底页放这里
const supportGroups = [
  {
    index: 'farm-data',
    title: '农场数据',
    children: [
      { path: '/dashbordnew', icon: DataLine, title: '监测分析大屏' },
      { path: '/statistic', icon: PieChart, title: '地块数据总览' },
      { path: '/farmland', icon: LocationInformation, title: '地块资产图谱' },
      { path: '/farm-map-gaode', icon: MapLocation, title: 'GIS 农场地图' }
    ]
  },
  {
    index: 'business',
    title: '经营协同',
    children: [
      { path: '/supply-center', icon: Box, title: '供给协同中心' },
      { path: '/market-center', icon: Sell, title: '产销协同中心' }
    ]
  },
  {
    index: 'system',
    title: '系统后台',
    children: [
      { path: '/user', icon: User, title: '用户管理' },
      { path: '/role', icon: DataAnalysis, title: '数据报表中心' },
      { path: '/notice', icon: Bell, title: '系统公告' }
    ]
  }
]

export default {
  name: 'Aside',
  components: {
    More
  },
  props: {
    isCollapse: Boolean,
    logoTextShow: Boolean
  },
  data() {
    return {
      mainMenus,
      supportGroups,
      chatUnread: 0
    }
  },
  computed: {
    defaultOpeneds() {
      const currentPath = this.$route.path
      const inSupportMenus = this.supportGroups.some(group =>
        group.children.some(item => item.path === currentPath)
      )
      return inSupportMenus ? ['more-features'] : []
    }
  },
  mounted() {
    this.fetchUnread()
    // 监听 Header 的未读数更新事件，避免重复轮询
    window.addEventListener('chat-unread-updated', this.onUnreadUpdated)
  },
  beforeUnmount() {
    window.removeEventListener('chat-unread-updated', this.onUnreadUpdated)
  },
  methods: {
    async fetchUnread() {
      // 未登录时不请求
      if (!getStoredUserRaw()) return
      try {
        const res = await this.request.get('/chat-message/unread')
        if (res && res.code === '200') {
          this.chatUnread = res.data || 0
        }
      } catch (e) {
        // 静默失败，不影响其他功能
      }
    },
    onUnreadUpdated(e) {
      this.chatUnread = e.detail || 0
    }
  }
}
</script>

<style scoped>
/* 鍘熸湁鐨勬牱寮忓畬鍏ㄤ繚鐣欙紝淇濇寔浣犵殑 UI 缇庤鎬?*/
.aside-container {
  height: 100%;
  background: #f8faf9;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e2e8f0 !important;
}

.logo-box {
  min-height: 84px;
  display: flex;
  align-items: center;
  padding: 20px 16px;
  background: #f8faf9;
  border-bottom: 1px solid #e8ebe8;
  flex-shrink: 0;
}

.logo-img {
  width: 52px;
  height: 52px;
  border-radius: 12px;
}

.logo-text-group {
  margin-left: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.logo-title {
  color: #1e293b;
  font-weight: 700;
  font-size: 22px;
  letter-spacing: 0.5px;
}

.logo-sub {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.2px;
  margin-top: 3px;
}

.custom-menu {
  border: none !important;
  padding: 14px 12px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.custom-menu::-webkit-scrollbar {
  display: none;
  width: 0 !important;
}

.section-divider {
  height: 1px;
  margin: 14px 18px 10px;
  background: linear-gradient(90deg, rgba(203, 213, 225, 0), rgba(203, 213, 225, 0.9), rgba(203, 213, 225, 0));
}

.submenu-group-label {
  padding: 12px 20px 6px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

:deep(.el-menu-item),
:deep(.el-submenu__title ){
  height: 48px;
  line-height: 48px;
  margin: 4px 8px;
  background-color: transparent !important;
  color: #475569 !important;
  font-size: 15px;
  font-weight: 500;
  border-radius: 12px;
  padding: 0 14px !important;
  position: relative;
  transition: all 0.2s ease;
}

:deep(.el-menu-item:hover),
:deep(.el-submenu__title:hover ){
  color: #0f766e !important;
  background: rgba(15, 118, 110, 0.06) !important;
}

:deep(.el-menu-item.is-active ){
  color: #059669 !important;
  background: rgba(5, 150, 105, 0.08) !important;
  font-weight: 600;
}

:deep(.el-menu-item.is-active::before ){
  content: '';
  position: absolute;
  left: -8px;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, #059669, #34d399);
}

.menu-icon {
  width: 18px;
  height: 18px;
  margin-right: 10px;
  color: inherit !important;
  flex-shrink: 0;
}

:deep(.more-submenu > .el-submenu__title ){
  margin-top: 0;
  color: #475569 !important;
  font-size: 15px;
  font-weight: 500;
}

:deep(.secondary-item ){
  color: #475569 !important;
  font-size: 15px;
  font-weight: 500;
}

.menu-item-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* 未读角标：小圆点位置微调 */
:deep(.chat-badge .el-badge__content ){
  font-size: 10px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #f56c6c;
  border: none;
  position: static;
  transform: none;
  top: auto;
  right: auto;
}
</style>
