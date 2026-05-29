<template>
  <div v-if="visible" class="global-loading-overlay">
    <div class="loading-container">
      <div class="loading-spinner">
        <div class="spinner-circle"></div>
        <div class="spinner-circle"></div>
        <div class="spinner-circle"></div>
      </div>
      <div class="loading-text">{{ text || '加载中...' }}</div>
      <div class="loading-progress" v-if="showProgress">
        <el-progress
          :percentage="progress"
          :status="progressStatus"
          :stroke-width="4"
          :show-text="false"
        />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GlobalLoading',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    text: {
      type: String,
      default: '加载中...'
    },
    showProgress: {
      type: Boolean,
      default: false
    },
    progress: {
      type: Number,
      default: 0,
      validator: value => value >= 0 && value <= 100
    },
    progressStatus: {
      type: String,
      default: ''
    }
  }
}
</script>

<style scoped>
.global-loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999999;
  backdrop-filter: blur(2px);
}

.loading-container {
  text-align: center;
  padding: 40px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.loading-spinner {
  width: 60px;
  height: 60px;
  position: relative;
  margin: 0 auto 20px;
}

.spinner-circle {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border: 3px solid transparent;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.spinner-circle:nth-child(1) {
  animation-delay: -0.16s;
  border-top-color: #409eff;
}

.spinner-circle:nth-child(2) {
  animation-delay: -0.08s;
  border-top-color: #67c23a;
}

.spinner-circle:nth-child(3) {
  animation-delay: 0s;
  border-top-color: #f56c6c;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: rotate(360deg);
    opacity: 0.6;
  }
}

.loading-text {
  color: #fff;
  font-size: 16px;
  margin-bottom: 15px;
  font-weight: 500;
}

.loading-progress {
  width: 200px;
  margin: 0 auto;
}
</style>