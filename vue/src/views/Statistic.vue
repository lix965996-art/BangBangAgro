<template>
  <div class="monitoring-center">
    <!-- 战术指挥栏 -->
    <div class="tactical-toolbar">
      <div class="toolbar-left">
        <h1 class="page-title">
          <span class="title-main">全域农田网格阵列</span>
          <span class="title-sub">Field Grid Array</span>
        </h1>
        <div class="status-strip">
          <span class="status-item">
            <span class="status-dot online"></span>
            在线终端: <strong>{{ onlineCount }}</strong>
          </span>
          <span class="status-divider">|</span>
          <span class="status-item">
            <span class="status-dot warning"></span>
            异常: <strong>{{ abnormalCount }}</strong>
          </span>
        </div>
      </div>
      <div class="toolbar-right">
        <input 
          link 
          class="search-input" 
          v-model="searchFarm" 
          placeholder="搜索地块..." 
          @keyup.enter="loadData"
        />
        <button class="tool-btn" @click="loadData">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
            <path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
          </svg>
          搜索
        </button>
        <button class="tool-btn" @click="handleAdd">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
            <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
          </svg>
          新增地块
        </button>
        <button class="tool-btn" @click="loadData">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
            <path d="M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
          </svg>
          刷新
        </button>
        <button class="tool-btn special" @click="batchIrrigate">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
            <path d="M12 2.69l5.66 5.66a8 8 0 1 1-11.31 0z"/>
          </svg>
          批量灌溉
        </button>
        <button class="tool-btn special" @click="droneInspect">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
            <path d="M14 8h-4L8 4H4v2h3l2 4h6l2-4h3V4h-4z"/>
            <circle cx="7" cy="14" r="3"/>
            <circle cx="17" cy="14" r="3"/>
          </svg>
          无人机巡检
        </button>
      </div>
    </div>

    <!-- 智能网格卡片 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>
    <div v-else class="field-grid">
      <div 
        v-for="field in fieldData" 
        :key="field.id" 
        class="field-card"
        :class="getHealthClass(field.healthScore)"
        @mouseenter="showToast(field)"
        @mouseleave="hideToast"
      >
        <!-- 卡片头部 -->
        <div class="card-header">
          <h3 class="field-name">{{ field.farm }}</h3>
          <div class="health-badge" :class="getHealthClass(field.healthScore)">
            <span class="score-value">{{ field.healthScore }}</span>
            <span class="score-label">分</span>
          </div>
        </div>
        <!-- 卡片主体 -->
        <div class="card-body">
          <!-- 左侧：作物缩略图 -->
          <div class="crop-thumbnail">
            <img :src="getCropImage(field.crop)" :alt="field.crop" />
            <span class="crop-label">{{ field.crop }}</span>
          </div>
          
          <!-- 右侧：核心指标矩阵 -->
          <div class="metrics-matrix">
            <div class="metric-row">
              <span class="metric-icon">🌡️</span>
              <div class="metric-content">
                <span class="metric-label">温度</span>
                <span class="metric-value">{{ formatTemperature(field.temperature) }}°C</span>
                <span class="metric-trend" :class="getTrendClass(field.tempTrend)">{{ field.tempTrend > 0 ? '↑' : field.tempTrend < 0 ? '↓' : '→' }}</span>
              </div>
            </div>
            <div class="metric-row">
              <span class="metric-icon">💧</span>
              <div class="metric-content">
                <span class="metric-label">土壤湿度</span>
                <span class="metric-value">{{ formatHumidity(field.soilhumidity) }}%</span>
                <div class="metric-bar">
                  <div class="bar-fill" :style="{ width: normalizeHumidity(field.soilhumidity) + '%' }"></div>
                </div>
              </div>
            </div>
            <div class="metric-row">
              <span class="metric-icon">☀️</span>
              <div class="metric-content">
                <span class="metric-label">GDD 有效积温</span>
                <span class="metric-value gdd">{{ field.gdd }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 卡片底部操作条 -->
        <div class="card-footer">
          <button class="action-icon-btn" @click="showDetail(field)" title="详情">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
            </svg>
          </button>
          <button class="action-icon-btn" @click="irrigate(field)" title="灌溉">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M12 2.69l5.66 5.66a8 8 0 1 1-11.31 0z"/>
            </svg>
          </button>
          <button class="action-icon-btn" @click="fertilize(field)" title="施肥">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M19.5 3.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2 7.5 3.5 6 2v14H3v3c0 1.66 1.34 3 3 3h12c1.66 0 3-1.34 3-3V2l-1.5 1.5zM19 19c0 .55-.45 1-1 1s-1-.45-1-1v-3H8V5h11v14z"/>
            </svg>
          </button>
          <button class="action-icon-btn" @click="viewLog(field)" title="日志">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
            </svg>
          </button>
        </div>
        
        <!-- AI 建议 Toast（悬停显示）-->
        <div class="ai-toast" v-if="activeToast === field.id">
          <span class="toast-icon">💡</span>
          <span class="toast-text">{{ field.aiAdvice }}</span>
        </div>
      </div>
    </div>

    <div style="padding: 5px 0; text-align: center; margin-top: -10px;">
      <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="pageNum"
          :page-sizes="[8, 12, 20]"
          :page-size="pageSize"
          layout="total, prev, pager, next, jumper"
          :total="total">
      </el-pagination>
    </div>

    <el-dialog title="地块信息管理" v-model="dialogFormVisible" width="40%" :close-on-click-modal="false">
      <el-form label-width="120px" size="small" style="width: 90%">
        <el-form-item label="农田名称">
           <el-input v-model="form.farm" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item label="作物名称">
          <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
            <el-tag
              :key="tag"
              v-for="tag in dynamicTags"
              closable
              :disable-transitions="false"
              @close="handleClose(tag)">
              {{tag}}
            </el-tag>
            <el-input
              class="input-new-tag"
              v-if="inputVisible"
              v-model="inputValue"
              ref="saveTagInput"
              size="small"
              style="width: 100px"
              @keyup.enter="handleInputConfirm"
              @blur="handleInputConfirm"
            >
            </el-input>
            <el-button v-else class="button-new-tag" size="small" @click="showInput">+ 添加作物</el-button>
          </div>
        </el-form-item>
        <el-form-item label="生长状态">
           <el-select v-model="form.state" placeholder="请选择">
              <el-option label="生长良好" value="生长良好"></el-option>
              <el-option label="缺水预警" value="缺水预警"></el-option>
              <el-option label="病虫害风险" value="病虫害风险"></el-option>
           </el-select>
        </el-form-item>
        <el-form-item label="农田负责人">
          <el-input v-model="form.keeper" autocomplete="off"></el-input>
        </el-form-item>
        </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogFormVisible = false">取 消</el-button>
          <el-button type="primary" @click="save">保 存</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script>
import { assetUrl } from '@/utils/assetUrl'

export default {
  name: 'Statistic',
  data() {
    return {
      fieldData: [],
      total: 0,
      pageNum: 1,
      pageSize: 8,
      searchFarm: '',
      loading: false,
      form: {},
      dialogFormVisible: false,
      activeToast: null,
      dynamicTags: [],
      inputVisible: false,
      inputValue: '',
      stm32Data: {
        temperature: 25,
        humidity: 50
      },
      stm32Timer: null
    }
  },
  computed: {
    onlineCount() {
      return this.fieldData.length
    },
    abnormalCount() {
      return this.fieldData.filter(f => f.healthScore < 80).length
    }
  },
  mounted() {
    this.loadData()
    this.stm32Timer = setInterval(() => {
      this.fetchSTM32Data()
    }, 30000)
  },
  beforeUnmount() {
    if (this.stm32Timer) {
      clearInterval(this.stm32Timer)
    }
  },
  methods: {
    // 农学算法：计算健康分
    calculateHealthScore(temp, humidity) {
      let score = 100
      
      if (temp < 15 || temp > 35) score -= 30
      else if (temp < 18 || temp > 32) score -= 15
      else if (temp < 20 || temp > 28) score -= 5
      
      if (humidity < 30 || humidity > 80) score -= 30
      else if (humidity < 40 || humidity > 75) score -= 15
      else if (humidity < 50 || humidity > 70) score -= 5
      
      return Math.max(0, Math.min(100, score))
    },
    
    // 计算 GDD
    calculateGDD(temp) {
      const baseTemp = 10
      const dailyGDD = Math.max(0, temp - baseTemp)
      return Math.round(dailyGDD * 30)
    },
    
    // 生成 Mock 数据
    generateMockData() {
      const crops = ['草莓', '玉米', '小麦', '番茄', '黄瓜', '西瓜', '水稻', '葡萄']
      const keepers = ['张三', '李四', '王五', '赵六', '孙七', '周八']
      const states = ['生长良好', '缺水预警', '病虫害风险', '监测中']
      
      return Array.from({ length: 10 }, (_, i) => {
        const temp = 20 + Math.random() * 15
        const humidity = 40 + Math.random() * 40
        const healthScore = this.calculateHealthScore(temp, humidity)
        
        return {
          id: i + 1,
          farm: `${i + 1}号田-${['有机实验区', '高产示范区', '智能温室', '露天种植区'][i % 4]}`,
          crop: crops[i % crops.length],
          keeper: keepers[i % keepers.length],
          temperature: Math.round(temp * 10) / 10,
          soilhumidity: Math.round(humidity),
          airhumidity: Math.round(50 + Math.random() * 30),
          carbon: Math.round(400 + Math.random() * 200),
          light: `${Math.round(20000 + Math.random() * 30000)} Lux`,
          state: states[healthScore > 90 ? 0 : healthScore > 70 ? 3 : healthScore > 50 ? 1 : 2],
          pump: Math.random() > 0.5 ? '开启' : '关闭',
          filllight: Math.random() > 0.5 ? '开启' : '关闭',
          healthScore,
          gdd: this.calculateGDD(temp),
          tempTrend: Math.random() > 0.5 ? 1 : Math.random() > 0.5 ? -1 : 0,
          aiAdvice: healthScore > 90 ? 'AI建议：适宜追肥' : healthScore > 70 ? 'AI建议：保持当前管理' : 'AI建议：需加强监测'
        }
      })
    },
    
    // 加载数据（真实 API）
    async loadData() {
      this.loading = true
      
      await this.fetchSTM32Data()
      
      this.request.get('/statistic/page', {
        params: {
          pageNum: this.pageNum,
          pageSize: this.pageSize,
          farm: this.searchFarm
        }
      }).then(res => {
        this.loading = false

        const ok = res && (res.code === '200' || res.code === 200)
        const page = res && res.data
        const records = page && Array.isArray(page.records) ? page.records : null

        // 注意：[] 在 JS 中为真值，原先 if (res.data.records) 会在「有接口但 0 条」时不走降级，页面会一直空白
        if (ok && records && records.length > 0) {
          // 为每条真实数据计算健康分和 GDD
          this.fieldData = records.map(item => {
            // 如果温度或湿度为空，使用 STM32 数据
            const temp = item.temperature || this.stm32Data.temperature || 25
            const humidity = item.soilhumidity || this.stm32Data.humidity || 50
            
            return {
              ...item,
              temperature: temp,
              soilhumidity: humidity,
              // 计算健康分
              healthScore: this.calculateHealthScore(temp, humidity),
              // 计算 GDD
              gdd: this.calculateGDD(temp),
              // 随机趋势（真实环境可从历史数据对比得出）
              tempTrend: Math.random() > 0.5 ? 1 : Math.random() > 0.5 ? -1 : 0,
              // AI 建议
              aiAdvice: this.generateAIAdvice(temp, humidity, item.state)
            }
          })
          this.total = page.total || records.length
        } else {
          if (ok && records && records.length === 0) {
            console.warn(
              '农田列表为 0 条：若已登录非管理员，后端会按 statistic.keeper=当前用户名 过滤；' +
                '库里 keeper 为空或与登录名不一致时会查不到数据。以下为演示用 Mock 降级。'
            )
          } else {
            console.warn('后端返回异常或结构不符，使用 Mock 数据演示')
          }
          this.loadMockData()
        }
      }).catch(err => {
        console.error('API 调用失败，使用 Mock 数据降级', err)
        this.loadMockData()
      })
    },
    
    // Mock 数据降级方案
    loadMockData() {
      let mockData = this.generateMockData()
      
      if (this.searchFarm) {
        mockData = mockData.filter(f => f.farm.includes(this.searchFarm))
      }
      
      const start = (this.pageNum - 1) * this.pageSize
      const end = start + this.pageSize
      this.fieldData = mockData.slice(start, end)
      this.total = mockData.length
      
      this.loading = false
    },
    
    // 生成 AI 建议
    generateAIAdvice(temp, humidity, state) {
      if (humidity < 30) return `监测到土壤湿度严重不足，建议立即开启水泵灌溉 2 小时`
      if (state && state.includes('虫')) return `系统图像识别发现疑似叶斑病，建议无人机喷洒杀菌剂`
      if (temp > 35) return `当前气温过高，建议开启遮阳网和自动喷淋降温`
      const score = this.calculateHealthScore(temp, humidity)
      if (score > 90) return 'AI建议：适宜追肥'
      if (score > 70) return 'AI建议：保持当前管理'
      return 'AI建议：需加强监测'
    },
    
    // 工具函数
    getHealthClass(score) {
      if (score >= 90) return 'health-excellent'
      if (score >= 70) return 'health-good'
      if (score >= 50) return 'health-warning'
      return 'health-danger'
    },
    
    getTrendClass(trend) {
      if (trend > 0) return 'trend-up'
      if (trend < 0) return 'trend-down'
      return 'trend-stable'
    },
    
    toFiniteNumber(value, fallback = 0) {
      const n = Number(value)
      return Number.isFinite(n) ? n : fallback
    },
    
    formatTemperature(value) {
      return this.toFiniteNumber(value, 0).toFixed(1)
    },
    
    formatHumidity(value) {
      return Math.round(this.toFiniteNumber(value, 0))
    },
    
    normalizeHumidity(value) {
      const humidity = this.formatHumidity(value)
      return Math.max(0, Math.min(100, humidity))
    },
    
    getCropImage(crop) {
      const imageMap = {
        '草莓': assetUrl('moreng.png'),
        '玉米': assetUrl('yumi.png'),
        '小麦': assetUrl('xiaomai.png'),
        '番茄': assetUrl('xihongshi.png'),
        '黄瓜': assetUrl('aiye.png'),
        '西瓜': assetUrl('moreng.png'),
        '水稻': assetUrl('yumi.png'),
        '葡萄': assetUrl('moreng.png')
      }
      return imageMap[crop] || assetUrl('moreng.png')
    },
    
    // 交互函数
    showToast(field) {
      this.activeToast = field.id
    },
    
    hideToast() {
      this.activeToast = null
    },
    
    showDetail(field) {
      this.$message.info(`正在加载 ${field.farm} 详细数据...`)
    },
    
    irrigate(field) {
      this.$message.success(`指令已下发：正在对 [${field.farm}] 执行灌溉操作`)
    },
    
    fertilize(field) {
      this.$message.success(`指令已下发：正在对 [${field.farm}] 执行施肥操作`)
    },
    
    viewLog(field) {
      this.$message.info(`正在加载 ${field.farm} 操作日志...`)
    },
    
    batchIrrigate() {
      this.$message.success('批量灌溉指令已下发至所有在线地块')
    },
    
    droneInspect() {
      this.$message.success('无人机巡检任务已启动，预计 30 分钟完成')
    },
    
    // CRUD
    handleAdd() {
      this.dialogFormVisible = true
      this.form = {}
      this.dynamicTags = []
      this.inputVisible = false
      this.inputValue = ''
    },

    showInput() {
      this.inputVisible = true
      this.inputValue = ''
      this.$nextTick(() => {
        const ref = this.$refs.saveTagInput
        if (ref && typeof ref.focus === 'function') {
          ref.focus()
        }
      })
    },

    handleClose(tag) {
      this.dynamicTags = this.dynamicTags.filter((t) => t !== tag)
    },

    handleInputConfirm() {
      const v = (this.inputValue || '').trim()
      if (v && !this.dynamicTags.includes(v)) {
        this.dynamicTags.push(v)
      }
      this.inputVisible = false
      this.inputValue = ''
    },

    save() {
      this.form.crop = (this.dynamicTags || []).join(',')
      if (!this.form.farm || !String(this.form.farm).trim()) {
        this.$message.warning('请填写农田名称')
        return
      }
      if (!this.form.crop) {
        this.$message.warning('请至少添加一种作物')
        return
      }
      this.request
        .post('/statistic', this.form)
        .then((res) => {
          if (res.code === '200' || res.code === 200) {
            this.$message.success('保存成功')
            this.dialogFormVisible = false
            this.loadData()
          } else {
            this.$message.error(res.msg || '保存失败')
          }
        })
        .catch((err) => {
          this.$message.error('提交失败：' + (err.message || '网络异常'))
        })
    },
    
    handleSizeChange(size) {
      this.pageSize = size
      this.loadData()
    },
    
    handleCurrentChange(page) {
      this.pageNum = page
      this.loadData()
    },
    
    // STM32
    async fetchSTM32Data() {
      try {
        this.stm32Data = {
          temperature: 20 + Math.random() * 10,
          humidity: 50 + Math.random() * 20
        }
      } catch (e) {
        console.warn('STM32 数据获取失败', e)
      }
    }
  }
}
</script>

<style scoped>
/* ========== Modern Eco-Tech 风格 - 农田监测中心 ========== */

.monitoring-center {
  min-height: 100vh;
  background: #f1f5f9;
  padding: 16px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', Arial, sans-serif;
}

/* ===== 战术指挥栏 ===== */
.tactical-toolbar {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 14px 18px;
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.page-title {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-main {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.3px;
}

.title-sub {
  font-size: 11px;
  font-weight: 500;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.status-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-item strong {
  font-family: 'Roboto Mono', 'Courier New', monospace;
  color: #111827;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.status-dot.online {
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.4);
  animation: pulse-online 2s infinite;
}

.status-dot.warning {
  background: #f59e0b;
}

@keyframes pulse-online {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.status-divider {
  color: #d1d5db;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-input {
  width: 180px;
  padding: 6px 12px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: #10b981;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
  transition: all 0.15s;
}

.tool-btn:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}

.tool-btn.special {
  background: #ecfdf5;
  border-color: #10b981;
  color: #047857;
}

.tool-btn.special:hover {
  background: #d1fae5;
}

/* ===== 加载状态 ===== */
.loading-overlay {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: #6b7280;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e5e7eb;
  border-top-color: #10b981;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 智能网格卡片 ===== */
.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.field-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 14px;
  transition: all 0.2s;
  position: relative;
  overflow: visible;
}

.field-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

/* 健康分边框颜色 */
.field-card.health-excellent {
  border-left: 3px solid #10b981;
}

.field-card.health-good {
  border-left: 3px solid #3b82f6;
}

.field-card.health-warning {
  border-left: 3px solid #f59e0b;
}

.field-card.health-danger {
  border-left: 3px solid #dc2626;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f3f4f6;
}

.field-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.health-badge {
  display: flex;
  align-items: baseline;
  gap: 2px;
  padding: 4px 10px;
  border-radius: 4px;
  font-weight: 700;
}

.health-badge.health-excellent {
  background: #ecfdf5;
  color: #047857;
}

.health-badge.health-good {
  background: #eff6ff;
  color: #1e40af;
}

.health-badge.health-warning {
  background: #fef3c7;
  color: #b45309;
}

.health-badge.health-danger {
  background: #fef2f2;
  color: #991b1b;
}

.score-value {
  font-size: 16px;
  font-family: 'Roboto Mono', 'Courier New', monospace;
}

.score-label {
  font-size: 11px;
  font-weight: 500;
}

/* 卡片主体 */
.card-body {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

/* 作物缩略图 */
.crop-thumbnail {
  width: 80px;
  height: 80px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px;
  flex-shrink: 0;
}

.crop-thumbnail img {
  width: 48px;
  height: 48px;
  object-fit: contain;
}

.crop-label {
  font-size: 11px;
  color: #6b7280;
  margin-top: 4px;
  font-weight: 500;
}

/* 指标矩阵 */
.metrics-matrix {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-icon {
  font-size: 16px;
  width: 20px;
  text-align: center;
}

.metric-content {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-label {
  font-size: 11px;
  color: #6b7280;
  min-width: 60px;
}

.metric-value {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  font-family: 'Roboto Mono', 'Courier New', monospace;
  min-width: 50px;
}

.metric-value.gdd {
  color: #f59e0b;
}

.metric-trend {
  font-size: 14px;
  font-weight: 700;
}

.metric-trend.trend-up {
  color: #dc2626;
}

.metric-trend.trend-down {
  color: #10b981;
}

.metric-trend.trend-stable {
  color: #6b7280;
}

/* 湿度进度条 */
.metric-bar {
  flex: 1;
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6 0%, #10b981 100%);
  transition: width 0.3s;
}

/* 卡片底部操作条 */
.card-footer {
  display: flex;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid #f3f4f6;
}

.action-icon-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  color: #6b7280;
}

.action-icon-btn:hover {
  background: #f3f4f6;
  color: #111827;
  border-color: #d1d5db;
}

/* AI Toast（悬停显示）*/
.ai-toast {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  background: #1e293b;
  color: #ffffff;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 12px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 10;
  animation: slideDown 0.2s;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.toast-icon {
  font-size: 16px;
}

.toast-text {
  flex: 1;
  line-height: 1.4;
}
</style>
