<template>
  <div class="person-page">
  <el-card class="person-card">
    <el-form label-width="80px" size="small">
      <el-upload
          class="avatar-uploader"
          :action="apiBaseUrl + '/file/upload'"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleAvatarSuccess"
      >
        <img v-if="form.avatarUrl" :src="form.avatarUrl" class="avatar">
        <i v-else class="el-icon-plus avatar-uploader-icon"></i>
      </el-upload>

      <!-- 帮帮农ID（只读，可一键复制） -->
      <el-form-item label="帮帮农ID">
        <div class="uid-row">
          <span class="uid-value">{{ form.uid || '暂无' }}</span>
          <el-button
            v-if="form.uid"
            link
            size="mini"
            icon="el-icon-document-copy"
            @click="copyUid"
          >复制</el-button>
          <el-tooltip content="把这个ID告诉好友，对方可通过「添加好友」搜索你" placement="right">
            <i class="el-icon-question uid-tip"></i>
          </el-tooltip>
        </div>
      </el-form-item>

      <el-form-item label="用户名">
        <el-input v-model="form.username" disabled autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="form.nickname" autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item label="电话">
        <el-input v-model="form.phone" autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item label="地址">
        <el-input type="textarea" v-model="form.address" autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">确 定</el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <!-- ── AI 模型配置 ── -->
  <el-card class="person-card ai-config-card">
    <template #header>
      <div class="ai-config-head">
        <div>
          <div class="ai-config-title">
            <i class="el-icon-cpu"></i>
            <span>AI 模型配置</span>
          </div>
          <div class="ai-config-subtitle">填写主模型即可使用；对话模型留空时会自动复用主模型。</div>
        </div>
        <el-tag v-if="aiCfg.provider" size="small" type="success">
          {{ providerLabel(aiCfg.provider) }}
        </el-tag>
      </div>
    </template>
    <el-form :model="aiCfg" label-position="top" size="small" class="ai-config-form">

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

      <el-collapse v-model="openAiSections" class="ai-advanced-collapse">
        <el-collapse-item name="chat">
          <template #title>
            <span class="collapse-title">对话模型（可选）</span>
          </template>

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
      </el-collapse>

      <div class="ai-actions">
        <el-button type="primary" :loading="aiSaving" @click="saveAiConfig">保存</el-button>
        <el-button :loading="aiTesting" @click="testAiConnection">
          <i class="el-icon-connection"></i> 测试主模型
        </el-button>
        <el-button :loading="aiTestingChat" @click="testChatConnection">
          <i class="el-icon-chat-line-round"></i> 测试对话模型
        </el-button>
      </div>

    </el-form>
  </el-card>
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
      },
      aiPresets: {},
      aiSaving: false,
      aiTesting: false,
      aiTestingChat: false,
      openAiSections: [],
    }
  },
  created() {
    this.getUser().then(res => { this.form = res })
    this.loadAiConfig()
    this.loadAiPresets()
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
    providerLabel(p) {
      const map = { qwen:'通义千问', deepseek:'DeepSeek', glm:'智谱GLM', minimax:'MiniMax', openai:'OpenAI', custom:'自定义' }
      return map[p] || p
    },
  }
}
</script>

<style>
.person-page {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 24px 32px;
  display: grid;
  grid-template-columns: 400px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  justify-content: stretch;
}

.person-card {
  width: 100%;
  margin: 0;
  border: 1px solid #edf2ee !important;
  border-radius: 8px !important;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04) !important;
  overflow: hidden;
}

.person-card .el-card__body {
  padding: 24px !important;
}

.ai-config-card {
  margin-top: 0;
}

.ai-config-card .el-card__header {
  padding: 18px 24px !important;
  background: #f7fbf8 !important;
  border-bottom: 1px solid #e8f1eb !important;
}

.ai-config-card .el-card__body {
  padding: 24px !important;
}

.ai-config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.ai-config-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1f3d2b;
  font-size: 16px;
  font-weight: 800;
}

.ai-config-title i {
  color: #10b981;
  font-size: 18px;
}

.ai-config-subtitle {
  margin-top: 5px;
  color: #7b8794;
  font-size: 12px;
  line-height: 1.4;
}

.ai-config-form .el-form-item {
  margin-bottom: 16px;
}

.ai-config-form .el-form-item__label {
  padding-bottom: 5px !important;
  color: #4f6b58 !important;
  font-weight: 700 !important;
}

.ai-main-grid,
.ai-advanced-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.person-page .el-input__wrapper,
.person-page .el-select .el-input__wrapper,
.person-page .el-textarea__inner {
  border: 1px solid #dfe7e2 !important;
  border-radius: 6px !important;
  box-shadow: none !important;
  background: #fff !important;
}

.person-page .el-input__wrapper:hover,
.person-page .el-select .el-input__wrapper:hover,
.person-page .el-textarea__inner:hover {
  border-color: #a8d8b2 !important;
}

.person-page .el-input__wrapper.is-focus,
.person-page .el-select .el-input__wrapper.is-focus,
.person-page .el-textarea__inner:focus {
  border-color: #42b883 !important;
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

.ai-advanced-collapse {
  margin: 2px 0 20px;
  border: 1px solid #e8f1eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fbfdfb;
}

.ai-advanced-collapse .el-collapse-item__header {
  height: 42px;
  padding: 0 14px;
  background: #fbfdfb;
  border-bottom: none;
  color: #5f6f65;
  font-size: 13px;
  font-weight: 700;
}

.ai-advanced-collapse .el-collapse-item__wrap {
  border-top: 1px solid #edf3ef;
  border-bottom: none;
}

.ai-advanced-collapse .el-collapse-item__content {
  padding: 14px 14px 2px;
}

.collapse-title {
  color: #516358;
}

.ai-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding-top: 0;
}

.ai-actions .el-button {
  margin-left: 0 !important;
}

.avatar-uploader {
  text-align: center;
  padding-bottom: 14px;
}
.avatar-uploader .el-upload {
  border: none;
  border-radius: 12px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  box-shadow: none;
}
.avatar-uploader .el-upload:hover {
  box-shadow: none;
}
.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 138px;
  height: 138px;
  line-height: 138px;
  text-align: center;
}
.avatar {
  width: 150px;
  height: 150px;
  object-fit: cover;
  display: block;
  border-radius: 12px;
}

.uid-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.uid-value {
  font-size: 18px;
  font-weight: 700;
  color: #059669;
  letter-spacing: 2px;
}

.uid-tip {
  color: #c0c4cc;
  cursor: pointer;
  font-size: 15px;
}

.person-page .el-form-item__content {
  min-width: 0;
}

.person-page .el-input,
.person-page .el-select,
.person-page .el-textarea {
  width: 100%;
}

.person-page .el-form-item:last-child .el-form-item__content {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.person-page .el-form-item:last-child .el-button {
  margin-left: 0 !important;
}

@media (max-width: 720px) {
  .person-page {
    padding: 16px;
    grid-template-columns: 1fr;
  }

  .ai-main-grid,
  .ai-advanced-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

@media (max-width: 1200px) {
  .person-page {
    grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
    padding: 20px;
  }
}

@media (max-width: 980px) {
  .person-page {
    grid-template-columns: 1fr;
  }
}
</style>
