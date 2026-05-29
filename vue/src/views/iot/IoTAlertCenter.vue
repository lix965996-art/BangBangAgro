<template>
  <div class="iot-alert-center">
    <section class="hero-panel">
      <div>
        <p class="hero-kicker">Alert Loop</p>
        <h1>预警闭环中心</h1>
        <p class="hero-summary">
          汇总温湿度阈值预警与视觉巡检结果，并在同一页面完成定位、联动和处置留痕。
        </p>
      </div>
      <div class="hero-actions">
        <el-button size="mini" icon="el-icon-refresh" @click="refreshAll">刷新</el-button>
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
        <span class="metric-label">当前设备状态</span>
        <strong class="metric-value">{{ sensorStatus.online ? '在线' : '离线' }}</strong>
        <span class="metric-meta">风扇 / 补光 / 水泵联动可达</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">环境快照</span>
        <strong class="metric-value">{{ environmentText }}</strong>
        <span class="metric-meta">当前温湿度实时回传</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">已执行动作</span>
        <strong class="metric-value">{{ actionLogs.length }}</strong>
        <span class="metric-meta">本页触发的联动与处置记录</span>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel list-panel">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Pending Queue</p>
            <h2>待处理预警队列</h2>
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
            <p class="panel-eyebrow">Action Board</p>
            <h2>{{ selectedAlert ? (selectedAlert.farmlandName || '预警详情') : '选择预警' }}</h2>
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
            <h3>处置说明</h3>
            <p>{{ selectedAlert.message || '暂无描述' }}</p>
            <ul class="suggestion-list">
              <li v-for="item in selectedSuggestions" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div class="action-grid">
            <button type="button" class="action-btn fan" @click="executeDeviceAction('fan', true)">
              <i class="el-icon-wind-power"></i>
              <span>启动风扇</span>
            </button>
            <button type="button" class="action-btn led" @click="executeDeviceAction('led', true)">
              <i class="el-icon-lightbulb"></i>
              <span>开启补光</span>
            </button>
            <button type="button" class="action-btn pump" @click="executeDeviceAction('pump', true)">
              <i class="el-icon-umbrella"></i>
              <span>启动水泵</span>
            </button>
            <button type="button" class="action-btn neutral" @click="$router.push('/iot-vision')">
              <i class="el-icon-video-camera"></i>
              <span>视觉复核</span>
            </button>
          </div>

          <div class="footer-actions">
            <el-button @click="$router.push('/farmmap3d')">前往 3D 孪生</el-button>
            <el-button type="primary" icon="el-icon-check" @click="markProcessed">标记已处理</el-button>
          </div>
        </template>

        <el-empty v-else description="从左侧选择一条预警开始处置" :image-size="80"></el-empty>
      </article>

      <article class="panel log-panel">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Execution Feed</p>
            <h2>处置记录</h2>
          </div>
        </div>
        <div v-if="actionLogs.length" class="log-list">
          <div v-for="item in actionLogs" :key="item.id" class="log-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.message }}</p>
            <span>{{ item.time }}</span>
          </div>
        </div>
        <el-empty v-else description="本页还没有联动记录" :image-size="72"></el-empty>
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
  name: 'IoTAlertCenter',
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
        items.push('建议前往视觉巡检页复核图像，并结合 GIS 定位确认受影响区域');
      }

      if (!items.length) {
        items.push('建议先复核现场状态，再决定是否执行设备联动');
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
          processor: 'competition-center'
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
.iot-alert-center {
  min-height: calc(100vh - 60px);
  padding: 20px;
  background:
    radial-gradient(circle at top left, rgba(234, 88, 12, 0.12), transparent 18%),
    linear-gradient(180deg, #f8f7f3 0%, #eef3f8 100%);
}

.hero-panel,
.metric-card,
.panel {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(203, 213, 225, 0.74);
  border-radius: 22px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px;
  margin-bottom: 18px;
}

.hero-kicker,
.panel-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #c2410c;
  font-weight: 700;
}

.hero-panel h1,
.panel-head h2 {
  margin: 0;
  color: #14213d;
}

.hero-summary {
  max-width: 700px;
  margin: 10px 0 0;
  color: #475569;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.metric-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-label,
.metric-meta {
  color: #64748b;
}

.metric-label {
  font-size: 13px;
  font-weight: 600;
}

.metric-value {
  font-size: 32px;
  line-height: 1;
  color: #0f172a;
}

.metric-value.warning {
  color: #b91c1c;
}

.metric-meta {
  font-size: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: 0.95fr 1.1fr 0.95fr;
  gap: 16px;
}

.panel {
  padding: 20px 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.alert-list,
.log-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-item {
  width: 100%;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 18px;
  padding: 14px;
  text-align: left;
  cursor: pointer;
}

.alert-item.active {
  border-color: #fdba74;
  background: linear-gradient(180deg, #fff7ed 0%, #f8fafc 100%);
}

.alert-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.level-pill,
.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 7px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.level-pill.high,
.status-chip.high {
  background: #fee2e2;
  color: #991b1b;
}

.level-pill.medium,
.status-chip.medium {
  background: #ffedd5;
  color: #9a3412;
}

.level-pill.low,
.status-chip.low {
  background: #fef3c7;
  color: #92400e;
}

.status-chip.steady {
  background: #e2e8f0;
  color: #334155;
}

.time-text {
  color: #64748b;
  font-size: 12px;
}

.alert-item strong,
.log-item strong,
.detail-card strong {
  display: block;
  color: #0f172a;
}

.alert-item p,
.log-item p,
.message-card p {
  margin: 8px 0 0;
  color: #475569;
  line-height: 1.6;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.detail-card,
.message-card,
.log-item {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.detail-card span {
  display: block;
  margin-bottom: 10px;
  color: #64748b;
  font-size: 12px;
}

.message-card {
  margin-bottom: 16px;
}

.message-card h3 {
  margin: 0 0 10px;
  color: #14213d;
}

.suggestion-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: #0f172a;
}

.suggestion-list li + li {
  margin-top: 8px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
  border: 0;
  border-radius: 16px;
  padding: 16px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.action-btn.fan {
  background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
}

.action-btn.led {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
}

.action-btn.pump {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
}

.action-btn.neutral {
  background: linear-gradient(135deg, #475569 0%, #334155 100%);
}

.footer-actions {
  display: flex;
  gap: 10px;
}

@media screen and (max-width: 1280px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .iot-alert-center {
    padding: 14px;
  }

  .hero-panel,
  .hero-actions,
  .panel-head,
  .metric-grid,
  .detail-grid,
  .action-grid,
  .footer-actions {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
