<template>
  <div class="patrol-page">

    <!-- 顶部状态栏 -->
    <div class="patrol-header">
      <div class="header-title">
        <i class="el-icon-robot patrol-icon"></i>
        <span>无人农场·自主巡检中心</span>
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

    <!-- AI 最新报告 -->
    <div v-if="status.latestAiReport" class="ai-report-card">
      <div class="ai-report-title"><i class="el-icon-reading"></i> 最新 AI 巡检报告</div>
      <div class="ai-report-content">{{ status.latestAiReport }}</div>
    </div>

    <!-- 规则说明 -->
    <el-collapse class="rule-collapse" v-model="ruleExpanded">
      <el-collapse-item title="📋 当前规则引擎规则（点击展开）" name="rules">
        <div class="rule-list">
          <div class="rule-item"><i class="el-icon-water-cup text-blue"></i> <b>土壤湿度 &lt; 25%</b> → 自动开启灌溉水泵</div>
          <div class="rule-item"><i class="el-icon-sunny text-orange"></i> <b>温度 &gt; 38°C</b> → 推送高温预警通知</div>
          <div class="rule-item"><i class="el-icon-magic-stick text-yellow"></i> <b>光照 &lt; 500 lux（白天 6-18 时）</b> → 自动开启补光灯</div>
          <div class="rule-tip">阈值可在 <code>application.yml → patrol</code> 中调整，改完重启生效</div>
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
        latestAiReport: null
      },
      logs: [],
      toggling: false,
      triggering: false,
      logsLoading: false,
      ruleExpanded: [],
      intervalLabel: '30 分钟'
    }
  },
  created() {
    this.loadStatus()
    this.loadLogs()
  },
  methods: {
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
      try {
        const res = await this.request.post('/api/patrol/trigger')
        if (res.code === '200') {
          this.$message.success(
            `巡检完成 ✅ 检查了 ${res.data.farmsChecked} 块农田，执行了 ${res.data.actionsExecuted} 项操作`
          )
          await this.loadStatus()
          await this.loadLogs()
        } else {
          this.$message.error(res.msg || '巡检失败')
        }
      } catch (e) {
        this.$message.error('巡检异常：' + e.message)
      } finally {
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
