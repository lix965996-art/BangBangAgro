<template>
  <div class="dashboard-page">

    <!-- 顶部标题栏 -->
    <div class="dashboard-header">
      <div class="header-title">
        <i class="el-icon-monitor dashboard-icon"></i>
        <span>无人农场总控仪表盘</span>
        <el-tag :type="patrolEnabled ? 'success' : 'info'" size="small" class="status-tag">
          {{ patrolEnabled ? '运行中' : '已暂停' }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button size="small" icon="el-icon-refresh" @click="loadAll" :loading="loading">刷新数据</el-button>
      </div>
    </div>

    <!-- 系统健康状态卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon green"><i class="el-icon-success"></i></div>
          <div class="stat-body">
            <div class="stat-label">Agent 决策链</div>
            <div class="stat-value">{{ stats.totalChains }}</div>
            <div class="stat-sub">最近24h</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon blue"><i class="el-icon-s-order"></i></div>
          <div class="stat-body">
            <div class="stat-label">待审批任务</div>
            <div class="stat-value">{{ stats.pendingTasks }}</div>
            <div class="stat-sub">需人工确认</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon orange"><i class="el-icon-warning-outline"></i></div>
          <div class="stat-body">
            <div class="stat-label">未处理事件</div>
            <div class="stat-value">{{ stats.unhandledEvents }}</div>
            <div class="stat-sub">传感器异常</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon purple"><i class="el-icon-s-marketing"></i></div>
          <div class="stat-body">
            <div class="stat-label">自动执行任务</div>
            <div class="stat-value">{{ stats.autoTasks }}</div>
            <div class="stat-sub">低风险自动处理</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 主内容区：左右两栏 -->
    <el-row :gutter="16" class="main-row">
      <!-- 左栏：待审批队列 + 传感器事件 -->
      <el-col :span="12">
        <!-- 待审批队列 -->
        <div class="panel">
          <div class="panel-header">
            <span><i class="el-icon-s-claim"></i> 待审批队列</span>
            <el-tag size="small" type="danger" v-if="approvalTasks.length">{{ approvalTasks.length }}</el-tag>
          </div>
          <div class="panel-body">
            <div v-if="approvalTasks.length === 0" class="empty-state">
              <i class="el-icon-circle-check"></i>
              <p>暂无待审批任务</p>
            </div>
            <div v-for="task in approvalTasks" :key="task.id" class="task-item">
              <div class="task-header">
                <el-tag :type="riskTagType(task.riskLevel)" size="small">{{ task.riskLevel }}</el-tag>
                <span class="task-type">{{ taskTypeLabel(task.taskType) }}</span>
                <span class="task-farm">{{ task.farmName || '-' }}</span>
              </div>
              <div class="task-reasoning">{{ task.reasoning || '无推理说明' }}</div>
              <div class="task-actions">
                <el-button type="success" size="small" @click="approveTask(task.taskId)">批准</el-button>
                <el-button type="danger" size="small" @click="rejectTask(task.taskId)">拒绝</el-button>
                <el-button v-if="task.chainId" type="text" size="small" @click="viewChain(task.chainId)">查看推理</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 传感器事件 -->
        <div class="panel" style="margin-top: 16px;">
          <div class="panel-header">
            <span><i class="el-icon-warning"></i> 传感器事件</span>
            <el-tag size="small" type="warning" v-if="sensorEvents.length">{{ sensorEvents.length }}</el-tag>
          </div>
          <div class="panel-body">
            <div v-if="sensorEvents.length === 0" class="empty-state">
              <i class="el-icon-circle-check"></i>
              <p>暂无未处理事件</p>
            </div>
            <div v-for="event in sensorEvents" :key="event.id" class="event-item">
              <div class="event-header">
                <el-tag :type="severityTagType(event.severity)" size="small">{{ event.severity }}</el-tag>
                <span class="event-metric">{{ metricLabel(event.metricName) }}</span>
                <span class="event-farm">{{ event.farmName }}</span>
              </div>
              <div class="event-detail">
                当前值: <strong>{{ event.currentValue }}</strong>
                / 阈值: {{ event.thresholdValue }}
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右栏：决策历史 + Agent 活动 -->
      <el-col :span="12">
        <!-- 决策历史时间线 -->
        <div class="panel">
          <div class="panel-header">
            <span><i class="el-icon-time"></i> 最近决策链</span>
            <el-button type="text" size="small" @click="$router.push('/auto-patrol')">查看全部</el-button>
          </div>
          <div class="panel-body">
            <div v-if="decisionChains.length === 0" class="empty-state">
              <i class="el-icon-document"></i>
              <p>暂无决策记录</p>
            </div>
            <el-timeline v-else>
              <el-timeline-item
                v-for="chain in decisionChains"
                :key="chain.id"
                :timestamp="formatTime(chain.createdAt)"
                placement="top"
                :color="chainColor(chain.triggerSource)"
              >
                <div class="chain-item" @click="viewChain(chain.chainId)">
                  <div class="chain-source">
                    <el-tag size="small" :type="chainSourceTag(chain.triggerSource)">
                      {{ chainSourceLabel(chain.triggerSource) }}
                    </el-tag>
                  </div>
                  <div class="chain-content">{{ truncate(chain.stepContent, 60) }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>

        <!-- 已完成任务 -->
        <div class="panel" style="margin-top: 16px;">
          <div class="panel-header">
            <span><i class="el-icon-finished"></i> 最近完成任务</span>
          </div>
          <div class="panel-body">
            <div v-if="completedTasks.length === 0" class="empty-state">
              <i class="el-icon-document"></i>
              <p>暂无已完成任务</p>
            </div>
            <div v-for="task in completedTasks" :key="task.id" class="task-item completed">
              <div class="task-header">
                <el-tag type="success" size="small">已完成</el-tag>
                <span class="task-type">{{ taskTypeLabel(task.taskType) }}</span>
                <span class="task-farm">{{ task.farmName || '-' }}</span>
              </div>
              <div class="task-result">{{ task.executionResult || '执行成功' }}</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 决策链详情抽屉 -->
    <el-drawer
      title="决策链详情"
      v-model="drawerVisible"
      size="55%"
      destroy-on-close
    >
      <DecisionChainViewer v-if="currentChainId" :chain-id="currentChainId" />
    </el-drawer>

  </div>
</template>

<script>
import DecisionChainViewer from '@/components/DecisionChainViewer.vue'

export default {
  name: 'UnmannedFarmDashboard',
  components: { DecisionChainViewer },
  data() {
    return {
      loading: false,
      patrolEnabled: true,
      stats: {
        totalChains: 0,
        pendingTasks: 0,
        unhandledEvents: 0,
        autoTasks: 0
      },
      approvalTasks: [],
      sensorEvents: [],
      decisionChains: [],
      completedTasks: [],
      drawerVisible: false,
      currentChainId: ''
    }
  },
  created() {
    this.loadAll()
  },
  methods: {
    async loadAll() {
      this.loading = true
      await Promise.all([
        this.loadStats(),
        this.loadApprovalTasks(),
        this.loadSensorEvents(),
        this.loadDecisionChains(),
        this.loadCompletedTasks()
      ])
      this.loading = false
    },

    async loadStats() {
      try {
        const [chainsRes, tasksRes, eventsRes, autoRes] = await Promise.all([
          this.request.get('/api/agent/decision-chains', { params: { limit: 100 } }),
          this.request.get('/api/agent/tasks/pending-approval', { params: { limit: 100 } }),
          this.request.get('/api/agent/sensor-events', { params: { handled: false, limit: 100 } }),
          this.request.get('/api/agent/tasks', { params: { status: 'pending', limit: 100 } })
        ])
        if (chainsRes.code === '200') this.stats.totalChains = (chainsRes.data || []).length
        if (tasksRes.code === '200') this.stats.pendingTasks = (tasksRes.data || []).length
        if (eventsRes.code === '200') this.stats.unhandledEvents = (eventsRes.data || []).length
        if (autoRes.code === '200') {
          const all = autoRes.data || []
          this.stats.autoTasks = all.filter(t => t.autoExecute).length
        }
      } catch (e) { /* 忽略 */ }
    },

    async loadApprovalTasks() {
      try {
        const res = await this.request.get('/api/agent/tasks/pending-approval', { params: { limit: 20 } })
        if (res.code === '200') this.approvalTasks = res.data || []
      } catch (e) { /* 忽略 */ }
    },

    async loadSensorEvents() {
      try {
        const res = await this.request.get('/api/agent/sensor-events', { params: { handled: false, limit: 20 } })
        if (res.code === '200') this.sensorEvents = res.data || []
      } catch (e) { /* 忽略 */ }
    },

    async loadDecisionChains() {
      try {
        const res = await this.request.get('/api/agent/decision-chains', { params: { limit: 10 } })
        if (res.code === '200') this.decisionChains = res.data || []
      } catch (e) { /* 忽略 */ }
    },

    async loadCompletedTasks() {
      try {
        const res = await this.request.get('/api/agent/tasks', { params: { status: 'completed', limit: 10 } })
        if (res.code === '200') this.completedTasks = res.data || []
      } catch (e) { /* 忽略 */ }
    },

    async approveTask(taskId) {
      try {
        const res = await this.request.post(`/api/agent/task/${taskId}/approve`)
        if (res.code === '200') {
          this.$message.success('已批准')
          this.loadAll()
        } else {
          this.$message.error(res.msg || '审批失败')
        }
      } catch (e) {
        this.$message.error('操作失败: ' + e.message)
      }
    },

    async rejectTask(taskId) {
      try {
        const res = await this.request.post(`/api/agent/task/${taskId}/reject`)
        if (res.code === '200') {
          this.$message.success('已拒绝')
          this.loadAll()
        } else {
          this.$message.error(res.msg || '拒绝失败')
        }
      } catch (e) {
        this.$message.error('操作失败: ' + e.message)
      }
    },

    viewChain(chainId) {
      this.currentChainId = chainId
      this.drawerVisible = true
    },

    // 标签映射
    riskTagType(level) {
      const map = { low: 'success', medium: 'warning', high: 'danger', critical: 'danger' }
      return map[level] || 'info'
    },
    severityTagType(severity) {
      const map = { low: 'info', medium: 'warning', high: 'danger', critical: 'danger' }
      return map[severity] || 'info'
    },
    taskTypeLabel(type) {
      const map = { irrigation: '灌溉', led: '补光', notification: '通知', purchase: '采购', inspection: '巡检' }
      return map[type] || type
    },
    metricLabel(metric) {
      const map = { soil_humidity: '土壤湿度', temperature: '温度', light: '光照', air_humidity: '空气湿度' }
      return map[metric] || metric
    },
    chainSourceLabel(source) {
      const map = { user_chat: '用户对话', auto_patrol: '自主巡检', sensor_event: '传感器事件', scheduled: '定时任务' }
      return map[source] || source
    },
    chainSourceTag(source) {
      const map = { user_chat: 'primary', auto_patrol: 'success', sensor_event: 'warning', scheduled: 'info' }
      return map[source] || ''
    },
    chainColor(source) {
      const map = { user_chat: '#409EFF', auto_patrol: '#67C23A', sensor_event: '#E6A23C', scheduled: '#909399' }
      return map[source] || '#409EFF'
    },

    formatTime(time) {
      if (!time) return ''
      const d = new Date(time)
      return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
    },
    truncate(text, len) {
      if (!text) return ''
      return text.length > len ? text.substring(0, len) + '...' : text
    }
  }
}
</script>

<style scoped>
.dashboard-page {
  padding: 20px;
  max-width: 1400px;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 700;
  color: #1a1a1a;
}
.dashboard-icon { font-size: 24px; color: #409EFF; }
.status-tag { margin-left: 6px; }

/* 统计卡片 */
.stats-row { margin-bottom: 16px; }
.stat-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.stat-icon {
  width: 44px; height: 44px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; color: #fff; margin-right: 12px;
  flex-shrink: 0;
}
.stat-icon.green  { background: linear-gradient(135deg, #67C23A, #4caf50); }
.stat-icon.blue   { background: linear-gradient(135deg, #409EFF, #2196F3); }
.stat-icon.orange { background: linear-gradient(135deg, #E6A23C, #ff9800); }
.stat-icon.purple { background: linear-gradient(135deg, #9c27b0, #7b1fa2); }
.stat-body { flex: 1; }
.stat-label { font-size: 12px; color: #909399; }
.stat-value { font-size: 24px; font-weight: 700; color: #1a1a1a; }
.stat-value.small { font-size: 14px; }
.stat-sub { font-size: 11px; color: #c0c4cc; margin-top: 2px; }

/* 主内容区 */
.main-row { margin-top: 0; }

/* 面板 */
.panel {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.panel-header i { margin-right: 6px; color: #409EFF; }
.panel-body {
  padding: 12px 16px;
  max-height: 400px;
  overflow-y: auto;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 30px 0;
  color: #c0c4cc;
}
.empty-state i { font-size: 36px; display: block; margin-bottom: 8px; }
.empty-state p { font-size: 13px; }

/* 任务项 */
.task-item {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 8px;
  transition: border-color 0.2s;
}
.task-item:hover { border-color: #409EFF; }
.task-item.completed { opacity: 0.7; }
.task-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.task-type { font-weight: 600; font-size: 13px; }
.task-farm { font-size: 12px; color: #909399; margin-left: auto; }
.task-reasoning {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  margin-bottom: 8px;
}
.task-result {
  font-size: 12px;
  color: #67C23A;
}
.task-actions {
  display: flex;
  gap: 6px;
}

/* 事件项 */
.event-item {
  border-left: 3px solid #E6A23C;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #fdf6ec;
  border-radius: 0 4px 4px 0;
}
.event-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.event-metric { font-weight: 600; font-size: 13px; }
.event-farm { font-size: 12px; color: #909399; margin-left: auto; }
.event-detail { font-size: 12px; color: #606266; }

/* 时间线 */
.chain-item {
  cursor: pointer;
  padding: 4px 0;
}
.chain-item:hover { color: #409EFF; }
.chain-source { margin-bottom: 4px; }
.chain-content { font-size: 13px; color: #606266; }
</style>
