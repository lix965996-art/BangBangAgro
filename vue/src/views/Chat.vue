<template>
  <div class="chat-page">
    <!-- 左侧面板 -->
    <div class="left-panel">
      <div class="panel-header">
        <span class="panel-title">消息中心</span>
        <el-dropdown trigger="click" @command="handlePanelCommand">
          <el-button type="primary" size="mini" icon="el-icon-plus">新建 <i class="el-icon-arrow-down el-icon--right"></i></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="addFriend" icon="el-icon-user">添加好友</el-dropdown-item>
              <el-dropdown-item command="joinGroup" icon="el-icon-connection">加入群聊</el-dropdown-item>
              <el-dropdown-item command="createGroup" icon="el-icon-circle-plus-outline">创建群聊</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- 搜索 -->
      <div class="search-box">
        <el-input v-model="searchText" placeholder="搜索联系人..." prefix-icon="el-icon-search" size="small" clearable></el-input>
      </div>

      <!-- 好友申请通知 -->
      <div v-if="friendRequests.length > 0" class="request-section">
        <div class="group-label">
          好友申请
          <span class="request-badge">{{ friendRequests.length }}</span>
        </div>
        <div v-for="req in friendRequests" :key="req.requestId" class="request-item">
          <el-avatar :size="36" :src="req.fromAvatarUrl" icon="el-icon-user-solid"></el-avatar>
          <div class="request-info">
            <div class="request-name">{{ req.fromNickname || req.fromUsername }}</div>
            <div class="request-uid">UID: {{ req.fromUid }}</div>
          </div>
          <div class="request-actions">
            <el-button type="success" size="mini" icon="el-icon-check" circle @click="acceptRequest(req)"></el-button>
            <el-button type="danger" size="mini" icon="el-icon-close" circle @click="rejectRequest(req)"></el-button>
          </div>
        </div>
      </div>

      <!-- 联系人列表 -->
      <div class="contact-list" ref="contactList">
        <!-- 群聊分组 -->
        <div v-if="filteredGroups.length > 0" class="contact-group">
          <div class="group-label">群聊</div>
          <div
            v-for="group in filteredGroups"
            :key="'g-'+group.groupId"
            :class="['contact-item', { active: activeChat && activeChat.type === 'group' && activeChat.id === group.groupId }]"
            @click="openGroupChat(group)"
          >
            <div class="avatar-group">
              <el-avatar :size="36" icon="el-icon-user-solid" :style="{ backgroundColor: '#42b883' }"></el-avatar>
            </div>
            <div class="contact-info">
              <div class="contact-name">
                {{ group.name }}
                <span class="member-count">({{ group.memberCount }})</span>
              </div>
            </div>
            <span v-if="group.unread > 0" class="unread-badge">{{ group.unread > 99 ? '99+' : group.unread }}</span>
          </div>
        </div>

        <!-- 私聊联系人 -->
        <div class="contact-group">
          <div class="group-label">联系人</div>
          <div
            v-for="contact in filteredContacts"
            :key="'c-'+contact.userId"
            :class="['contact-item', { active: activeChat && activeChat.type === 'private' && activeChat.id === contact.userId }]"
            @click="openPrivateChat(contact)"
          >
            <div class="avatar-group">
              <el-avatar :size="36" :src="contact.avatarUrl" v-if="contact.avatarUrl"></el-avatar>
              <el-avatar :size="36" icon="el-icon-user-solid" v-else></el-avatar>
            </div>
            <div class="contact-info">
              <div class="contact-name">
                {{ contact.nickname || contact.username }}
                <el-tag size="mini" type="warning" v-if="contact.role === 'ROLE_ADMIN'">管理员</el-tag>
              </div>
              <div class="contact-last-msg">{{ contact.lastMessage || '暂无消息' }}</div>
            </div>
            <span v-if="contact.unread > 0" class="unread-badge">{{ contact.unread > 99 ? '99+' : contact.unread }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="right-panel">
      <!-- 空状态 -->
      <div v-if="!activeChat" class="empty-chat">
        <i class="el-icon-chat-dot-square empty-icon"></i>
        <p>选择一个联系人开始聊天</p>
      </div>

      <!-- 聊天窗口 -->
      <template v-else>
        <div class="chat-header">
          <div class="chat-header-info">
            <el-avatar :size="36" :src="activeChat.avatar" v-if="activeChat.avatar"></el-avatar>
            <el-avatar :size="36" icon="el-icon-user-solid" v-else></el-avatar>
            <div class="chat-header-name">
              <span>{{ activeChat.name }}</span>
              <span v-if="activeChat.type === 'group'" class="group-tag">群聊</span>
            </div>
          </div>
          <div v-if="activeChat.type === 'group'" class="chat-header-actions">
            <el-button size="mini" icon="el-icon-plus" @click="showInviteDialog">邀请成员</el-button>
          </div>
        </div>

        <div class="chat-messages" ref="chatMessages" @scroll="onScroll">
          <div v-if="loadingHistory" class="loading-more">加载中...</div>
          <div v-else-if="!hasMore" class="loading-more">没有更多消息了</div>

          <div
            v-for="msg in currentMessages"
            :key="msg.id || msg._tempId"
            :class="['message-item', msg.fromUserId === myUserId ? 'message-self' : 'message-other']"
          >
            <el-avatar :size="30" :src="msg.fromAvatar" v-if="msg.fromAvatar && msg.fromUserId !== myUserId"></el-avatar>
            <el-avatar :size="30" icon="el-icon-user-solid" v-else-if="msg.fromUserId !== myUserId"></el-avatar>
            <div class="message-bubble-wrapper">
              <div v-if="msg.fromUserId !== myUserId" class="message-sender">{{ msg.fromUsername }}</div>
              <div class="message-bubble">{{ msg.content || msg.text }}</div>
              <div class="message-time">{{ formatTime(msg.createTime || msg.time) }}</div>
            </div>
            <el-avatar :size="30" :src="msg.fromAvatar" v-if="msg.fromAvatar && msg.fromUserId === myUserId"></el-avatar>
            <el-avatar :size="30" icon="el-icon-user-solid" v-else-if="msg.fromUserId === myUserId"></el-avatar>
          </div>
        </div>

        <div class="chat-input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入消息… Ctrl+Enter 发送，Enter 换行"
            @keydown="onInputKeydown"
          ></el-input>
          <div class="send-actions">
            <span class="send-hint">Ctrl+Enter 发送</span>
            <el-button type="primary" size="small" @click="sendTextMessage" :disabled="!inputText.trim()">发 送</el-button>
          </div>
        </div>
      </template>
    </div>

    <!-- 创建群聊对话框 -->
    <el-dialog title="创建群聊" v-model="createGroupDialogVisible" width="450px">
      <el-form label-width="80px">
        <el-form-item label="群组名称">
          <el-input v-model="newGroupName" placeholder="请输入群组名称"></el-input>
        </el-form-item>
        <el-form-item label="选择成员">
          <el-checkbox-group v-model="newGroupMembers">
            <div v-for="u in allUsers" :key="u.id" style="margin-bottom: 8px;">
              <el-checkbox :label="u.id" :disabled="u.id === myUserId">
                {{ u.nickname || u.username }}
                <el-tag size="mini" v-if="u.role === 'ROLE_ADMIN'" type="warning">管理员</el-tag>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span>
          <el-button @click="createGroupDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="doCreateGroup" :disabled="!newGroupName.trim() || newGroupMembers.length === 0">创建</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 添加好友对话框 -->
    <el-dialog title="添加好友" v-model="addFriendDialogVisible" width="420px" @close="resetAddFriend">
      <div class="add-dialog-body">
        <el-input
          v-model="searchUid"
          placeholder="请输入对方的帮帮农ID（6位数字）"
          prefix-icon="el-icon-search"
          clearable
          @keyup.enter="doSearchUser"
        >
          <template #append>
            <el-button @click="doSearchUser" :loading="searchingUser">搜索</el-button>
          </template>
        </el-input>

        <div v-if="foundUser" class="found-card">
          <el-avatar :size="50" :src="foundUser.user.avatarUrl" icon="el-icon-user-solid"></el-avatar>
          <div class="found-info">
            <div class="found-name">{{ foundUser.user.nickname || foundUser.user.username }}</div>
            <div class="found-uid">帮帮农ID：{{ foundUser.user.uid }}</div>
            <el-tag size="mini" type="warning" v-if="foundUser.user.role === 'ROLE_ADMIN'">管理员</el-tag>
          </div>
          <el-button
            v-if="!foundUser.isFriend && !requestSent"
            type="primary" size="small"
            @click="doSendRequest"
            :loading="addingFriend"
          >发送申请</el-button>
          <el-tag v-else-if="requestSent" type="warning" size="small">申请已发送</el-tag>
          <el-tag v-else type="success" size="small">已是好友</el-tag>
        </div>
        <div v-if="searchUserMsg" class="search-tip">{{ searchUserMsg }}</div>
      </div>
    </el-dialog>

    <!-- 加入群聊对话框 -->
    <el-dialog title="加入群聊" v-model="joinGroupDialogVisible" width="420px" @close="resetJoinGroup">
      <div class="add-dialog-body">
        <el-input
          v-model="searchGroupNumber"
          placeholder="请输入群号（10位数字）"
          prefix-icon="el-icon-search"
          clearable
          @keyup.enter="doSearchGroup"
        >
          <template #append>
            <el-button @click="doSearchGroup" :loading="searchingGroup">搜索</el-button>
          </template>
        </el-input>

        <div v-if="foundGroup" class="found-card">
          <el-avatar :size="50" icon="el-icon-user-solid" :style="{ backgroundColor: '#42b883' }"></el-avatar>
          <div class="found-info">
            <div class="found-name">{{ foundGroup.group.name }}</div>
            <div class="found-uid">群号：{{ foundGroup.group.groupNumber }} · {{ foundGroup.memberCount }} 人</div>
          </div>
          <el-button
            v-if="!foundGroup.joined"
            type="primary" size="small"
            @click="doJoinGroup"
            :loading="joiningGroup"
          >加入群聊</el-button>
          <el-tag v-else type="success" size="small">已在群里</el-tag>
        </div>
        <div v-if="searchGroupMsg" class="search-tip">{{ searchGroupMsg }}</div>
      </div>
    </el-dialog>

    <!-- 邀请成员对话框 -->
    <el-dialog title="邀请成员" v-model="inviteDialogVisible" width="400px" v-if="activeChat && activeChat.type === 'group'">
      <el-checkbox-group v-model="inviteMemberIds">
        <div v-for="u in nonMembers" :key="u.userId" style="margin-bottom: 8px;">
          <el-checkbox :label="u.userId">{{ u.nickname || u.username }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <div v-if="nonMembers.length === 0" style="color: #999; text-align: center;">没有可邀请的用户</div>
      <template #footer>
        <span>
          <el-button @click="inviteDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="doInvite" :disabled="inviteMemberIds.length === 0">邀请</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { getStoredUserRaw } from '@/utils/authStorage'

export default {
  name: 'Chat',
  data() {
    return {
      // 当前用户
      myUserId: null,
      myUsername: '',

      // 联系人和群组
      contacts: [],
      groups: [],
      allUsers: [],

      // 搜索
      searchText: '',

      // 当前聊天
      activeChat: null,
      currentMessages: [],
      pageNum: 1,
      pageSize: 30,
      hasMore: true,
      loadingHistory: false,

      // 输入
      inputText: '',

      // WebSocket
      socket: null,
      reconnectTimer: null,
      reconnectAttempts: 0,
      MAX_RECONNECT_ATTEMPTS: 10,
      unreadMap: {},

      // 创建群聊
      createGroupDialogVisible: false,
      newGroupName: '',
      newGroupMembers: [],

      // 邀请成员
      inviteDialogVisible: false,
      inviteMemberIds: [],
      nonMembers: [],

      // 好友申请
      friendRequests: [],
      _requestTimer: null,

      // 添加好友
      addFriendDialogVisible: false,
      searchUid: '',
      foundUser: null,
      searchUserMsg: '',
      searchingUser: false,
      addingFriend: false,
      requestSent: false,

      // 加入群聊
      joinGroupDialogVisible: false,
      searchGroupNumber: '',
      foundGroup: null,
      searchGroupMsg: '',
      searchingGroup: false,
      joiningGroup: false,
    }
  },
  computed: {
    filteredContacts() {
      if (!this.searchText) return this.contacts
      const keyword = this.searchText.toLowerCase()
      return this.contacts.filter(c =>
        (c.username || '').toLowerCase().includes(keyword) ||
        (c.nickname || '').toLowerCase().includes(keyword)
      )
    },
    filteredGroups() {
      if (!this.searchText) return this.groups
      const keyword = this.searchText.toLowerCase()
      return this.groups.filter(g =>
        (g.name || '').toLowerCase().includes(keyword)
      )
    }
  },
  mounted() {
    this.loadCurrentUser()
    this.loadContacts()
    this.loadGroups()
    this.connectWebSocket()
    this.loadFriendRequests()
    this._requestTimer = setInterval(() => this.loadFriendRequests(), 15000)
  },
  beforeUnmount() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    if (this._requestTimer) clearInterval(this._requestTimer)
    if (this.socket) this.socket.close()
  },
  methods: {
    loadCurrentUser() {
      const userStr = getStoredUserRaw()
      if (userStr) {
        try {
          const user = JSON.parse(userStr)
          this.myUserId = user.id
          this.myUsername = user.username
        } catch (e) {
          console.error('解析用户信息失败', e)
        }
      }
    },

    async loadContacts() {
      try {
        const res = await this.request.get('/chat-message/contacts')
        if (res.code === '200') {
          this.contacts = res.data || []
        }
      } catch (e) {
        console.error('加载联系人失败', e)
      }
    },

    async loadGroups() {
      try {
        const res = await this.request.get('/chat-group')
        if (res.code === '200') {
          const groups = res.data || []
          // 为每个群加载未读数
          for (let g of groups) {
            g.unread = this.unreadMap['g-' + g.groupId] || 0
          }
          this.groups = groups
        }
      } catch (e) {
        console.error('加载群组失败', e)
      }
    },

    async loadAllUsers() {
      try {
        const res = await this.request.get('/chat-message/users')
        if (res.code === '200') {
          this.allUsers = res.data || []
        }
      } catch (e) {
        console.error('加载用户列表失败', e)
      }
    },

    connectWebSocket() {
      const userStr = getStoredUserRaw()
      const token = userStr ? JSON.parse(userStr).token : null
      if (!token || !this.myUsername) {
        setTimeout(() => this.connectWebSocket(), 1000)
        return
      }

      // 用 URL API 健壮地解析后端地址，避免正则拼接在不同部署环境出错
      let wsUrl
      try {
        const apiBase = import.meta.env.VUE_APP_API_BASE_URL || window.location.origin
        const apiUrl = new URL(apiBase)
        const wsProtocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'
        // apiUrl.host 含端口（若非标准端口），如 "localhost:9090" 或 "example.com"
        wsUrl = `${wsProtocol}//${apiUrl.host}/imserver/${encodeURIComponent(this.myUsername)}`
      } catch (e) {
        // 兜底
        wsUrl = `ws://localhost:9090/imserver/${encodeURIComponent(this.myUsername)}`
      }

      this.socket = new WebSocket(wsUrl)

      this.socket.onopen = () => {
        console.log('WebSocket connected')
        this.reconnectAttempts = 0
        // 连接后立即发送认证消息（不在 URL 中传递 token）
        this.socket.send(JSON.stringify({ type: 'auth', token: token }))
      }

      this.socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleSocketMessage(data)
        } catch (e) {
          console.error('解析WebSocket消息失败', e)
        }
      }

      this.socket.onclose = () => {
        if (this.reconnectAttempts < this.MAX_RECONNECT_ATTEMPTS) {
          const delay = Math.min(3000 * Math.pow(2, this.reconnectAttempts), 30000)
          console.log(`WebSocket disconnected, reconnecting in ${delay}ms (attempt ${this.reconnectAttempts + 1}/${this.MAX_RECONNECT_ATTEMPTS})...`)
          this.reconnectAttempts++
          this.reconnectTimer = setTimeout(() => this.connectWebSocket(), delay)
        } else {
          console.warn('WebSocket reconnect limit reached, giving up')
        }
      }

      this.socket.onerror = (e) => {
        console.error('WebSocket error', e)
      }
    },

    handleSocketMessage(data) {
      // 在线用户列表更新
      if (data.type === 'online_users') {
        return
      }

      // 私聊消息
      if (data.type === 'private') {
        const msg = {
          id: data.id,
          fromUserId: data.fromUserId,
          fromUsername: data.fromUsername,
          fromAvatar: data.fromAvatar,
          content: data.text,
          createTime: data.time,
          _tempId: 'ws-' + data.id
        }

        // 判断当前是否在跟这个人聊天
        if (this.activeChat && this.activeChat.type === 'private' && this.activeChat.id === msg.fromUserId) {
          this.currentMessages.push(msg)
          this.scrollToBottom()
          // 标记已读
          this.request.put('/chat-message/read/' + msg.fromUserId).catch(() => {})
        } else {
          // 更新未读计数
          const contact = this.contacts.find(c => c.userId === msg.fromUserId)
          if (contact) {
            contact.unread = (contact.unread || 0) + 1
            contact.lastMessage = msg.content
          }
        }
      }

      // 群聊消息
      if (data.type === 'group') {
        const groupId = data.toGroupId || data.groupId
        const msg = {
          id: data.id,
          fromUserId: data.fromUserId,
          fromUsername: data.fromUsername,
          fromAvatar: data.fromAvatar,
          content: data.text,
          createTime: data.time,
          toGroupId: groupId,
          _tempId: 'ws-' + data.id
        }

        if (this.activeChat && this.activeChat.type === 'group' && this.activeChat.id === groupId) {
          this.currentMessages.push(msg)
          this.scrollToBottom()
          this.request.put('/chat-message/read-group/' + groupId).catch(() => {})
        } else {
          const group = this.groups.find(g => g.groupId === groupId)
          if (group) {
            group.unread = (group.unread || 0) + 1
          }
        }
      }
    },

    openPrivateChat(contact) {
      this.activeChat = {
        type: 'private',
        id: contact.userId,
        name: contact.nickname || contact.username,
        avatar: contact.avatarUrl
      }
      this.currentMessages = []
      this.pageNum = 1
      this.hasMore = true
      this.loadPrivateMessages()

      // 标记已读
      if (contact.unread > 0) {
        contact.unread = 0
        this.request.put('/chat-message/read/' + contact.userId).catch(() => {})
      }
    },

    openGroupChat(group) {
      this.activeChat = {
        type: 'group',
        id: group.groupId,
        name: group.name,
        avatar: null
      }
      this.currentMessages = []
      this.pageNum = 1
      this.hasMore = true
      this.loadGroupMessages()

      if (group.unread > 0) {
        group.unread = 0
        this.request.put('/chat-message/read-group/' + group.groupId).catch(() => {})
      }
    },

    async loadPrivateMessages() {
      if (!this.activeChat || this.activeChat.type !== 'private') return
      this.loadingHistory = true
      try {
        const res = await this.request.get('/chat-message/private/' + this.activeChat.id, {
          params: { pageNum: this.pageNum, pageSize: this.pageSize }
        })
        if (res.code === '200') {
          const msgs = res.data || []
          if (msgs.length < this.pageSize) {
            this.hasMore = false
          }
          this.currentMessages = [...msgs, ...this.currentMessages]
        }
      } catch (e) {
        console.error('加载私聊消息失败', e)
      }
      this.loadingHistory = false
    },

    async loadGroupMessages() {
      if (!this.activeChat || this.activeChat.type !== 'group') return
      this.loadingHistory = true
      try {
        const res = await this.request.get('/chat-message/group/' + this.activeChat.id, {
          params: { pageNum: this.pageNum, pageSize: this.pageSize }
        })
        if (res.code === '200') {
          const msgs = res.data || []
          if (msgs.length < this.pageSize) {
            this.hasMore = false
          }
          this.currentMessages = [...msgs, ...this.currentMessages]
        }
      } catch (e) {
        console.error('加载群聊消息失败', e)
      }
      this.loadingHistory = false
    },

    onInputKeydown(e) {
      // Ctrl+Enter 发送，普通 Enter 换行
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault()
        this.sendTextMessage()
      }
    },

    sendTextMessage() {
      const text = this.inputText.trim()
      if (!text || !this.socket || this.socket.readyState !== WebSocket.OPEN) return

      if (this.activeChat.type === 'private') {
        // 找到目标用户名
        const contact = this.contacts.find(c => c.userId === this.activeChat.id)
        if (contact) {
          this.socket.send(JSON.stringify({
            to: contact.username,
            text: text,
            type: 'private'
          }))

          // 本地显示
          this.currentMessages.push({
            _tempId: 'local-' + Date.now(),
            fromUserId: this.myUserId,
            fromUsername: this.myUsername,
            fromAvatar: '',
            content: text,
            createTime: new Date().toISOString()
          })
          this.scrollToBottom()
        }
      } else if (this.activeChat.type === 'group') {
        this.socket.send(JSON.stringify({
          toGroup: this.activeChat.id,
          text: text,
          type: 'group'
        }))

        this.currentMessages.push({
          _tempId: 'local-' + Date.now(),
          fromUserId: this.myUserId,
          fromUsername: this.myUsername,
          fromAvatar: '',
          content: text,
          createTime: new Date().toISOString()
        })
        this.scrollToBottom()
      }

      this.inputText = ''
    },

    onScroll() {
      const el = this.$refs.chatMessages
      if (!el) return
      if (el.scrollTop === 0 && this.hasMore && !this.loadingHistory) {
        this.pageNum++
        if (this.activeChat.type === 'private') {
          this.loadPrivateMessages()
        } else if (this.activeChat.type === 'group') {
          this.loadGroupMessages()
        }
      }
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.chatMessages
        if (el) {
          el.scrollTop = el.scrollHeight
        }
      })
    },

    handlePanelCommand(cmd) {
      if (cmd === 'addFriend') {
        this.addFriendDialogVisible = true
      } else if (cmd === 'joinGroup') {
        this.joinGroupDialogVisible = true
      } else if (cmd === 'createGroup') {
        this.showCreateGroupDialog()
      }
    },

    showCreateGroupDialog() {
      this.newGroupName = ''
      this.newGroupMembers = []
      this.createGroupDialogVisible = true
      if (this.allUsers.length === 0) {
        this.loadAllUsers()
      }
    },

    /* ========== 好友申请 ========== */
    async loadFriendRequests() {
      try {
        const res = await this.request.get('/friendship/requests')
        if (res.code === '200') {
          this.friendRequests = res.data || []
        }
      } catch (e) { /* ignore */ }
    },
    async acceptRequest(req) {
      try {
        const res = await this.request.post(`/friendship/requests/${req.requestId}/accept`)
        if (res.code === '200') {
          this.$message.success(`已同意 ${req.fromNickname || req.fromUsername} 的好友申请`)
          this.friendRequests = this.friendRequests.filter(r => r.requestId !== req.requestId)
          this.loadContacts()
        } else {
          this.$message.error(res.msg || '操作失败')
        }
      } catch (e) { this.$message.error('操作失败') }
    },
    async rejectRequest(req) {
      try {
        const res = await this.request.post(`/friendship/requests/${req.requestId}/reject`)
        if (res.code === '200') {
          this.$message.info('已拒绝')
          this.friendRequests = this.friendRequests.filter(r => r.requestId !== req.requestId)
        }
      } catch (e) { this.$message.error('操作失败') }
    },

    /* ========== 添加好友 ========== */
    resetAddFriend() {
      this.searchUid = ''
      this.foundUser = null
      this.searchUserMsg = ''
      this.requestSent = false
    },
    async doSearchUser() {
      const uid = this.searchUid.trim()
      if (!uid) return
      this.searchingUser = true
      this.foundUser = null
      this.searchUserMsg = ''
      try {
        const res = await this.request.get('/friendship/search', { params: { uid } })
        if (res.code === '200') {
          this.foundUser = res.data
        } else {
          this.searchUserMsg = res.msg || '未找到该用户'
        }
      } catch (e) {
        this.searchUserMsg = '搜索失败，请稍后重试'
      }
      this.searchingUser = false
    },
    async doSendRequest() {
      if (!this.foundUser) return
      this.addingFriend = true
      try {
        const res = await this.request.post('/friendship/request', { toUserId: this.foundUser.user.id })
        if (res.code === '200') {
          this.$message.success('申请已发送，等待对方同意')
          this.requestSent = true
        } else {
          this.$message.error(res.msg || '发送失败')
        }
      } catch (e) {
        this.$message.error('发送失败')
      }
      this.addingFriend = false
    },

    /* ========== 加入群聊 ========== */
    resetJoinGroup() {
      this.searchGroupNumber = ''
      this.foundGroup = null
      this.searchGroupMsg = ''
    },
    async doSearchGroup() {
      const num = this.searchGroupNumber.trim()
      if (!num) return
      this.searchingGroup = true
      this.foundGroup = null
      this.searchGroupMsg = ''
      try {
        const res = await this.request.get('/chat-group/search', { params: { groupNumber: num } })
        if (res.code === '200') {
          this.foundGroup = res.data
        } else {
          this.searchGroupMsg = res.msg || '未找到该群聊'
        }
      } catch (e) {
        this.searchGroupMsg = '搜索失败，请稍后重试'
      }
      this.searchingGroup = false
    },
    async doJoinGroup() {
      if (!this.foundGroup) return
      this.joiningGroup = true
      try {
        const res = await this.request.post('/chat-group/join', { groupNumber: this.searchGroupNumber.trim() })
        if (res.code === '200') {
          this.$message.success('加群成功！')
          this.foundGroup.joined = true
          this.loadGroups()  // 刷新群列表
          this.joinGroupDialogVisible = false
        } else {
          this.$message.error(res.msg || '加群失败')
        }
      } catch (e) {
        this.$message.error('加群失败')
      }
      this.joiningGroup = false
    },

    async doCreateGroup() {
      try {
        const res = await this.request.post('/chat-group', {
          name: this.newGroupName.trim(),
          memberIds: this.newGroupMembers
        })
        if (res.code === '200') {
          this.$message.success('群聊创建成功')
          this.createGroupDialogVisible = false
          this.loadGroups()

          // 自动打开新群聊
          const group = res.data
          this.openGroupChat({ groupId: group.id, name: group.name, unread: 0 })
        } else {
          this.$message.error(res.msg || '创建失败')
        }
      } catch (e) {
        this.$message.error('创建群聊失败')
      }
    },

    async showInviteDialog() {
      this.inviteMemberIds = []
      this.inviteDialogVisible = true
      try {
        const res = await this.request.get('/chat-group/' + this.activeChat.id + '/non-members')
        if (res.code === '200') {
          this.nonMembers = res.data || []
        }
      } catch (e) {
        console.error('加载可邀请用户失败', e)
      }
    },

    async doInvite() {
      try {
        const res = await this.request.post('/chat-group/' + this.activeChat.id + '/members', {
          userIds: this.inviteMemberIds
        })
        if (res.code === '200') {
          this.$message.success('邀请成功')
          this.inviteDialogVisible = false
        } else {
          this.$message.error(res.msg || '邀请失败')
        }
      } catch (e) {
        this.$message.error('邀请失败')
      }
    },

    formatTime(time) {
      if (!time) return ''
      const d = new Date(time)
      const now = new Date()
      if (d.toDateString() === now.toDateString()) {
        return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      }
      return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' +
        d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 60px);
  background: #f6f9f7;
}

/* 左侧面板 */
.left-panel {
  width: 320px;
  min-width: 320px;
  background: #ffffff;
  border-right: 1px solid #eef2f0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 24px 14px;
  border-bottom: none;
}

.panel-title {
  font-size: 22px;
  font-weight: 800;
  color: #172033;
  letter-spacing: 0;
}

.search-box {
  padding: 4px 24px 14px;
}

.search-box :deep(.el-input__wrapper) {
  min-height: 36px;
  border-radius: 8px;
  background: #f4f7f5;
  box-shadow: none !important;
  border: 1px solid transparent;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.search-box :deep(.el-input__wrapper:hover),
.search-box :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  border-color: #b9dec1;
}

.search-box :deep(.el-input__inner) {
  box-shadow: none !important;
  border: none !important;
  background: transparent !important;
}

.contact-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 12px 16px;
}

.contact-group {
  margin-bottom: 4px;
}

.group-label {
  padding: 12px 12px 6px;
  font-size: 12px;
  color: #8b98a7;
  font-weight: 700;
}

.contact-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  position: relative;
  border-radius: 8px;
}

.contact-item:hover {
  background: #f5f8f6;
}

.contact-item.active {
  background: #e8f6ee;
}

.avatar-group {
  margin-right: 12px;
  flex-shrink: 0;
}

.contact-info {
  flex: 1;
  min-width: 0;
}

.contact-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}

.member-count {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

.contact-last-msg {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-badge {
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 9px;
  padding: 0 5px;
  flex-shrink: 0;
}

/* 右侧面板 */
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #eef2f0;
  background: #fff;
}

.chat-header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-header-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.group-tag {
  font-size: 11px;
  background: #e6f7e6;
  color: #67c23a;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: normal;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f8faf9;
}

.loading-more {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  padding: 8px 0;
}

.message-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 8px;
}

.message-other {
  flex-direction: row;
}

.message-self {
  flex-direction: row-reverse;
}

.message-bubble-wrapper {
  max-width: 60%;
}

.message-sender {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.message-other .message-bubble {
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,0.08);
  border-top-left-radius: 2px;
}

.message-self .message-bubble {
  background: #42b883;
  color: #fff;
  border-top-right-radius: 2px;
}

.message-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

.message-self .message-time {
  text-align: right;
}

/* 输入区域 */
.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 20px;
  border-top: 1px solid #eef2f0;
  background: #fff;
}

/* ====== 添加好友 / 加群 对话框 ====== */
.add-dialog-body {
  padding: 8px 0;
}

.found-card {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 20px;
  padding: 16px;
  border-radius: 10px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
}

.found-info {
  flex: 1;
}

.found-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.found-uid {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
}

.search-tip {
  margin-top: 14px;
  text-align: center;
  color: #f56c6c;
  font-size: 13px;
}

.chat-input-area .el-textarea {
  flex: 1;
}

.send-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.send-hint {
  font-size: 11px;
  color: #c0c4cc;
  white-space: nowrap;
}

.chat-input-area .el-button {
  height: 36px;
  flex-shrink: 0;
}

/* 好友申请区域 */
.request-section {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 8px;
  margin-bottom: 4px;
}

.request-section .group-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.request-badge {
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 9px;
  padding: 0 5px;
}

.request-item {
  display: flex;
  align-items: center;
  padding: 10px 20px;
  gap: 10px;
}

.request-info {
  flex: 1;
  min-width: 0;
}

.request-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.request-uid {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.request-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
</style>
