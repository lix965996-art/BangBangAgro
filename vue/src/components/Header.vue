<template>
  <div class="green-header">
    <div class="header-left">
      <el-icon class="collapse-btn" @click="collapse">
        <component :is="collapseIcon" />
      </el-icon>

      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item :to="'/'">
          <el-icon class="breadcrumb-icon"><HomeFilled /></el-icon>
          首页
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentPathName }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    
  
    <router-link to="/chat" class="msg-bell-btn" title="消息中心">
      <el-badge :value="headerUnread > 0 ? (headerUnread > 99 ? '99+' : headerUnread) : ''" :hidden="headerUnread === 0">
        <el-icon class="msg-bell-icon"><ChatDotSquare /></el-icon>
      </el-badge>
    </router-link>

    <el-dropdown class="user-dropdown">
      <div class="user-info">
        <img :src="user && user.avatarUrl ? user.avatarUrl : $assetUrl('mo.png')" alt="" class="user-avatar">
        <span class="user-name">{{ user ? user.nickname : '用户' }}</span>
        <el-icon class="user-arrow"><ArrowDown /></el-icon>
      </div>
      <template #dropdown>
        <el-dropdown-menu class="user-menu">
          <el-dropdown-item>
            <router-link to="/person" class="menu-link">
              <el-icon><User /></el-icon>
              个人信息
            </router-link>
          </el-dropdown-item>
          <el-dropdown-item>
            <router-link to="/password" class="menu-link">
              <el-icon><Key /></el-icon>
              修改密码
            </router-link>
          </el-dropdown-item>
          <el-dropdown-item divided>
            <div class="menu-link logout-btn" @click="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </div>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script>
import { useAppStore } from '@/store'
import {
  ArrowDown,
  ChatDotSquare,
  Expand,
  Fold,
  HomeFilled,
  Key,
  SwitchButton,
  User
} from '@element-plus/icons-vue'

export default {
  name: "Header",
  components: {
    ArrowDown,
    ChatDotSquare,
    HomeFilled,
    Key,
    SwitchButton,
    User
  },
  props: {
    collapseBtnClass: String,
    user: Object
  },
  computed: {
    collapseIcon () {
      return this.collapseBtnClass === 'el-icon-s-unfold' ? Expand : Fold
    },
    currentPathName () {
      return this.appStore.currentPathName;
    }
  },
  data() {
    return {
      headerUnread: 0,
      _unreadTimer: null
    }
  },
  created() {
    this.appStore = useAppStore()
  },
  mounted() {
    this.fetchUnread()
    this._unreadTimer = setInterval(this.fetchUnread, 30000)
  },
  beforeUnmount() {
    if (this._unreadTimer) clearInterval(this._unreadTimer)
  },
  methods: {
    collapse() {
      this.$emit("asideCollapse")
    },
    logout() {
      this.appStore.logout()
      this.$message.success("退出成功")
    },
    async fetchUnread() {
      if (!localStorage.getItem('user')) return
      try {
        const res = await this.request.get('/chat-message/unread')
        if (res && res.code === '200') {
          this.headerUnread = res.data || 0
          // 通知 Aside 组件更新未读数
          window.dispatchEvent(new CustomEvent('chat-unread-updated', { detail: this.headerUnread }))
        }
      } catch (e) {
        // 静默失败
      }
    }
  }
}
</script>

<style scoped>
/* 顶部导航 - 悬浮白色 (最上层) */
.green-header {
  height: 60px;
  display: flex;
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #e8ebe8;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  flex: 1;
}

.collapse-btn {
  cursor: pointer;
  font-size: 20px;
  color: #4caf50;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.collapse-btn:hover {
  background: rgba(76, 175, 80, 0.1);
}

.breadcrumb-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

.breadcrumb {
  margin-left: 15px;
}

:deep(.breadcrumb .el-breadcrumb__item) {
  font-weight: 500;
}

:deep(.breadcrumb .el-breadcrumb__inner) {
  color: #2c5530;
}

:deep(.breadcrumb .el-breadcrumb__inner:hover) {
  color: #4caf50;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.system-title {
  font-size: 20px;
  font-weight: 600;
  color: #2c5530;
  display: flex;
  align-items: center;
  gap: 8px;
}

.system-title i {
  font-size: 24px;
  color: #4caf50;
}

/* 消息铃铛 */
.msg-bell-btn {
  display: flex;
  align-items: center;
  margin-right: 20px;
  text-decoration: none;
  color: #4caf50;
  padding: 8px;
  border-radius: 50%;
  transition: background 0.2s;
}

.msg-bell-btn:hover {
  background: rgba(76, 175, 80, 0.1);
}

.msg-bell-icon {
  font-size: 22px;
}

:deep(.msg-bell-btn .el-badge__content) {
  font-size: 10px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 8px;
}

.user-dropdown {
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 20px;
  background: transparent;
  border: none;
  transition: all 0.3s ease;
}

.user-info:hover {
  background: rgba(76, 175, 80, 0.1);
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 2px solid #6dac50ff;
  object-fit: cover;
  background: #ffffff;
  padding: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.user-name {
  font-weight: 600;
  color: #2c5530;
  font-size: 14px;
}

.user-arrow {
  color: #4caf50;
  font-size: 12px;
}

/* 下拉菜单 */
.user-menu {
  border: 2px solid #4caf50;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(76, 175, 80, 0.2);
}

.menu-link {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #2c5530;
  text-decoration: none;
  font-weight: 500;
  transition: all 0.3s ease;
}

.menu-link:hover {
  color: #4caf50;
}

.menu-link i {
  font-size: 16px;
}

.logout-btn {
  cursor: pointer;
}

.logout-btn:hover {
  color: #f56c6c !important;
}
</style>
