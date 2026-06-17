<template>
  <div class="patrol-page">

    <!-- 顶部状态栏 -->
    <div class="patrol-header">
      <div class="header-title">
        <i class="el-icon-robot patrol-icon"></i>
        <span>无人农场指挥中心</span>
        <el-tag :type="status.enabled ? 'success' : 'info'" size="small" class="status-tag">
          {{ status.enabled ? '▶ 自主巡检运行中' : '⏸ 巡检已暂停' }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button
          :type="status.enabled ? 'warning' : 'success'"
          size="small"
          :loading="toggling"
          @click="togglePatrol"
          :icon="status.enabled ? 'el-icon-video-pause' : 'el-icon-video-play'"
        >{{ status.enabled ? '暂停自主巡检' : '开启自主巡检' }}</el-button>
        <el-button
          type="primary"
          size="small"
          :loading="triggering"
          icon="el-icon-refresh"
          @click="triggerPatrol"
        >立即巡检一次</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon green"><i class="el-icon-success"></i></div>
          <div class="stat-body">
            <div class="stat-label">累计巡检次数</div>
            <div class="stat-value">{{ status.totalPatrols || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon blue"><i class="el-icon-setting"></i></div>
          <div class="stat-body">
            <div class="stat-label">自动执行操作</div>
            <div class="stat-value">{{ status.totalActions || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon orange"><i class="el-icon-time"></i></div>
          <div class="stat-body">
            <div class="stat-label">上次巡检时间</div>
            <div class="stat-value small">{{ status.lastPatrolTime || '尚未执行' }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon purple"><i class="el-icon-chat-dot-round"></i></div>
          <div class="stat-body">
            <div class="stat-label">巡检间隔</div>
            <div class="stat-value small">{{ intervalLabel }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- AI 最新报告（仅当前用户用自己 key 跑出的报告） -->
    <div v-if="status.latestAiReport" class="ai-report-card">
      <div class="ai-report-title"><i class="el-icon-reading"></i> 最新 AI 巡检报告</div>
      <div class="ai-report-content">{{ status.latestAiReport }}</div>
    </div>
    <!-- 未配置 key 的用户提示：规则引擎在跑，但 AI 分析要先配自己的 key -->
    <div v-else-if="!status.aiConfigured" class="ai-report-card ai-report-hint">
      <div class="ai-report-title"><i class="el-icon-warning-outline"></i> AI 智能分析未启用</div>
      <div class="ai-report-content">
        规则引擎已在运行（按阈值自动灌溉 / 补光 / 预警）。要启用 <b>AI 综合分析</b>，请先到
        「个人中心 → AI 模型配置」填入你自己的 API Key，再点上方「立即巡检一次」——
        分析会用你的 Key 生成，且只对你可见。
      </div>
    </div>

    <div v-if="!embeddedMode" class="agent-workspace">
      <div class="agent-window-card">
        <div class="agent-card-head">
          <div>
            <div class="agent-eyebrow">AUTONOMOUS FARM AGENT</div>
            <div class="agent-title">智能体操作视窗</div>
          </div>
          <el-tag size="small" :type="triggering ? 'warning' : 'info'">
            {{ triggering ? '执行中' : '待命' }}
          </el-tag>
        </div>

        <div class="agent-console">
          <div class="live-window-toolbar">
            <div class="window-dots"><span></span><span></span><span></span></div>
            <div class="window-address">
              <i class="el-icon-monitor"></i>
              帮帮农 / {{ activeAgentScreen }}
            </div>
            <div class="window-state" :class="{ running: triggering || status.enabled }">
              {{ triggering ? '智能体正在接管' : '实时待命' }}
            </div>
          </div>

          <div class="project-live-window real-project-window">
            <div class="agent-cursor" :class="{ active: triggering }" :style="agentCursorStyle">
              <span></span>
            </div>

            <iframe
              class="real-project-frame"
              :src="agentViewportSrc"
              title="帮帮农真实项目操作视窗"
            ></iframe>

            <div class="agent-route-strip">
              <div
                v-for="(item, index) in agentViewportRoutes"
                :key="item.key"
                class="route-chip"
                :class="{
                  active: activeInterfaceKey === item.key,
                  done: index < agentStage - 1
                }"
              >
                <i :class="item.icon"></i>
                <span>{{ item.label }}</span>
              </div>
            </div>

            <div class="agent-live-caption">
              <strong>{{ agentCalloutTitle }}</strong>
              <span>{{ agentCalloutText }}</span>
            </div>
          </div>

          <div class="agent-bottom-feed">
            <div class="feed-title">智能体操作轨迹</div>
            <div class="feed-lines">
              <div
                v-for="(line, index) in visibleOperationFeed"
                :key="line"
                class="feed-line"
                :class="{ current: index === visibleOperationFeed.length - 1 && triggering }"
              >
                <span>{{ index + 1 }}</span>
                {{ line }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="agent-progress-card">
        <div class="progress-head">
          <span>巡检进度</span>
          <strong>{{ agentProgress }}%</strong>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: agentProgress + '%' }"></div>
        </div>

        <div class="agent-step-list">
          <div
            v-for="(step, index) in agentSteps"
            :key="step.title"
            class="agent-step"
            :class="agentStepClass(index)"
          >
            <span class="step-number">{{ index + 1 }}</span>
            <div>
              <div class="step-title">{{ step.title }}</div>
              <div class="step-desc">{{ step.desc }}</div>
            </div>
          </div>
        </div>

        <div class="agent-trace">
          <div class="trace-title">操作轨迹</div>
          <div class="trace-text">{{ triggering ? '智能体正在执行巡检链路' : '等待智能体开始巡检' }}</div>
        </div>
      </div>
    </div>

    <!-- 规则说明 -->
    <el-collapse class="rule-collapse" v-model="ruleExpanded">
      <el-collapse-item title="📋 当前规则引擎规则（点击展开）" name="rules">
        <div class="rule-list">
          <div class="rule-item"><i class="el-icon-water-cup text-blue"></i> <b>土壤湿度 &lt; 25%</b> → 自动开启灌溉水泵</div>
          <div class="rule-item"><i class="el-icon-sunny text-orange"></i> <b>温度 &gt; 38°C</b> → 推送高温预警通知</div>
          <div class="rule-item"><i class="el-icon-magic-stick text-yellow"></i> <b>光照 &lt; 500 lux（白天 6-18 时）</b> → 自动开启补光灯</div>
          <div class="rule-tip">策略阈值可在系统策略中心维护，调整后将用于后续巡检判断。</div>
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- 巡检日志表格 -->
    <div class="log-section">
      <div class="log-header">
        <span class="log-title"><i class="el-icon-document"></i> 巡检决策日志</span>
        <el-button size="mini" icon="el-icon-refresh" @click="loadLogs" :loading="logsLoading">刷新</el-button>
      </div>

      <el-table
        :data="logs"
        v-loading="logsLoading"
        stripe
        size="small"
        style="width:100%"
        :row-class-name="rowClass"
      >
        <el-table-column prop="patrolTime" label="时间" width="160" sortable />
        <el-table-column prop="triggerType" label="触发方式" width="90" align="center">
          <template #default="{row}">
            <el-tag size="mini" :type="row.triggerType === 'manual' ? 'warning' : 'info'">
              {{ row.triggerType === 'manual' ? '手动' : '定时' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="farmName" label="农田" width="110">
          <template #default="{row}">
            <span>{{ row.farmName || '全局' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="actionType" label="操作类型" width="130">
          <template #default="{row}">
            <el-tag size="mini" :type="actionTagType(row.actionType)">{{ actionLabel(row.actionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="详情 / 原因" min-width="220">
          <template #default="{row}">
            <div v-if="row.aiReport" class="ai-report-inline">
              <i class="el-icon-reading"></i> {{ row.aiReport }}
            </div>
            <div v-else>
              <div v-if="row.actionDetail" class="detail-text">{{ row.actionDetail }}</div>
              <div v-if="row.reason" class="reason-text">{{ row.reason }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="90" align="center">
          <template #default="{row}">
            <el-tag size="mini" :type="resultTagType(row.result)">{{ resultLabel(row.result) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="logs.length === 0 && !logsLoading" class="empty-log">
        <i class="el-icon-inbox"></i>
        <p>暂无巡检记录，点击「立即巡检一次」开始</p>
      </div>
    </div>

  </div>
</template>

<script>
export default {
  name: 'AutoPatrol',
  data() {
    return {
      status: {
        enabled: true,
        totalPatrols: 0,
        totalActions: 0,
        lastPatrolTime: null,
        latestAiReport: null,
        aiConfigured: false
      },
      logs: [],
      toggling: false,
      triggering: false,
      logsLoading: false,
      ruleExpanded: [],
      intervalLabel: '30 分钟',
      agentStage: 0,
      agentSteps: [
        { title: '打开无人农场控制台', desc: '读取运行状态' },
        { title: '读取环境监测数据', desc: '采集温湿度与光照' },
        { title: '查看视觉巡检结果', desc: '聚合图像异常' },
        { title: '定位地块与巡检区域', desc: '确认异常位置' },
        { title: '执行自主巡检策略', desc: '触发灌溉/补光/通知' }
      ],
      agentViewportRoutes: [
        { key: 'control', label: '无人农场指挥中心', path: '/auto-patrol?agentViewport=1', icon: 'el-icon-monitor' },
        { key: 'sensor', label: '环境监测', path: '/aether-monitor?agentViewport=1', icon: 'el-icon-sunny' },
        { key: 'vision', label: '视觉巡检', path: '/fruit-detect?agentViewport=1', icon: 'el-icon-camera-solid' },
        { key: 'map', label: '地块资产图谱', path: '/farmland?agentViewport=1', icon: 'el-icon-location-outline' },
        { key: 'execute', label: '无人农场总控', path: '/unmanned-dashboard?agentViewport=1', icon: 'el-icon-magic-stick' }
      ],
      operationFeed: [
        '打开项目真实页面：无人农场指挥中心，读取巡检状态和运行数据。',
        '切换项目真实页面：环境监测，查看温湿度、光照和传感器数据。',
        '切换项目真实页面：视觉巡检，查看图像识别和异常结果。',
        '切换项目真实页面：地块资产图谱，定位异常地块和巡检区域。',
        '切换项目真实页面：无人农场总控，执行策略并写入巡检日志。'
      ]
    }
  },
  computed: {
    agentProgress() {
      return Math.min(100, this.agentStage * 20)
    },
    recentAgentLogs() {
      return (this.logs || []).slice(0, 5)
    },
    latestActionLabel() {
      const latest = this.recentAgentLogs[0]
      return latest ? this.actionLabel(latest.actionType) : '暂无'
    },
    embeddedMode() {
      return this.$route && this.$route.query && this.$route.query.agentViewport === '1'
    },
    agentCalloutTitle() {
      return this.triggering ? this.agentSteps[Math.max(this.agentStage - 1, 0)].title : '打开无人农场控制台'
    },
    agentCalloutText() {
      return this.triggering
        ? `智能体正在操作真实项目页面：${this.activeAgentScreen}。`
        : '这里显示的是项目真实页面，不是模拟图。点击“立即巡检一次”后，智能体会在这些真实模块间切换。'
    },
    activeInterfaceKey() {
      const keys = ['control', 'sensor', 'vision', 'map', 'execute']
      return keys[Math.max(this.agentStage - 1, 0)] || 'control'
    },
    activeAgentScreen() {
      const current = this.agentViewportRoutes.find(item => item.key === this.activeInterfaceKey)
      return current ? current.label : '无人农场指挥中心'
    },
    activeAgentRoute() {
      return this.agentViewportRoutes.find(item => item.key === this.activeInterfaceKey) || this.agentViewportRoutes[0]
    },
    agentViewportSrc() {
      const baseUrl = window.location.href.split('#')[0]
      return `${baseUrl}#${this.activeAgentRoute.path}`
    },
    agentCursorStyle() {
      const positions = [
        { left: '27%', top: '24%' },
        { left: '50%', top: '43%' },
        { left: '72%', top: '43%' },
        { left: '45%', top: '72%' },
        { left: '78%', top: '83%' }
      ]
      const current = positions[Math.max(this.agentStage - 1, 0)] || positions[0]
      return {
        left: current.left,
        top: current.top
      }
    },
    visibleOperationFeed() {
      const count = this.agentStage > 0 ? this.agentStage : 1
      return this.operationFeed.slice(0, count)
    }
  },
  created() {
    this.loadStatus()
    this.loadLogs()
  },
  methods: {
    agentStepClass(index) {
      if (!this.agentStage) return ''
      if (index < this.agentStage - 1) return 'done'
      if (index === this.agentStage - 1) return this.triggering ? 'active' : 'done'
      return ''
    },
    async loadStatus() {
      try {
        const res = await this.request.get('/api/patrol/status')
        if (res.code === '200') this.status = res.data
      } catch (e) { /* 忽略 */ }
    },
    async loadLogs() {
      this.logsLoading = true
      try {
        const res = await this.request.get('/api/patrol/logs?limit=100')
        if (res.code === '200') this.logs = res.data || []
      } catch (e) { /* 忽略 */ } finally {
        this.logsLoading = false
      }
    },
    async togglePatrol() {
      this.toggling = true
      try {
        const res = await this.request.post('/api/patrol/toggle')
        if (res.code === '200') {
          this.status.enabled = res.data.enabled
          this.$message.success(res.data.message)
        }
      } catch (e) {
        this.$message.error('操作失败：' + e.message)
      } finally {
        this.toggling = false
      }
    },
    async triggerPatrol() {
      this.triggering = true
      this.agentStage = 1
      const stageTimer = setInterval(() => {
        this.agentStage = Math.min(this.agentStage + 1, this.agentSteps.length)
      }, 650)
      try {
        const res = await this.request.post('/api/patrol/trigger')
        if (res.code === '200') {
          this.agentStage = this.agentSteps.length
          if (this.$message.closeAll) this.$message.closeAll()
          this.$message.success(
            `巡检完成 ✅ 检查了 ${res.data.farmsChecked} 块农田，执行了 ${res.data.actionsExecuted} 项操作`
          )
          await this.loadStatus()
          await this.loadLogs()
          // AI 综合分析走异步，触发后稍等再拉，等"用你自己 key 跑出的报告"写入数据库
          setTimeout(() => this.loadStatus(), 4000)
          setTimeout(() => { this.loadStatus(); this.loadLogs() }, 9000)
        } else {
          this.$message.error(res.msg || '巡检失败')
        }
      } catch (e) {
        this.$message.error('巡检异常：' + e.message)
      } finally {
        clearInterval(stageTimer)
        this.triggering = false
      }
    },

    actionLabel(type) {
      const map = {
        irrigation_on:     '开启灌溉',
        led_on:            '开启补光灯',
        send_notification: '推送通知',
        ai_analysis:       'AI 报告',
        no_action:         '无需操作'
      }
      return map[type] || type
    },
    actionTagType(type) {
      const map = {
        irrigation_on:     'primary',
        led_on:            'warning',
        send_notification: 'danger',
        ai_analysis:       'success',
        no_action:         'info'
      }
      return map[type] || ''
    },
    resultLabel(r) {
      const map = { success: '成功', failed: '失败', skipped: '跳过', no_action: '正常' }
      return map[r] || r
    },
    resultTagType(r) {
      const map = { success: 'success', failed: 'danger', skipped: 'info', no_action: '' }
      return map[r] || ''
    },
    rowClass({ row }) {
      if (row.actionType === 'ai_analysis') return 'row-ai'
      if (row.result === 'failed') return 'row-failed'
      if (row.actionType === 'no_action') return 'row-normal'
      return ''
    }
  }
}
</script>

<style scoped>
.patrol-page {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px;
}

.patrol-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 700;
  color: #1a1a1a;
  min-width: 0;
  flex-wrap: wrap;
}
.patrol-icon {
  font-size: 24px;
  color: #10b981;
}
.status-tag { margin-left: 4px; }
.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* 统计卡片 */
.stats-row { margin-bottom: 16px; }
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 6px rgba(0,0,0,.07);
  height: 76px;
  min-width: 0;
}
.stat-icon {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; flex-shrink: 0;
}
.stat-icon.green  { background: #d1fae5; color: #059669; }
.stat-icon.blue   { background: #dbeafe; color: #2563eb; }
.stat-icon.orange { background: #ffedd5; color: #ea580c; }
.stat-icon.purple { background: #ede9fe; color: #7c3aed; }
.stat-label { font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.stat-value { font-size: 22px; font-weight: 700; color: #111827; line-height: 1; }
.stat-value.small { font-size: 13px; font-weight: 600; }
.stat-body { min-width: 0; }

/* AI 报告 */
.ai-report-card {
  background: linear-gradient(135deg, #f0fdf4, #ecfdf5);
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  padding: 14px 18px;
  margin-bottom: 16px;
}
.ai-report-title { font-weight: 600; color: #065f46; margin-bottom: 8px; }
.ai-report-content { color: #374151; font-size: 14px; line-height: 1.6; }
.ai-report-hint {
  background: linear-gradient(135deg, #fffbeb, #fef9c3);
  border-color: #fde68a;
}
.ai-report-hint .ai-report-title { color: #92400e; }

/* 智能体操作视窗 */
.agent-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  margin-bottom: 16px;
}

.agent-window-card,
.agent-progress-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 6px rgba(0,0,0,.07);
}

.agent-window-card {
  padding: 18px;
}

.agent-card-head,
.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.agent-eyebrow {
  color: #059669;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .5px;
}

.agent-title {
  margin-top: 4px;
  color: #111827;
  font-size: 18px;
  font-weight: 800;
}

.agent-console {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid #dbe7df;
  border-radius: 10px;
  background: linear-gradient(135deg, #f8fbf9, #effaf2);
}

.live-window-toolbar {
  height: 38px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  border: 1px solid #e2eee6;
  border-radius: 9px 9px 0 0;
  background: #ffffff;
}

.window-dots {
  display: flex;
  gap: 6px;
}

.window-dots span {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: #d1d5db;
}

.window-dots span:nth-child(1) { background: #f87171; }
.window-dots span:nth-child(2) { background: #fbbf24; }
.window-dots span:nth-child(3) { background: #34d399; }

.window-address {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f3d2b;
  font-size: 13px;
  font-weight: 800;
}

.window-address i {
  margin-right: 6px;
  color: #059669;
}

.window-state {
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.window-state.running {
  background: #dcfce7;
  color: #047857;
}

.project-live-window {
  position: relative;
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  min-height: 520px;
  overflow: hidden;
  border: 1px solid #dbe7df;
  border-top: 0;
  border-radius: 0 0 10px 10px;
  background: #eef6f0;
}

.project-live-window.real-project-window {
  display: block;
  min-height: 620px;
  background: #ffffff;
}

.real-project-frame {
  width: 100%;
  height: 620px;
  border: 0;
  background: #f3f7fb;
}

.agent-route-strip {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  z-index: 4;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  pointer-events: none;
}

.route-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(148, 163, 184, .28);
  border-radius: 999px;
  background: rgba(255, 255, 255, .82);
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 6px 18px rgba(15, 23, 42, .08);
  backdrop-filter: blur(8px);
}

.route-chip.active {
  border-color: rgba(16, 185, 129, .65);
  background: rgba(220, 252, 231, .92);
  color: #047857;
}

.route-chip.done {
  border-color: rgba(74, 222, 128, .36);
  color: #059669;
}

.agent-live-caption {
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 18px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(187, 247, 208, .9);
  border-radius: 12px;
  background: rgba(255, 255, 255, .9);
  box-shadow: 0 14px 30px rgba(15, 23, 42, .12);
  backdrop-filter: blur(10px);
}

.agent-live-caption strong {
  flex: 0 0 auto;
  color: #10251b;
  font-size: 14px;
  font-weight: 900;
}

.agent-live-caption span {
  min-width: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
}

.mini-project-sidebar {
  padding: 14px 10px;
  background: #f8fbf9;
  border-right: 1px solid #dbe7df;
}

.mini-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 8px;
}

.mini-logo img {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: cover;
}

.mini-logo strong,
.mini-logo small {
  display: block;
}

.mini-logo strong {
  color: #0f172a;
  font-size: 14px;
}

.mini-logo small {
  margin-top: 2px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.2;
}

.mini-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 8px 10px;
  border-radius: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.mini-nav-item + .mini-nav-item {
  margin-top: 6px;
}

.mini-nav-item i {
  color: #78918b;
  font-size: 15px;
}

.mini-nav-item.active {
  background: #dcfce7;
  color: #047857;
}

.mini-nav-item.active i {
  color: #059669;
}

.mini-project-screen {
  min-width: 0;
  padding: 16px;
  background: linear-gradient(180deg, #f8fbf9 0%, #edf7f1 100%);
}

.screen-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
  margin-bottom: 14px;
}

.screen-eyebrow {
  color: #059669;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: .5px;
}

.screen-head h3 {
  margin: 4px 0 0;
  color: #10251b;
  font-size: 18px;
  line-height: 1.2;
}

.screen-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.screen-kpis {
  display: grid;
  grid-template-columns: repeat(3, 78px);
  gap: 8px;
}

.screen-kpis div {
  padding: 9px;
  border: 1px solid #e2eee6;
  border-radius: 8px;
  background: #fff;
}

.screen-kpis span {
  display: block;
  color: #64748b;
  font-size: 11px;
}

.screen-kpis strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: #10251b;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.screen-body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.operation-panel {
  min-height: 112px;
  padding: 12px;
  border: 1px solid #e2eee6;
  border-radius: 10px;
  background: rgba(255, 255, 255, .76);
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.operation-panel.active {
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, .14), 0 8px 22px rgba(15, 118, 110, .12);
  transform: translateY(-1px);
}

.operation-panel.done {
  border-color: #bbf7d0;
  background: #f7fef9;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 10px;
  color: #1f3d2b;
  font-size: 13px;
  font-weight: 900;
}

.panel-title i {
  color: #059669;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.control-grid div,
.sensor-card {
  padding: 10px;
  border-radius: 8px;
  background: #f8fbf9;
}

.control-grid span,
.sensor-card span {
  display: block;
  color: #64748b;
  font-size: 11px;
}

.control-grid strong,
.sensor-card strong {
  display: block;
  margin-top: 6px;
  color: #10251b;
  font-size: 13px;
}

.sensor-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.sensor-card small {
  display: block;
  margin-top: 5px;
  color: #94a3b8;
  font-size: 10px;
}

.sensor-card.warn {
  background: #fff7ed;
}

.sensor-card.warn strong {
  color: #c2410c;
}

.vision-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.vision-tile {
  min-height: 58px;
  padding: 9px;
  border-radius: 8px;
  background: #f8fbf9;
  color: #64748b;
}

.vision-tile.active {
  background: #fef3c7;
  color: #92400e;
}

.vision-tile span,
.vision-tile strong {
  display: block;
}

.vision-tile span {
  font-size: 11px;
}

.vision-tile strong {
  margin-top: 5px;
  font-size: 12px;
}

.field-map {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.field-block {
  min-height: 58px;
  padding: 9px;
  border: 1px solid #dbe7df;
  border-radius: 8px;
  background: #f4fbf6;
}

.field-block.selected {
  border-color: #10b981;
  background: #dcfce7;
}

.field-block strong,
.field-block span {
  display: block;
}

.field-block strong {
  color: #10251b;
  font-size: 12px;
}

.field-block span {
  margin-top: 6px;
  color: #64748b;
  font-size: 11px;
}

.execute-panel {
  grid-column: span 2;
  min-height: 86px;
}

.execute-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.execute-actions button {
  height: 36px;
  border: 1px solid #dbe7df;
  border-radius: 8px;
  background: #fff;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.execute-actions button.active {
  border-color: #10b981;
  background: #10b981;
  color: #fff;
}

.agent-cursor {
  position: absolute;
  z-index: 5;
  width: 24px;
  height: 24px;
  transform: translate(-4px, -2px);
  transition: left .45s ease, top .45s ease;
  opacity: .72;
  pointer-events: none;
}

.agent-cursor span {
  display: block;
  width: 0;
  height: 0;
  border-left: 12px solid #0f766e;
  border-top: 8px solid transparent;
  border-bottom: 8px solid transparent;
  filter: drop-shadow(0 2px 4px rgba(15, 23, 42, .25));
  transform: rotate(35deg);
}

.agent-cursor.active::after {
  content: "";
  position: absolute;
  left: 6px;
  top: 6px;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: rgba(16, 185, 129, .18);
  animation: cursorPulse 1s infinite;
}

@keyframes cursorPulse {
  from { transform: scale(.8); opacity: .8; }
  to { transform: scale(1.8); opacity: 0; }
}

.agent-bottom-feed {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #e2eee6;
  border-radius: 9px;
  background: #fff;
}

.feed-title {
  margin-bottom: 8px;
  color: #1f3d2b;
  font-size: 13px;
  font-weight: 900;
}

.feed-lines {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.feed-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 26px;
  color: #475569;
  font-size: 12px;
}

.feed-line span {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #dcfce7;
  color: #047857;
  font-size: 11px;
  font-weight: 900;
}

.feed-line.current {
  color: #047857;
  font-weight: 800;
}

.console-status {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #dfeee4;
}

.console-status-main {
  display: flex;
  gap: 12px;
  min-width: 0;
}

.pulse-dot {
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
  margin-top: 5px;
  border-radius: 999px;
  background: #cbd5e1;
  box-shadow: 0 0 0 5px rgba(148, 163, 184, .14);
}

.pulse-dot.running {
  background: #10b981;
  box-shadow: 0 0 0 5px rgba(16, 185, 129, .14);
}

.console-title {
  color: #10251b;
  font-size: 18px;
  font-weight: 800;
}

.console-desc {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.console-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  max-width: 420px;
}

.console-meta span {
  padding: 5px 8px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #e2eee6;
  color: #4f6b58;
  font-size: 12px;
  font-weight: 700;
}

.console-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 14px;
}

.console-metric {
  padding: 14px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, .04);
}

.console-metric span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.console-metric strong {
  display: block;
  margin-top: 7px;
  color: #10251b;
  font-size: 18px;
  font-weight: 800;
}

.console-section {
  margin-top: 14px;
  padding: 14px;
  border-radius: 10px;
  background: #fff;
}

.console-section-title {
  color: #1f3d2b;
  font-size: 13px;
  font-weight: 800;
}

.console-report {
  margin-top: 8px;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}

.console-log-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.console-log-item {
  display: grid;
  grid-template-columns: 150px 1fr 64px;
  gap: 10px;
  align-items: center;
  padding: 9px 10px;
  border-radius: 8px;
  background: #f8fbf9;
  color: #475569;
  font-size: 12px;
}

.log-action {
  color: #0f766e;
  font-weight: 800;
}

.log-result {
  text-align: right;
  color: #059669;
  font-weight: 800;
}

.console-empty {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.agent-progress-card {
  padding: 18px;
}

.progress-head span {
  color: #1f3d2b;
  font-size: 14px;
  font-weight: 800;
}

.progress-head strong {
  color: #111827;
  font-size: 16px;
}

.progress-track {
  height: 8px;
  margin: 10px 0 16px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf5ef;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #10b981, #34d399);
  transition: width .3s ease;
}

.agent-step-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.agent-step {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fbf9;
  color: #64748b;
}

.agent-step.active {
  background: #ecfdf5;
  color: #065f46;
}

.agent-step.done {
  color: #047857;
  background: #f0fdf4;
}

.step-number {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #e2e8f0;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.agent-step.active .step-number,
.agent-step.done .step-number {
  background: #bbf7d0;
  color: #047857;
}

.step-title {
  color: #1f2937;
  font-size: 13px;
  font-weight: 800;
}

.step-desc {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
}

.agent-trace {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #edf2ee;
}

.trace-title {
  color: #1f3d2b;
  font-size: 13px;
  font-weight: 800;
}

.trace-text {
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
}

/* 规则说明 */
.rule-collapse { margin-bottom: 16px; border-radius: 8px; overflow: hidden; }
.rule-list { padding: 4px 0; }
.rule-item {
  padding: 6px 0;
  font-size: 13px;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 8px;
}
.text-blue   { color: #2563eb; }
.text-orange { color: #ea580c; }
.text-yellow { color: #d97706; }
.rule-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #9ca3af;
}
.rule-tip code { background: #f3f4f6; padding: 1px 4px; border-radius: 3px; }

/* 日志区域 */
.log-section {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 6px rgba(0,0,0,.07);
  min-height: 320px;
}
.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.log-title { font-size: 15px; font-weight: 600; color: #111827; }
.detail-text { font-size: 13px; color: #374151; }
.reason-text  { font-size: 12px; color: #6b7280; margin-top: 2px; }
.ai-report-inline { font-size: 12px; color: #065f46; line-height: 1.5; }
.empty-log {
  text-align: center;
  padding: 40px 0;
  color: #9ca3af;
}
.empty-log i { font-size: 36px; display: block; margin-bottom: 8px; }

@media (max-width: 1100px) {
  .agent-workspace {
    grid-template-columns: 1fr;
  }

  .stats-row :deep(.el-col) {
    width: 50%;
    max-width: 50%;
    flex: 0 0 50%;
    margin-bottom: 16px;
  }
}

@media (max-width: 720px) {
  .patrol-page {
    padding: 16px;
  }

  .patrol-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .console-status {
    flex-direction: column;
  }

  .console-meta {
    justify-content: flex-start;
  }

  .console-grid {
    grid-template-columns: 1fr;
  }

  .console-log-item {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .log-result {
    text-align: left;
  }

  .stats-row :deep(.el-col) {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
  }
}
</style>

<style>
.row-ai    td { background: #f0fdf4 !important; }
.row-failed td { background: #fff1f2 !important; }
.row-normal td { color: #9ca3af; }
</style>
