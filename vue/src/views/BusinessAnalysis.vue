<template>
  <div class="analysis-center">
    <section class="hero-panel">
      <div>
        <p class="hero-kicker">研判中心</p>
        <h1>预警与研判中心</h1>
        <p class="hero-summary">
          汇总环境采集、视觉巡检和处置记录，完成异常发现、关联定位、执行处置与结果留痕。
        </p>
      </div>
      <div class="hero-actions">
        <el-button size="mini" icon="el-icon-refresh" @click="refreshAll">刷新数据</el-button>
        <el-button type="primary" size="mini" icon="el-icon-map-location" @click="$router.push('/farm-map-gaode')">
          GIS 定位
        </el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <span class="metric-label">待处理预警</span>
        <strong class="metric-value warning">{{ pendingAlerts.length }}</strong>
        <span class="metric-meta">来自环境监测与视觉巡检</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">采集链路</span>
        <strong class="metric-value">{{ sensorStatus.online ? '在线' : '离线' }}</strong>
        <span class="metric-meta">用于承接环境数据与联动指令</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">环境快照</span>
        <strong class="metric-value">{{ environmentText }}</strong>
        <span class="metric-meta">当前温湿度实时回传</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">本页记录</span>
        <strong class="metric-value">{{ actionLogs.length }}</strong>
        <span class="metric-meta">包含执行动作与处理留痕</span>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel list-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">待办队列</p>
            <h2>预警列表</h2>
          </div>
        </div>

        <div v-if="pendingAlerts.length" class="alert-list">
          <button
            v-for="alert in pendingAlerts"
            :key="alert.id"
            type="button"
            class="alert-item"
            :class="{ active: selectedAlert && selectedAlert.id === alert.id }"
            @click="selectAlert(alert)"
          >
            <div class="alert-top">
              <span class="level-pill" :class="alert.alertLevel || 'medium'">
                {{ levelLabel(alert.alertLevel) }}
              </span>
              <span class="time-text">{{ formatTime(alert.createTime) }}</span>
            </div>
            <strong>{{ alert.farmlandName || '监测点位' }}</strong>
            <p>{{ alert.message }}</p>
          </button>
        </div>
        <el-empty v-else description="当前没有待处理预警" :image-size="72"></el-empty>
      </article>

      <article class="panel detail-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">处置面板</p>
            <h2>{{ selectedAlert ? (selectedAlert.farmlandName || '预警详情') : '选择一条预警' }}</h2>
          </div>
          <span class="status-chip" :class="selectedAlertLevel">
            {{ selectedAlert ? levelLabel(selectedAlert.alertLevel) : '待选择' }}
          </span>
        </div>

        <template v-if="selectedAlert">
          <div class="detail-grid">
            <div class="detail-card">
              <span>预警类型</span>
              <strong>{{ typeLabel(selectedAlert.alertType) }}</strong>
            </div>
            <div class="detail-card">
              <span>当前数值</span>
              <strong>{{ valueText(selectedAlert.currentValue) }}</strong>
            </div>
            <div class="detail-card">
              <span>阈值范围</span>
              <strong>{{ thresholdText }}</strong>
            </div>
          </div>

          <div class="message-card">
            <h3>研判说明</h3>
            <p>{{ selectedAlert.message || '暂无描述' }}</p>
            <ul class="suggestion-list">
              <li v-for="item in selectedSuggestions" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div class="action-grid">
            <button type="button" class="action-btn fan" @click="executeDeviceAction('fan', true)">
              启动风扇
            </button>
            <button type="button" class="action-btn led" @click="executeDeviceAction('led', true)">
              打开补光
            </button>
            <button type="button" class="action-btn pump" @click="executeDeviceAction('pump', true)">
              启动水泵
            </button>
            <button type="button" class="action-btn neutral" @click="$router.push('/fruit-detect')">
              视觉复核
            </button>
          </div>

          <div class="footer-actions">
            <el-button @click="$router.push('/farmmap3d')">查看 3D 场景</el-button>
            <el-button type="primary" icon="el-icon-check" @click="markProcessed">标记已处理</el-button>
          </div>
        </template>

        <el-empty v-else description="从左侧选择一条预警开始研判" :image-size="80"></el-empty>
      </article>

      <article class="panel log-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">执行记录</p>
            <h2>处置留痕</h2>
          </div>
        </div>

        <div v-if="actionLogs.length" class="log-list">
          <div v-for="item in actionLogs" :key="item.id" class="log-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.message }}</p>
            <span>{{ item.time }}</span>
          </div>
        </div>
        <el-empty v-else description="本页还没有新的处置记录" :image-size="72"></el-empty>
      </article>
    </section>
  </div>
</template>

<script>
const controlMeta = {
  fan: {
    endpoint: '/aether/device/control/fan',
    payload: value => ({ fan: value }),
    title: '风扇联动'
  },
  led: {
    endpoint: '/aether/device/control/led',
    payload: value => ({ led: value ? 1 : 0 }),
    title: '补光联动'
  },
  pump: {
    endpoint: '/aether/device/control/bump',
    payload: value => ({ bump: value }),
    title: '灌溉联动'
  }
};

export default {
  name: 'BusinessAnalysis',
  data() {
    return {
      pendingAlerts: [],
      selectedAlertId: null,
      sensorStatus: {
        online: false,
        temperature: null,
        humidity: null
      },
      actionLogs: []
    };
  },
  computed: {
    selectedAlert() {
      return this.pendingAlerts.find(item => item.id === this.selectedAlertId) || null;
    },
    selectedAlertLevel() {
      return this.selectedAlert ? (this.selectedAlert.alertLevel || 'medium') : 'steady';
    },
    environmentText() {
      const temp = this.sensorStatus.temperature;
      const humi = this.sensorStatus.humidity;
      if (temp === null || temp === undefined || humi === null || humi === undefined) {
        return '--';
      }
      return `${Math.round(Number(temp))}℃ / ${Math.round(Number(humi))}%`;
    },
    thresholdText() {
      if (!this.selectedAlert) {
        return '--';
      }
      const min = this.valueText(this.selectedAlert.thresholdMin);
      const max = this.valueText(this.selectedAlert.thresholdMax);
      if (min === '--' && max === '--') {
        return '未配置';
      }
      return `${min} - ${max}`;
    },
    selectedSuggestions() {
      if (!this.selectedAlert) {
        return [];
      }

      const items = [];
      if (this.selectedAlert.suggestion) {
        items.push(this.selectedAlert.suggestion);
      }

      const type = String(this.selectedAlert.alertType || '').toLowerCase();
      const message = String(this.selectedAlert.message || '').toLowerCase();

      if (type.includes('humidity') || message.includes('湿')) {
        items.push('建议优先检查灌溉链路，并确认土壤补水是否完成');
      }
      if (type.includes('temperature') || message.includes('温')) {
        items.push('建议优先检查通风状态，确认风扇联动是否已生效');
      }
      if (type.includes('visual') || message.includes('视觉')) {
        items.push('建议前往视觉巡检页面复核图像，再结合 GIS 页面确认异常位置');
      }

      if (!items.length) {
        items.push('建议先复核现场状态，再决定是否执行联动处置');
      }
      return Array.from(new Set(items));
    }
  },
  mounted() {
    this.refreshAll();
  },
  methods: {
    levelLabel(level) {
      const map = {
        high: '高风险',
        medium: '中风险',
        low: '提示'
      };
      return map[level] || '提示';
    },
    typeLabel(type) {
      const map = {
        temperature: '温度预警',
        humidity: '湿度预警',
        soil_temperature: '土壤温度预警',
        soil_humidity: '土壤湿度预警',
        air_temperature: '空气温度预警',
        air_humidity: '空气湿度预警',
        light: '光照预警',
        co2: '二氧化碳预警',
        visual: '视觉预警'
      };
      return map[type] || (type || '通用预警');
    },
    valueText(value) {
      if (value === null || value === undefined || value === '') {
        return '--';
      }
      if (Number.isNaN(Number(value))) {
        return String(value);
      }
      return Number(value).toFixed(1);
    },
    formatTime(value) {
      if (!value) {
        return '--';
      }
      return String(value).replace('T', ' ').slice(0, 16);
    },
    selectAlert(alert) {
      this.selectedAlertId = alert.id;
    },
    async refreshAll() {
      await Promise.allSettled([
        this.fetchAlerts(),
        this.fetchDeviceStatus()
      ]);
    },
    async fetchAlerts() {
      const res = await this.request.get('/alert/pending');
      if (res.code === '200' && Array.isArray(res.data)) {
        this.pendingAlerts = res.data;
        if (this.pendingAlerts.length) {
          if (!this.selectedAlertId || !this.pendingAlerts.some(item => item.id === this.selectedAlertId)) {
            this.selectedAlertId = this.pendingAlerts[0].id;
          }
        } else {
          this.selectedAlertId = null;
        }
      } else {
        this.pendingAlerts = [];
        this.selectedAlertId = null;
      }
    },
    async fetchDeviceStatus() {
      const res = await this.request.get('/aether/device/status');
      if (res.code === '200' && res.data) {
        this.sensorStatus = {
          online: res.data.online !== false,
          temperature: res.data.temperature,
          humidity: res.data.humidity
        };
      }
    },
    async executeDeviceAction(key, value) {
      const config = controlMeta[key];
      if (!config) {
        return;
      }

      try {
        const res = await this.request.post(config.endpoint, config.payload(value));
        if (res.code !== '200') {
          throw new Error(res.msg || '联动执行失败');
        }
        this.pushLog(config.title, `${config.title}已执行，当前目标为${value ? '开启' : '关闭'}状态`);
        this.$message.success(`${config.title}执行成功`);
      } catch (error) {
        this.$message.error(error.message || '联动执行失败');
      }
    },
    async markProcessed() {
      if (!this.selectedAlert) {
        return;
      }

      try {
        const res = await this.request.post(`/alert/${this.selectedAlert.id}/process`, {
          processor: 'web-center'
        });
        if (res.code !== '200') {
          throw new Error(res.msg || '处理失败');
        }
        this.pushLog('闭环处理', `预警 ${this.selectedAlert.id} 已标记为处理完成`);
        this.$message.success('已标记为处理完成');
        await this.fetchAlerts();
      } catch (error) {
        this.$message.error(error.message || '处理失败');
      }
    },
    pushLog(title, message) {
      this.actionLogs.unshift({
        id: `${Date.now()}-${Math.random()}`,
        title,
        message,
        time: new Date().toLocaleTimeString('zh-CN', { hour12: false })
      });
      this.actionLogs = this.actionLogs.slice(0, 10);
    }
  }
};
</script>

<style scoped>
.analysis-center {
  min-height: calc(100vh - 60px);
  padding: 24px;
  background-color: #f4f7fb;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #303133;
}

/* 卡片基础样式 */
.hero-panel,
.metric-card,
.panel {
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  border: 1px solid #ebeef5;
}

/* 顶部区域 */
.hero-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 32px;
  margin-bottom: 24px;
}

.hero-kicker,
.panel-kicker {
  margin: 0 0 4px;
  font-size: 13px;
  color: #909399;
}

.hero-panel h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.hero-summary {
  margin: 8px 0 0;
  color: #606266;
  font-size: 14px;
}

.hero-actions {
  display: flex;
  gap: 12px;
}

/* 指标卡片 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.metric-card {
  padding: 20px 24px;
}

.metric-label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 12px;
  display: block;
}

.metric-value {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
}

.metric-value.warning {
  color: #f56c6c;
}

.metric-meta {
  font-size: 13px;
  color: #c0c4cc;
  margin-top: 12px;
  display: block;
}

/* 主体内页布局 */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1.5fr 1fr;
  gap: 24px;
}

.panel {
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.panel-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

/* 列表容器 */
.alert-list,
.log-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: calc(100vh - 360px);
  overflow-y: auto;
  padding-right: 4px;
}

/* 自定义滚动条 */
.alert-list::-webkit-scrollbar,
.log-list::-webkit-scrollbar {
  width: 6px;
}
.alert-list::-webkit-scrollbar-thumb,
.log-list::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

/* 列表项块 */
.alert-item, .log-item {
  width: 100%;
  flex-shrink: 0;
  box-sizing: border-box;
  display: block;
}

.alert-item {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}

.alert-item:hover {
  background: #f5f7fa;
}

.alert-item.active {
  background: #f0f9eb;
  border-color: #67c23a;
}

.alert-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

/* 标签 */
.level-pill,
.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}

.level-pill.high,
.status-chip.high {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fde2e2;
}

.level-pill.medium,
.status-chip.medium {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #faecd8;
}

.level-pill.low,
.status-chip.low {
  background: #f4f4f5;
  color: #909399;
  border: 1px solid #e9e9eb;
}

.status-chip.steady {
  background: #f4f4f5;
  color: #909399;
  border: 1px solid #e9e9eb;
}

.time-text {
  color: #909399;
  font-size: 12px;
}

.alert-item strong,
.log-item strong,
.detail-card strong {
  display: block;
  font-size: 15px;
  color: #303133;
}

.alert-item p,
.log-item p {
  margin: 8px 0 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

/* 详情面板内容 */
.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.detail-card,
.message-card,
.log-item {
  padding: 16px;
  border-radius: 6px;
  background: #fafafa;
  border: 1px solid #ebeef5;
}

.detail-card span {
  display: block;
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
}

.detail-card strong {
  font-size: 18px;
  color: #303133;
}

.message-card {
  margin-bottom: 24px;
  background: #ffffff;
}

.message-card h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #303133;
}

.suggestion-list {
  margin: 12px 0 0;
  padding-left: 20px;
  color: #606266;
  font-size: 13px;
}

.suggestion-list li + li {
  margin-top: 8px;
}

/* 操作按钮 */
.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.action-btn {
  border: none;
  border-radius: 4px;
  padding: 12px;
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.action-btn:hover {
  opacity: 0.85;
}

.action-btn.fan {
  background: #e6a23c;
}

.action-btn.led {
  background: #409eff;
}

.action-btn.pump {
  background: #67c23a;
}

.action-btn.neutral {
  background: #909399;
}

.footer-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: auto;
}

/* 响应式适配 */
@media screen and (max-width: 1440px) {
  .content-grid {
    grid-template-columns: 1fr 1.3fr 1fr;
  }
}

@media screen and (max-width: 1280px) {
  .content-grid {
    grid-template-columns: 1fr 1fr;
  }
  .log-panel {
    display: none; 
  }
}

@media screen and (max-width: 768px) {
  .analysis-center {
    padding: 16px;
  }

  .hero-panel,
  .panel-head,
  .detail-grid,
  .action-grid {
    flex-direction: column;
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
