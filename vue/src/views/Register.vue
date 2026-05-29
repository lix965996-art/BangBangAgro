<template>
  <div class="register-page">
    <FragmentBackground />

    <!-- 右侧白色背景容器 -->
    <div class="right-panel">
      <!-- 注册卡片 -->
      <div class="register-box">
        <div class="register-card">
          <div class="register-header">
            <div class="register-title-text">注册</div>
            <div class="logo-container">
              <div class="logo-icon-wrapper">
                <img class="logo-icon" :src="$assetUrl('Logo.jpg')" />
              </div>
              <div class="logo-text">BangBang Agro</div>
            </div>
          </div>
          
          <el-form :model="user" :rules="rules" ref="userForm" class="form-container">
            <el-form-item prop="username" class="form-group">
              <div class="input-wrapper">
                <i class="el-icon-user input-icon"></i>
                <el-input 
                  class="form-input" 
                  v-model="user.username"
                  placeholder="请输入用户名"
                  autocomplete="off"
                ></el-input>
              </div>
            </el-form-item>
            
            <el-form-item prop="password" class="form-group">
              <div class="input-wrapper">
                <i class="el-icon-lock input-icon"></i>
                <el-input 
                  class="form-input" 
                  type="password"
                  v-model="user.password"
                  placeholder="请输入密码（至少6位）"
                  show-password
                  autocomplete="off"
                ></el-input>
              </div>
            </el-form-item>
            
            <el-form-item prop="confirmPassword" class="form-group">
              <div class="input-wrapper">
                <i class="el-icon-lock input-icon"></i>
                <el-input 
                  class="form-input" 
                  type="password"
                  v-model="user.confirmPassword"
                  placeholder="请再次确认密码"
                  show-password
                  autocomplete="off"
                ></el-input>
              </div>
            </el-form-item>
            
            <el-form-item class="form-group">
              <el-button 
                class="register-button" 
                type="primary"
                :loading="registering"
                @click="register"
              >
                {{ registering ? '注册中...' : '注 册' }}
              </el-button>
            </el-form-item>
          
            <div class="login-link">
              <span>已有账号？</span>
              <span class="link-text" @click="$router.push('/login')">立即登录</span>
            </div>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import FragmentBackground from "@/components/FragmentBackground.vue";

export default {
  name: "Register",
  components: {FragmentBackground},
  data() {
    return {
      user: {},
      registering: false,
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请确认密码', trigger: 'blur' },
          { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
        ],
      },
    }
  },
  mounted() {
  },
  methods: {
    register() {
      this.$refs['userForm'].validate((valid) => {
        if (valid) {
          if (this.user.password !== this.user.confirmPassword) {
            this.$message.error("两次输入的密码不一致")
            return false
          }
          if (this.user.password.length < 6) {
            this.$message.error("密码长度至少6位")
            return false
          }
          
          this.registering = true;
          this.request.post("/user/register", this.user).then(res => {
            if(res.code === '200') {
              this.$message.success("注册成功")
              setTimeout(() => {
                this.$router.push('/login')
              }, 1500)
            } else {
              this.$message.error(res.msg)
            }
          }).finally(() => {
            this.registering = false;
          })
        }
      });
    }
  }
}
</script>

<style scoped>
.register-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: transparent;
}

/* 右半部分白色背景容器 */
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

.register-box {
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

.register-card {
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

.register-header {
  text-align: center;
  margin-bottom: 50px;
}

.register-title-text {
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
  background: linear-gradient(135deg, 
    rgba(34, 197, 94, 0.15),
    rgba(16, 185, 129, 0.15)
  );
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

.form-input :deep(.el-input__inner ){
  border: none !important;
  background: transparent !important;
  padding: 20px 0;
  font-size: 14px;
  color: #1f2937;
  box-shadow: none !important;
}

.form-input :deep(.el-input__inner::placeholder ){
  color: #9ca3af;
}

.form-input :deep(.el-input__inner:focus ){
  border: none !important;
  box-shadow: none !important;
}

.register-button {
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
  letter-spacing: 1px;
}

.register-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(34, 197, 94, 0.4);
}

.register-button:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.4);
}

.login-link {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
  margin-top: 8px;
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

/* Element UI 样式覆盖 */
.form-container :deep(.el-form-item__error ){
  padding-top: 4px;
}

.form-container :deep(.el-form-item ){
  margin-bottom: 0;
}
</style>
