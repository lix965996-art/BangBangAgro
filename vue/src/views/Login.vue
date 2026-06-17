<template>
  <div class="login-page">
    <FragmentBackground />

    <div class="right-panel">
      <div class="login-box">
        <div class="login-card">
          <div class="login-header">
            <div class="login-title-text">登录</div>
            <div class="logo-container">
              <div class="logo-icon-wrapper">
                <img class="logo-icon" :src="$assetUrl('Logo.jpg')" alt="BangBang Agro" />
              </div>
              <div class="logo-text">BangBang Agro</div>
            </div>
          </div>

          <el-form
            :model="user"
            :rules="rules"
            ref="userForm"
            class="form-container"
            @submit.prevent
          >
            <el-form-item prop="username" class="form-group">
              <div class="input-wrapper">
                <i class="el-icon-user input-icon"></i>
                <el-input
                  class="form-input"
                  v-model.trim="user.username"
                  placeholder="请输入用户名"
                  autocomplete="username"
                  @keyup.enter="login"
                />
              </div>
            </el-form-item>

            <el-form-item prop="password" class="form-group">
              <div class="input-wrapper">
                <i class="el-icon-lock input-icon"></i>
                <el-input
                  class="form-input"
                  type="password"
                  v-model="user.password"
                  placeholder="请输入密码"
                  show-password
                  autocomplete="current-password"
                  @keyup.enter="login"
                />
              </div>
            </el-form-item>

            <el-form-item class="form-group">
              <el-button
                class="login-button"
                type="primary"
                :loading="logging"
              @click.prevent="login"
              >
                {{ logging ? '登录中...' : '登录' }}
              </el-button>
            </el-form-item>

            <div class="action-links">
              <el-button link class="link-text" @click="handlePass">找回密码</el-button>
              <div class="register-link">
                <span>还没有账号？</span>
                <span class="link-text" @click="$router.push('/register')">立即注册</span>
              </div>
            </div>
          </el-form>
        </div>
      </div>
    </div>

    <el-dialog
      title="找回密码"
      v-model="dialogFormVisible"
      width="520px"
      class="password-dialog"
    >
      <!-- 步骤1：输入用户名 -->
      <el-form v-if="resetStep === 1" class="password-form" label-width="92px" size="default">
        <el-form-item label="用户名">
          <el-input v-model.trim="pass.username" autocomplete="off" placeholder="请输入注册时的用户名" />
        </el-form-item>
      </el-form>
      <!-- 步骤2：回答安全问题 -->
      <el-form v-if="resetStep === 2" class="password-form" label-width="92px" size="default">
        <el-form-item label="安全问题">
          <div class="security-question-text">{{ securityQuestion }}</div>
        </el-form-item>
        <el-form-item label="你的答案">
          <el-input v-model.trim="pass.answer" autocomplete="off" placeholder="请输入答案" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model.trim="pass.newPassword" type="password" show-password autocomplete="off" placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model.trim="pass.confirmPassword" type="password" show-password autocomplete="off" placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogFormVisible = false">取消</el-button>
          <el-button v-if="resetStep === 1" type="primary" :loading="resetLoading" @click="fetchSecurityQuestion">
            下一步
          </el-button>
          <el-button v-if="resetStep === 2" type="primary" :loading="resetLoading" @click="resetByQuestion">
            重置密码
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { resetRouter, setRoutes } from "@/router";
import { setStoredAuth } from "@/utils/authStorage";
import FragmentBackground from "@/components/FragmentBackground.vue";

export default {
  name: "Login",
  components: { FragmentBackground },
  data() {
    return {
      user: {
        username: '',
        password: ''
      },
      pass: {},
      code: '',
      dialogFormVisible: false,
      logging: false,
      resetLoading: false,
      resetStep: 1,
      securityQuestion: '',
      identifyCode: '',
      identifyCodes: '3456789ABCDEFGHGKMNPQRSTUVWXY',
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 1, max: 20, message: '长度在 1 到 20 个字符', trigger: 'blur' }
        ]
      }
    }
  },
  mounted() {
    resetRouter()
    this.refreshCode()
  },
  methods: {
    login() {
      this.$refs['userForm'].validate((valid) => {
        if (!valid || this.logging) return

        this.logging = true
        this.request.post("/user/login", this.user).then(res => {
          if (res.code === '200' || res.code === 200) {
            setStoredAuth(res.data, res.data.menus)
            setRoutes()
            this.$router.push("/")
            this.$message.success("登录成功")
          } else {
            this.$message.error(res.msg || res.message || "用户名或密码错误")
          }
        }).catch(error => {
          this.$message.error(error.message || "登录失败，请检查后端服务")
        }).finally(() => {
          this.logging = false
        })
      })
    },
    refreshCode() {
      this.identifyCode = ''
      this.makeCode(this.identifyCodes, 4)
    },
    makeCode(o, l) {
      for (let i = 0; i < l; i++) {
        this.identifyCode += o[Math.floor(Math.random() * o.length)]
      }
    },
    handlePass() {
      this.dialogFormVisible = true
      this.pass = {}
      this.resetStep = 1
      this.securityQuestion = ''
    },
    fetchSecurityQuestion() {
      if (!this.pass.username) {
        this.$message.warning("请输入用户名")
        return
      }
      this.resetLoading = true
      this.request.get("/user/security-question", { params: { username: this.pass.username } }).then(res => {
        if (res.code === '200' || res.code === 200) {
          this.securityQuestion = res.data.question
          this.resetStep = 2
        } else {
          this.$message.error(res.msg || res.message || "获取安全问题失败")
        }
      }).catch(() => {
        this.$message.error("网络异常，请稍后重试")
      }).finally(() => {
        this.resetLoading = false
      })
    },
    resetByQuestion() {
      if (!this.pass.answer || !this.pass.answer.trim()) {
        this.$message.warning("请输入答案")
        return
      }
      if (!this.pass.newPassword || this.pass.newPassword.length < 6) {
        this.$message.warning("新密码长度至少6位")
        return
      }
      if (this.pass.newPassword !== this.pass.confirmPassword) {
        this.$message.warning("两次输入的密码不一致")
        return
      }
      this.resetLoading = true
      this.request.post("/user/reset-by-question", {
        username: this.pass.username,
        answer: this.pass.answer,
        newPassword: this.pass.newPassword
      }).then(res => {
        if (res.code === '200' || res.code === 200) {
          this.$message.success("密码重置成功，请使用新密码登录")
          this.dialogFormVisible = false
        } else {
          this.$message.error(res.msg || res.message || "重置失败")
        }
      }).catch(() => {
        this.$message.error("网络异常，请稍后重试")
      }).finally(() => {
        this.resetLoading = false
      })
    }
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: transparent;
}

.right-panel {
  position: absolute;
  right: 0;
  top: 0;
  width: 45%;
  height: 100%;
  z-index: 2;
  overflow: hidden;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.login-box {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 380px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 0 auto;
}

.login-card {
  width: 100%;
  background: #ffffff;
  border-radius: 24px;
  padding: 40px 30px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.2);
  position: relative;
  overflow: hidden;
}

.login-header {
  text-align: center;
  margin-bottom: 50px;
}

.login-title-text {
  display: block;
  font-size: 28px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 30px;
  text-align: left;
}

.logo-container {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  margin-bottom: 0;
}

.logo-icon-wrapper {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.15), rgba(16, 185, 129, 0.15));
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(34, 197, 94, 0.2);
  overflow: hidden;
}

.logo-icon {
  width: 60px;
  height: 60px;
  object-fit: cover;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #22c55e;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.form-container {
  display: flex;
  flex-direction: column;
  gap: 28px;
  width: 100%;
}

.form-group {
  width: 100%;
  margin-bottom: 0 !important;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  background: #f9fafb;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  padding: 0 20px;
  transition: all 0.3s;
}

.input-wrapper:focus-within {
  border-color: #22c55e;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.1);
}

.input-icon {
  font-size: 18px;
  margin-right: 14px;
  color: #9ca3af;
}

.form-input {
  flex: 1;
}

.form-input :deep(.el-input__wrapper) {
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  padding: 0;
}

.form-input :deep(.el-input__inner) {
  border: none !important;
  background: transparent !important;
  height: 54px;
  font-size: 14px;
  color: #1f2937;
  box-shadow: none !important;
}

.form-input :deep(.el-input__inner::placeholder) {
  color: #9ca3af;
}

:deep(.el-dialog .el-input__wrapper) {
  border: 1px solid #dcdfe6 !important;
  border-radius: 6px !important;
  box-shadow: none !important;
}

:deep(.el-dialog .el-input__wrapper.is-focus) {
  border-color: #22c55e !important;
  box-shadow: none !important;
}

:deep(.el-dialog .el-input__inner) {
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  background: transparent !important;
}

:deep(.el-dialog .el-input__inner:focus) {
  border: none !important;
  box-shadow: none !important;
}

:deep(.password-dialog.el-dialog) {
  width: min(520px, calc(100vw - 32px)) !important;
  border: 3px solid #22c55e;
  border-radius: 14px;
  overflow: hidden;
  background: rgba(248, 252, 248, 0.96);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.22);
}

:deep(.password-dialog .el-dialog__header) {
  display: flex;
  align-items: center;
  height: 58px;
  padding: 0 22px;
  margin: 0;
  background: rgba(220, 249, 224, 0.92);
  border-bottom: 1px solid rgba(34, 197, 94, 0.2);
}

:deep(.password-dialog .el-dialog__title) {
  color: #14532d;
  font-size: 21px;
  font-weight: 700;
  line-height: 1;
}

:deep(.password-dialog .el-dialog__headerbtn) {
  top: 0;
  right: 18px;
  width: 40px;
  height: 58px;
}

:deep(.password-dialog .el-dialog__body) {
  padding: 22px 22px 18px;
}

:deep(.password-dialog .el-dialog__footer) {
  padding: 4px 22px 22px;
}

.password-form {
  width: 100%;
}

.password-form :deep(.el-form-item) {
  align-items: center;
  margin-bottom: 22px;
}

.password-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.password-form :deep(.el-form-item__label) {
  height: 40px;
  line-height: 40px;
  padding-right: 16px;
  color: #14532d;
  font-size: 15px;
  font-weight: 500;
}

.password-form :deep(.el-form-item__content) {
  min-width: 0;
}

:deep(.password-dialog .el-input__wrapper) {
  width: 100%;
  height: 40px;
  border: 1px solid #d7dde5 !important;
  border-radius: 7px !important;
  background: #ffffff !important;
  padding: 0 14px !important;
}

:deep(.password-dialog .el-input__wrapper.is-focus) {
  border-color: #22c55e !important;
  box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.12) !important;
}

:deep(.password-dialog .el-input__inner) {
  height: 38px;
  line-height: 38px;
  font-size: 15px;
}

.security-question-text {
  display: flex;
  align-items: center;
  min-height: 40px;
  color: #1f2937;
  font-size: 15px;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.dialog-footer :deep(.el-button) {
  min-width: 72px;
  height: 34px;
  margin-left: 0;
  border-radius: 6px;
  font-weight: 600;
}

.login-button {
  width: 100%;
  padding: 16px;
  background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.3);
  height: auto;
}

.login-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(34, 197, 94, 0.4);
}

.login-button:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.4);
}

.action-links {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.register-link {
  text-align: right;
  font-size: 13px;
  color: #6b7280;
}

.link-text {
  color: #22c55e !important;
  font-weight: 600;
  cursor: pointer;
  font-size: 13px;
}

.link-text:hover {
  opacity: 0.8;
  text-decoration: underline;
}

.form-container :deep(.el-form-item__error) {
  padding-top: 4px;
}

.form-container :deep(.el-form-item) {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .right-panel {
    width: 100%;
    background: rgba(255, 255, 255, 0.92);
  }

  .login-card {
    border-radius: 18px;
  }
}
</style>
