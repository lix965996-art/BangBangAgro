<template>
  <div class="person-page">
    <!-- ── 左侧 ── -->
    <div class="left-col">
      <!-- 用户信息卡 -->
      <el-card class="person-card profile-card">
        <div class="profile-top">
          <el-upload
            class="avatar-uploader"
            :action="apiBaseUrl + '/file/upload'"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <img v-if="form.avatarUrl" :src="form.avatarUrl" class="avatar" />
            <div v-else class="avatar-placeholder">
              <i class="el-icon-user"></i>
            </div>
          </el-upload>

          <!-- 帮帮农ID -->
          <div class="uid-row">
            <span class="uid-label">帮帮农ID</span>
            <span class="uid-value">{{ form.uid || '暂无' }}</span>
            <el-button v-if="form.uid" link size="small" icon="el-icon-document-copy" @click="copyUid">复制</el-button>
            <el-tooltip content="把这个ID告诉好友，对方可通过「添加好友」搜索你" placement="right">
              <i class="el-icon-question uid-tip"></i>
            </el-tooltip>
          </div>
        </div>

        <!-- 用户信息列表 -->
        <div class="profile-fields">
          <div class="profile-field">
            <span class="field-icon"><i class="el-icon-user"></i></span>
            <span class="field-label">用户名</span>
            <span class="field-value">{{ form.username || '—' }}</span>
          </div>
          <div class="profile-field">
            <span class="field-icon"><i class="el-icon-postcard"></i></span>
            <span class="field-label">昵称</span>
            <input class="field-input" v-model="form.nickname" placeholder="填写昵称" />
          </div>
          <div class="profile-field">
            <span class="field-icon"><i class="el-icon-message"></i></span>
            <span class="field-label">邮箱</span>
            <input class="field-input" v-model="form.email" placeholder="填写邮箱" />
          </div>
          <div class="profile-field">
            <span class="field-icon"><i class="el-icon-phone-outline"></i></span>
            <span class="field-label">电话</span>
            <input class="field-input" v-model="form.phone" placeholder="填写电话" />
          </div>
          <div class="profile-field">
            <span class="field-icon"><i class="el-icon-location-outline"></i></span>
            <span class="field-label">地址</span>
            <input class="field-input" v-model="form.address" placeholder="填写地址" />
          </div>
        </div>

        <div class="profile-actions">
          <el-button type="primary" icon="el-icon-edit" @click="save">编辑资料</el-button>
        </div>
      </el-card>

      <!-- AI 待审批任务 -->
      <el-card class="person-card pending-card">
        <template #header>
          <div class="ai-config-head">
            <div class="ai-config-head-left">
              <div class="ai-config-title">
                <i class="el-icon-finished"></i>
                <span>AI 待审批任务</span>
              </div>
              <div class="ai-config-subtitle">AI 在写操作或全审批模式下提交的写操作，确认后才会真正执行。</div>
            </div>
            <el-button size="small" icon="el-icon-refresh" :loading="pendingLoading" @click="loadPendingTasks">刷新</el-button>
          </div>
        </template>
        <el-empty v-if="!pendingTasks.length" description="暂无待审批任务" :image-size="60">
          <template #image>
            <div class="empty-icon">
              <i class="el-icon-document-checked"></i>
            </div>
          </template>
        </el-empty>
        <div v-else class="pending-list">
          <div v-for="t in pendingTasks" :key="t.taskId" class="pending-item">
            <div class="pending-main">
              <el-tag size="small" :type="riskTagType(t.riskLevel)">{{ riskLabel(t.riskLevel) }}</el-tag>
              <span class="pending-action">{{ actionLabel(t.actionType) }}</span>
            </div>
            <div class="pending-meta">
              <span class="pending-params">{{ t.actionParams }}</span>
              <span v-if="t.createdAt" class="pending-time">{{ formatTime(t.createdAt) }}</span>
            </div>
            <div class="pending-main" style="margin-top: 6px;">
              <span class="pending-reasoning">{{ t.reasoning }}</span>
            </div>
            <div class="pending-btns">
              <el-button size="small" type="success" @click="approvePending(t)">批准并执行</el-button>
              <el-button size="small" type="danger" @click="rejectPending(t)">拒绝</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- ── 右侧 ── -->
    <div class="right-col">
      <!-- AI 模型配置 -->
      <el-card class="person-card ai-config-card">
        <template #header>
          <div class="ai-config-head">
            <div class="ai-config-head-left">
              <div class="ai-config-title">
                <i class="el-icon-setting"></i>
                <span>AI 模型配置</span>
              </div>
              <div class="ai-config-subtitle">填写主模型即可使用；对话模型和空间全自动使用主模型。</div>
            </div>
            <el-tag v-if="aiCfg.provider" size="small" type="success">
              {{ providerLabel(aiCfg.provider) }}
            </el-tag>
          </div>
        </template>

        <el-form :model="aiCfg" label-position="top" size="small" class="ai-config-form">
          <!-- 提供商 + 模型 -->
          <div class="ai-main-grid">
            <el-form-item label="提供商">
              <el-select v-model="aiCfg.provider" placeholder="选择提供商" @change="onProviderChange">
                <el-option label="通义千问 (Qwen)" value="qwen"></el-option>
                <el-option label="DeepSeek" value="deepseek"></el-option>
                <el-option label="智谱 GLM" value="glm"></el-option>
                <el-option label="MiniMax" value="minimax"></el-option>
                <el-option label="OpenAI" value="openai"></el-option>
                <el-option label="自定义" value="custom"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="模型">
              <el-input v-model="aiCfg.modelName" placeholder="qwen-max / deepseek-chat"></el-input>
            </el-form-item>
          </div>

          <!-- Base URL -->
          <el-form-item label="Base URL">
            <el-input v-model="aiCfg.baseUrl" placeholder="https://api.xxx.com/v1"
                      :disabled="aiCfg.provider !== 'custom'" clearable></el-input>
          </el-form-item>

          <!-- API Key -->
          <el-form-item label="API Key">
            <el-input v-model="aiCfg.apiKey" placeholder="粘贴你的 API Key"
                      show-password clearable autocomplete="new-password"></el-input>
          </el-form-item>

          <!-- AI 写操作审批策略 -->
          <el-form-item label="AI 写操作审批策略">
            <div class="policy-cards">
              <div
                class="policy-card"
                :class="{ active: aiCfg.aiActionPolicy === 'full_auto' }"
                @click="aiCfg.aiActionPolicy = 'full_auto'"
              >
                <i class="el-icon-video-play"></i>
                <div class="policy-card-title">全自动</div>
                <div class="policy-card-desc">AI 直接执行所有写操作</div>
              </div>
              <div
                class="policy-card"
                :class="{ active: aiCfg.aiActionPolicy === 'semi_approval' }"
                @click="aiCfg.aiActionPolicy = 'semi_approval'"
              >
                <i class="el-icon-finished"></i>
                <div class="policy-card-title">半审批（建议）</div>
                <div class="policy-card-desc">通知/库存自动，采购/销售待确认</div>
              </div>
              <div
                class="policy-card"
                :class="{ active: aiCfg.aiActionPolicy === 'full_approval' }"
                @click="aiCfg.aiActionPolicy = 'full_approval'"
              >
                <i class="el-icon-lock"></i>
                <div class="policy-card-title">全审批</div>
                <div class="policy-card-desc">所有写操作都待确认</div>
              </div>
            </div>
            <div class="ai-policy-hint">控制 AI 下单 / 改库存 / 发通知等具备执行还是提交给你确认。默认半审批。</div>
          </el-form-item>

          <!-- 折叠区：对话模型 -->
          <el-collapse v-model="openAiSections" class="ai-advanced-collapse">
            <el-collapse-item name="chat">
              <template #title>
                <span class="collapse-title">对话模型（可选）</span>
              </template>
              <div class="collapse-subtitle">用于文本对话场景，若未配置则使用主模型</div>
              <div class="ai-advanced-grid">
                <el-form-item label="对话模型">
                  <el-input v-model="aiCfg.chatModelName" placeholder="留空复用主模型" clearable></el-input>
                </el-form-item>
                <el-form-item label="对话 URL">
                  <el-input v-model="aiCfg.chatBaseUrl" placeholder="留空复用主模型 Base URL" clearable></el-input>
                </el-form-item>
              </div>
              <el-form-item label="对话 Key">
                <el-input v-model="aiCfg.chatApiKey" placeholder="留空复用主模型 API Key"
                          show-password clearable autocomplete="new-password"></el-input>
              </el-form-item>
            </el-collapse-item>

            <!-- 视觉模型 -->
            <el-collapse-item name="vision">
              <template #title>
                <span class="collapse-title">视觉模型（多模态 · 果蔬病虫害/成熟度识别）</span>
              </template>
              <div class="collapse-subtitle">用于「果蔬检测 → 多模态视觉」引擎，必须填支持图片的多模态模型（名字一般带 VL / vision）</div>
              <div class="ai-advanced-grid">
                <el-form-item label="视觉模型">
                  <el-input v-model="aiCfg.visionModelName" placeholder="填带『视觉/VL』标签的多模态模型" clearable></el-input>
                </el-form-item>
                <el-form-item label="视觉 URL">
                  <el-input v-model="aiCfg.visionBaseUrl" placeholder="豆包/硅基流动等视觉API地址" clearable></el-input>
                </el-form-item>
              </div>
              <el-form-item label="视觉 Key">
                <el-input v-model="aiCfg.visionApiKey" placeholder="视觉模型独立 Key（不与主模型共用）"
                          show-password clearable autocomplete="new-password"></el-input>
              </el-form-item>
              <div class="ai-policy-hint">三项留空则使用后端默认配置。</div>
            </el-collapse-item>
          </el-collapse>

          <!-- 操作按钮 -->
          <div class="ai-actions">
            <el-button type="primary" icon="el-icon-folder-checked" :loading="aiSaving" @click="saveAiConfig">保存</el-button>
            <el-button icon="el-icon-video-play" :loading="aiTesting" @click="testAiConnection">测试主模型</el-button>
            <el-button icon="el-icon-chat-line-round" :loading="aiTestingChat" @click="testChatConnection">测试对话模型</el-button>
            <el-button icon="el-icon-picture-outline" :loading="aiTestingVision" @click="testVisionConnection">测试视觉模型</el-button>
          </div>
        </el-form>
      </el-card>

    </div>
  </div>
</template>

<script>
import { getStoredUserRaw, setStoredUser } from '@/utils/authStorage'

export default {
  name: "Person",
  data() {
    let user = {};
    try {
      const userStr = getStoredUserRaw();
      user = userStr ? JSON.parse(userStr) : {};
    } catch (e) {
      console.error('解析用户信息失败:', e);
      user = {};
    }

    return {
      form: {},
      user: user,
      // ── AI 配置 ──
      aiCfg: {
        provider: 'qwen',
        baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
        apiKey: '',
        modelName: 'qwen-max',
        chatModelName: '',
        chatBaseUrl: '',
        chatApiKey: '',
        visionModelName: '',
        visionBaseUrl: '',
        visionApiKey: '',
        aiActionPolicy: 'semi_approval',
      },
      aiPresets: {},
      pendingTasks: [],
      pendingLoading: false,
      aiSaving: false,
      aiTesting: false,
      aiTestingChat: false,
      aiTestingVision: false,
      openAiSections: ['vision'],
    }
  },
  created() {
    this.getUser().then(res => { this.form = res })
    this.loadAiConfig()
    this.loadAiPresets()
    this.loadPendingTasks()
  },
  computed: {
    apiBaseUrl() {
      return this.request.defaults.baseURL || ''
    },
    uploadHeaders() {
      try {
        const userStr = getStoredUserRaw()
        const user = userStr ? JSON.parse(userStr) : null
        return user && user.token ? { token: user.token } : {}
      } catch (e) {
        return {}
      }
    }
  },
  methods: {
    async getUser() {
      if (!this.user.username) {
        this.$message.error("用户信息异常，请重新登录")
        return {}
      }
      try {
        return (await this.request.get("/user/username/" + this.user.username)).data
      } catch (e) {
        this.$message.error("获取用户信息失败")
        return {}
      }
    },
    save() {
      this.request.post("/user", this.form).then(res => {
        if (res.code === '200') {
          this.$message.success("保存成功")

          // 触发父级更新User的方法
          this.$emit("refreshUser")

          // 更新浏览器存储的用户信息
          this.getUser().then(res => {
            const stored = getStoredUserRaw();
            const currentUser = stored ? JSON.parse(stored) : {};
            res.token = currentUser.token || '';
            setStoredUser(res)
          })

        } else {
          this.$message.error("保存失败")
        }
      })
    },
    copyUid() {
      const uid = this.form.uid
      if (!uid) return
      if (navigator.clipboard) {
        navigator.clipboard.writeText(uid).then(() => {
          this.$message.success('帮帮农ID已复制：' + uid)
        })
      } else {
        // 兜底方案
        const el = document.createElement('textarea')
        el.value = uid
        document.body.appendChild(el)
        el.select()
        document.execCommand('copy')
        document.body.removeChild(el)
        this.$message.success('帮帮农ID已复制：' + uid)
      }
    },
    handleAvatarSuccess(res) {
      const url = typeof res === 'string' ? res : (res && res.data)
      if (url && typeof url === 'string') {
        this.form.avatarUrl = url
        this.$message.success("上传成功")
        return
      }
      this.$message.error((res && res.msg) || "上传失败")
    },

    // ── AI 配置方法 ──
    async loadAiPresets() {
      try {
        const res = await this.request.get('/ai-config/presets')
        if (res.code === '200') this.aiPresets = res.data
      } catch (e) { /* 忽略 */ }
    },
    async loadAiConfig() {
      try {
        const res = await this.request.get('/ai-config')
        if (res.code === '200' && res.data) {
          this.aiCfg = Object.assign(this.aiCfg, res.data)
        }
      } catch (e) { /* 忽略 */ }
    },
    onProviderChange(provider) {
      if (provider === 'custom') return
      const preset = this.aiPresets[provider]
      if (preset) {
        this.aiCfg.baseUrl   = preset.baseUrl
        this.aiCfg.modelName = preset.model
      }
    },
    async saveAiConfig() {
      // 只在主模型 Key 完全为空时拦截；含 **** 的脱敏 Key 后端会自动还原，无需重填
      if (!this.aiCfg.apiKey) {
        this.$message.warning('请输入 API Key')
        return
      }
      this.aiSaving = true
      try {
        const res = await this.request.post('/ai-config', this.aiCfg)
        if (res.code === '200') {
          this.$message.success('AI 配置已保存 ✅')
          this.aiCfg = Object.assign(this.aiCfg, res.data)
        } else {
          this.$message.error(res.msg || '保存失败')
        }
      } catch (e) {
        this.$message.error('保存失败：' + e.message)
      } finally {
        this.aiSaving = false
      }
    },
    async testAiConnection() {
      if (!this.aiCfg.apiKey || this.aiCfg.apiKey.includes('****')) {
        this.$message.warning('请先填写 API Key 并保存，再测试')
        return
      }
      this.aiTesting = true
      try {
        const res = await this.request.post('/ai-config/test', this.aiCfg)
        if (res.code === '200') {
          this.$message.success(res.data || '连接成功 ✅')
        } else {
          this.$message.error('连接失败：' + (res.msg || res.data))
        }
      } catch (e) {
        this.$message.error('测试异常：' + e.message)
      } finally {
        this.aiTesting = false
      }
    },
    async testChatConnection() {
      // 对话模型 Key 可能为空（此时复用主模型），用主 Key 兜底判断
      const effectiveKey = this.aiCfg.chatApiKey && !this.aiCfg.chatApiKey.includes('****')
        ? this.aiCfg.chatApiKey
        : this.aiCfg.apiKey
      if (!effectiveKey || effectiveKey.includes('****')) {
        this.$message.warning('请先填写 API Key 并保存，再测试')
        return
      }
      // 构造测试用配置：用对话模型字段，留空时复用主模型字段
      const testCfg = {
        provider:  this.aiCfg.provider,
        baseUrl:   this.aiCfg.chatBaseUrl  || this.aiCfg.baseUrl,
        apiKey:    this.aiCfg.chatApiKey   || this.aiCfg.apiKey,
        modelName: this.aiCfg.chatModelName || this.aiCfg.modelName,
      }
      this.aiTestingChat = true
      try {
        const res = await this.request.post('/ai-config/test', testCfg)
        if (res.code === '200') {
          this.$message.success((res.data || '连接成功 ✅') + '（对话模型）')
        } else {
          this.$message.error('对话模型连接失败：' + (res.msg || res.data))
        }
      } catch (e) {
        this.$message.error('测试异常：' + e.message)
      } finally {
        this.aiTestingChat = false
      }
    },
    async testVisionConnection() {
      // 视觉模型独立配置，不复用主模型；三项需填齐才能测（保存后 Key 会脱敏，需重填才能测）
      if (!this.aiCfg.visionApiKey || this.aiCfg.visionApiKey.includes('****')) {
        this.$message.warning('请先填写视觉模型 Key 再测试（保存后 Key 脱敏需重填）')
        return
      }
      if (!this.aiCfg.visionModelName) { this.$message.warning('请填写视觉模型名'); return }
      if (!this.aiCfg.visionBaseUrl) { this.$message.warning('请填写视觉 URL'); return }
      const testCfg = {
        provider: this.aiCfg.provider,
        baseUrl: this.aiCfg.visionBaseUrl,
        apiKey: this.aiCfg.visionApiKey,
        modelName: this.aiCfg.visionModelName,
      }
      this.aiTestingVision = true
      try {
        const res = await this.request.post('/ai-config/test', testCfg)
        if (res.code === '200') {
          this.$message.success((res.data || '连接成功 ✅') + '（视觉模型）')
        } else {
          this.$message.error('视觉模型连接失败：' + (res.msg || res.data))
        }
      } catch (e) {
        this.$message.error('测试异常：' + e.message)
      } finally {
        this.aiTestingVision = false
      }
    },
    providerLabel(p) {
      const map = { qwen:'通义千问', deepseek:'DeepSeek', glm:'智谱GLM', minimax:'MiniMax', openai:'OpenAI', custom:'自定义' }
      return map[p] || p
    },
    // ── AI 待审批任务 ──
    async loadPendingTasks() {
      this.pendingLoading = true
      try {
        const res = await this.request.get('/api/agent/tasks/pending-approval', { params: { limit: 20 } })
        if (res.code === '200') this.pendingTasks = res.data || []
      } catch (e) { /* 忽略 */ }
      finally { this.pendingLoading = false }
    },
    async approvePending(t) {
      try {
        const res = await this.request.post('/api/agent/task/' + t.taskId + '/approve')
        if (res.code === '200') {
          this.$message.success(res.data || '已执行')
        } else {
          this.$message.error(res.msg || '执行失败')
        }
        this.loadPendingTasks()
      } catch (e) {
        this.$message.error('执行异常：' + e.message)
      }
    },
    async rejectPending(t) {
      try {
        const res = await this.request.post('/api/agent/task/' + t.taskId + '/reject')
        if (res.code === '200') {
          this.$message.success('已拒绝')
        } else {
          this.$message.error(res.msg || '操作失败')
        }
        this.loadPendingTasks()
      } catch (e) {
        this.$message.error('操作异常：' + e.message)
      }
    },
    actionLabel(a) {
      const map = { create_purchase:'创建采购单', create_sale:'创建销售单', update_inventory:'调整库存', send_notification:'发送通知' }
      return map[a] || a
    },
    riskLabel(r) {
      return { high:'高风险', medium:'中风险', low:'低风险' }[r] || r
    },
    riskTagType(r) {
      return { high:'danger', medium:'warning', low:'info' }[r] || ''
    },
    formatTime(t) {
      try { return new Date(t).toLocaleString('zh-CN') } catch (e) { return t }
    },
  }
}
</script>

<style>
/* ====== 全局布局 ====== */
.person-page {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 24px 32px;
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.left-col {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

/* 待审批卡片在左侧更紧凑 */
.pending-card .el-card__header {
  padding: 16px 20px 12px !important;
  background: #f9fcfa !important;
  border-bottom: 1px solid #e8f1eb !important;
}

.pending-card .el-card__body {
  padding: 16px 20px !important;
  max-height: 400px;
  overflow-y: auto;
}

.pending-reasoning {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

/* ====== 卡片通用 ====== */
.person-card {
  width: 100%;
  margin: 0;
  border: 1px solid #e8f0eb !important;
  border-radius: 12px !important;
  box-shadow: 0 2px 12px rgba(16, 185, 129, 0.06) !important;
  overflow: hidden;
}

.person-card .el-card__body {
  padding: 0 !important;
}

/* ====== 左侧 · 用户资料卡 ====== */
.profile-card .el-card__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 24px 24px !important;
}

.profile-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding-bottom: 24px;
  border-bottom: 1px solid #eef3f0;
  width: 100%;
}

/* 头像 */
.avatar-uploader .el-upload {
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;
  width: 100px;
  height: 100px;
  display: block;
  border: 3px solid #d1fae5;
  transition: border-color 0.2s;
}

.avatar-uploader .el-upload:hover {
  border-color: #10b981;
}

.avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 50%;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #d1fae5, #a7f3d0);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  color: #059669;
}

/* 帮帮农ID */
.uid-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}

.uid-label {
  font-size: 13px;
  color: #6b7280;
}

.uid-value {
  font-size: 20px;
  font-weight: 800;
  color: #059669;
  letter-spacing: 1px;
}

.uid-tip {
  color: #c0c4cc;
  cursor: pointer;
  font-size: 14px;
}

/* 用户信息字段 */
.profile-fields {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 8px 0;
}

.profile-field {
  display: flex;
  align-items: center;
  padding: 12px 4px;
  border-bottom: 1px solid #f3f6f4;
}

.profile-field:last-child {
  border-bottom: none;
}

.field-icon {
  width: 28px;
  flex-shrink: 0;
  color: #9ca3af;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.field-label {
  width: 56px;
  flex-shrink: 0;
  font-size: 13px;
  color: #6b7280;
  margin-left: 4px;
}

.field-value {
  flex: 1;
  font-size: 14px;
  color: #111827;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-size: 14px;
  color: #111827;
  padding: 4px 8px;
  border-radius: 6px;
  outline: none;
  transition: background 0.2s;
}

.field-input:hover {
  background: #f3f6f4;
}

.field-input:focus {
  background: #ecfdf5;
}

.field-input::placeholder {
  color: #d1d5db;
}

.profile-actions {
  padding-top: 20px;
  width: 100%;
}

.profile-actions .el-button {
  width: 100%;
}

/* ====== 右侧 · AI 配置卡 ====== */
.ai-config-card .el-card__header {
  padding: 20px 24px 16px !important;
  background: #f9fcfa !important;
  border-bottom: 1px solid #e8f1eb !important;
}

.ai-config-card .el-card__body {
  padding: 24px !important;
}

.ai-config-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.ai-config-head-left {
  flex: 1;
  min-width: 0;
}

.ai-config-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.ai-config-title i {
  color: #10b981;
  font-size: 18px;
}

.ai-config-subtitle {
  margin-top: 4px;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.5;
}

/* 表单 */
.ai-config-form .el-form-item {
  margin-bottom: 18px;
}

.ai-config-form .el-form-item__label {
  padding-bottom: 6px !important;
  color: #374151 !important;
  font-weight: 600 !important;
  font-size: 13px !important;
}

.ai-main-grid,
.ai-advanced-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* 输入框样式 */
.person-page .el-input__wrapper,
.person-page .el-select .el-input__wrapper,
.person-page .el-textarea__inner {
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  box-shadow: none !important;
  background: #fff !important;
}

.person-page .el-input__wrapper:hover,
.person-page .el-select .el-input__wrapper:hover,
.person-page .el-textarea__inner:hover {
  border-color: #a7f3d0 !important;
}

.person-page .el-input__wrapper.is-focus,
.person-page .el-select .el-input__wrapper.is-focus,
.person-page .el-textarea__inner:focus {
  border-color: #10b981 !important;
}

body .person-page .el-input__inner,
body .person-page .el-input__inner:focus,
body .person-page .el-select .el-input__inner,
body .person-page .el-select .el-input.is-focus .el-input__inner {
  border: 0 !important;
  box-shadow: none !important;
  background: transparent !important;
  border-radius: 0 !important;
}

.ai-config-form .el-input__wrapper,
.ai-config-form .el-select .el-input__wrapper {
  min-height: 36px;
}

/* ====== 审批策略卡片 ====== */
.policy-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.policy-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.policy-card:hover {
  border-color: #a7f3d0;
  background: #f0fdf4;
}

.policy-card.active {
  border-color: #10b981;
  background: #ecfdf5;
  box-shadow: 0 0 0 1px #10b981;
}

.policy-card i {
  font-size: 22px;
  color: #9ca3af;
  margin-bottom: 8px;
  display: block;
}

.policy-card.active i {
  color: #10b981;
}

.policy-card-title {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 4px;
}

.policy-card-desc {
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.4;
}

/* ====== 折叠区 ====== */
.ai-advanced-collapse {
  margin: 4px 0 20px;
  border: 1px solid #e8f1eb;
  border-radius: 10px;
  overflow: hidden;
  background: #f9fcfa;
}

.ai-advanced-collapse .el-collapse-item__header {
  height: 44px;
  padding: 0 16px;
  background: #f9fcfa;
  border-bottom: none;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.ai-advanced-collapse .el-collapse-item__wrap {
  border-top: 1px solid #edf3ef;
  border-bottom: none;
}

.ai-advanced-collapse .el-collapse-item__content {
  padding: 16px 16px 4px;
}

.collapse-title {
  color: #374151;
}

.collapse-subtitle {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 14px;
  line-height: 1.4;
}

/* ====== 操作按钮 ====== */
.ai-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding-top: 4px;
}

.ai-actions .el-button {
  margin-left: 0 !important;
}

/* ====== 空状态 ====== */
.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #f0fdf4;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.empty-icon i {
  font-size: 32px;
  color: #10b981;
}

/* ====== 待审批任务列表 ====== */
.pending-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pending-item {
  border: 1px solid #e8f1eb;
  border-radius: 10px;
  padding: 14px;
  background: #f9fcfa;
  transition: border-color 0.2s;
}

.pending-item:hover {
  border-color: #a7f3d0;
}

.pending-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.pending-action {
  font-weight: 600;
  color: #111827;
}

.pending-params {
  font-size: 12px;
  color: #9ca3af;
  word-break: break-all;
}

.pending-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #9ca3af;
  margin: 8px 0;
}

.pending-btns {
  display: flex;
  gap: 8px;
}

.ai-policy-hint {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
  margin-top: 6px;
}

/* ====== 响应式 ====== */
@media (max-width: 980px) {
  .person-page {
    grid-template-columns: 1fr;
    padding: 16px;
  }

  .ai-main-grid,
  .ai-advanced-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .policy-cards {
    grid-template-columns: 1fr;
  }
}
</style>
