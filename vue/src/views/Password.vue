<template>
  <div class="password-page">
    <section class="password-panel form-panel">
      <div class="panel-title">
        <div>
          <h2>修改登录密码</h2>
          <p>建议定期更新密码，避免与其他系统重复使用。</p>
        </div>
      </div>

      <el-form label-width="104px" size="default" :model="form" :rules="rules" ref="pass">
        <el-form-item label="原密码" prop="password">
          <el-input v-model="form.password" autocomplete="off" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" autocomplete="off" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" autocomplete="off" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <el-form-item class="actions-row">
          <el-button type="primary" :loading="saving" @click="save">{{ saving ? '修改中...' : '确认修改' }}</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="password-panel guide-panel">
      <h3>安全提示</h3>
      <div class="tip-list">
        <div class="tip-item">
          <span class="tip-dot"></span>
          <span>密码长度不少于 3 位，建议使用字母、数字和符号组合。</span>
        </div>
        <div class="tip-item">
          <span class="tip-dot"></span>
          <span>修改成功后系统会退出登录，需要重新登录验证。</span>
        </div>
        <div class="tip-item">
          <span class="tip-dot"></span>
          <span>如果忘记原密码，请联系系统管理员重置账号。</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { useAppStore } from '@/store'
import { getStoredUserRaw } from '@/utils/authStorage'

export default {
  name: "Password",
  data() {
    return {
      saving: false,
      form: {},
      user: (() => {
        try {
          const s = getStoredUserRaw()
          return s ? JSON.parse(s) : {}
        } catch (e) {
          return {}
        }
      })(),
      rules: {
        password: [
          { required: true, message: '请输入原密码', trigger: 'blur' },
          { min: 3, message: '长度不少于 3 位', trigger: 'blur' }
        ],
        newPassword: [
          { required: true, message: '请输入新密码', trigger: 'blur' },
          { min: 3, message: '长度不少于 3 位', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请确认新密码', trigger: 'blur' },
          { min: 3, message: '长度不少于 3 位', trigger: 'blur' }
        ],
      }
    }
  },
  created() {
    this.appStore = useAppStore()
    this.form.username = this.user.username
  },
  methods: {
    save() {
      this.$refs.pass.validate((valid) => {
        if (valid) {
          if (this.form.newPassword !== this.form.confirmPassword) {
            this.$message.error("两次输入的新密码不相同")
            return false
          }
          this.saving = true;
          this.request.post("/user/password", this.form).then(res => {
            if (res.code === '200' || res.code === 200) {
              this.$message.success("修改成功")
              this.appStore.logout()
            } else {
              this.$message.error(res.msg || '修改失败，请稍后重试')
            }
          }).catch(() => {
            this.$message.error('网络错误，请检查网络连接后重试')
          }).finally(() => {
            this.saving = false;
          })
        }
      })
    },
  }
}
</script>

<style scoped>
.password-page {
  width: 100%;
  padding: 24px 32px;
  display: grid;
  grid-template-columns: minmax(520px, 0.95fr) minmax(360px, 1.05fr);
  gap: 24px;
  align-items: stretch;
}

.password-panel {
  background: #fff;
  border: 1px solid rgba(76, 175, 80, 0.22);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.form-panel {
  padding: 28px 32px 24px;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 28px;
}

.panel-title h2 {
  margin: 0;
  font-size: 22px;
  color: #1f2937;
}

.panel-title p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.guide-panel {
  padding: 28px 32px;
  background: linear-gradient(135deg, #f7fbf5 0%, #ffffff 70%);
}

.guide-panel h3 {
  margin: 0 0 18px;
  font-size: 18px;
  color: #245b36;
}

.tip-list {
  display: grid;
  gap: 14px;
}

.tip-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: #475569;
  font-size: 14px;
  line-height: 1.7;
}

.tip-dot {
  width: 8px;
  height: 8px;
  margin-top: 8px;
  border-radius: 999px;
  background: #4caf50;
  flex-shrink: 0;
}

.actions-row :deep(.el-form-item__content) {
  display: flex;
  gap: 12px;
}

@media (max-width: 960px) {
  .password-page {
    grid-template-columns: 1fr;
    padding: 16px;
  }
}
</style>
