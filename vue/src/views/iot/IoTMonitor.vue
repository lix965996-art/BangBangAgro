<template>
  <div class="iot-monitor">
    <section class="top-bar">
      <div>
        <p class="kicker">Device Loop</p>
        <h1>设备感知与联动中心</h1>
        <p class="summary">围绕温湿度监测、阈值预警和风扇 / 补光 / 水泵联动的物联网主页面。</p>
      </div>
      <div class="actions">
        <div class="mode-box">
          <span>自动联动</span>
          <el-switch v-model="autoMode" active-color="#0f766e"></el-switch>
        </div>
        <el-button type="primary" size="mini" icon="el-icon-magic-stick" @click="runRecommendedActions">
          执行建议动作
        </el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card">
        <span class="stat-label">空气温度</span>
        <strong>{{ tempText }}</strong>
        <span class="stat-foot">当前阈值 {{ thresholds.tempLow }} - {{ thresholds.tempHigh }}℃</span>
      </article>
      <article class="stat-card">
        <span class="stat-label">空气湿度</span>
        <strong>{{ humiText }}</strong>
        <span class="stat-foot">当前阈值 {{ thresholds.humiLow }} - {{ thresholds.humiHigh }}%</span>
      </article>
      <article class="stat-card">
        <span class="stat-label">设备状态</span>
        <strong>{{ sensorStatus.online ? '在线' : '离线' }}</strong>
        <span class="stat-foot">{{ currentSuggestion.title }}</span>
      </article>
      <article class="stat-card">
        <span class="stat-label">待处理预警</span>
        <strong>{{ pendingAlerts.length }}</strong>
        <span class="stat-foot">来自环境阈值与巡检结果</span>
      </article>
    </section>

    <section class="layout">
      <article class="panel chart-panel">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">History Trend</p>
            <h2>近 7 天温湿度趋势</h2>
          </div>
          <el-button link @click="refreshData">刷新</el-button>
        </div>
        <div ref="trendChart" class="trend-chart"></div>
      </article>

      <article class="panel control-panel">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Control Board</p>
            <h2>联动控制台</h2>
          </div>
          <span class="chip" :class="{ muted: !sensorStatus.online }">
            {{ sensorStatus.online ? '控制就绪' : '等待设备恢复' }}
          </span>
        </div>

        <div class="control-list">
          <div class="control-item" :class="{ active: controls.fan }">
            <div>
              <span class="control-name">风扇联动</span>
              <p>用于高温或高湿场景通风降温</p>
            </div>
            <el-switch
              v-model="controls.fan"
              active-color="#0f766e"
              @change="toggleControl('fan', $event)"
            ></el-switch>
          </div>

          <div class="control-item" :class="{ active: controls.led }">
            <div>
              <span class="control-name">补光联动</span>
              <p>用于低光照场景补光</p>
            </div>
            <el-switch
              v-model="controls.led"
              active-color="#2563eb"
              @change="toggleControl('led', $event)"
            ></el-switch>
          </div>

          <div class="control-item" :class="{ active: controls.pump }">
            <div>
              <span class="control-name">灌溉联动</span>
              <p>用于湿度偏低时快速补水</p>
            </div>
            <el-switch
              v-model="controls.pump"
              active-color="#059669"
              @change="toggleControl('pump', $event)"
            ></el-switch>
          </div>
        </div>

        <div class="suggestion-card">
          <div class="suggestion-title">
            <i class="el-icon-guide"></i>
            <span>{{ currentSuggestion.title }}</span>
          </div>
          <p>{{ currentSuggestion.message }}</p>
          <ul class="suggestion-list">
            <li v-for="action in currentSuggestion.actions" :key="action.key">
              {{ action.label }}
            </li>
          </ul>
        </div>
      </article>

      <article class="panel log-panel">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Action Feed</p>
            <h2>联动日志</h2>
          </div>
        </div>
        <div v-if="actionLogs.length" class="log-list">
          <div v-for="log in actionLogs" :key="log.id" class="log-item">
            <strong>{{ log.title }}</strong>
            <p>{{ log.message }}</p>
            <span>{{ log.time }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无联动记录" :image-size="72"></el-empty>
      </article>
    </section>
  </div>
</template>

<script>
import * as echarts from 'echarts';

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
  name: 'IoTMonitor',
  data() {
    return {
      autoMode: false,
      sensorStatus: {
        online: false,
        temperature: null,
        humidity: null
      },
      controls: {
        fan: false,
        led: false,
        pump: false
      },
      thresholds: {
        tempHigh: 30,
        tempLow: 12,
        humiHigh: 75,
        humiLow: 25
      },
      pendingAlerts: [],
      actionLogs: [],
      history: [],
      chart: null,
      pollTimer: null
    }
  },
  computed: {
    tempText() {
      if (this.sensorStatus.temperature === null || this.sensorStatus.temperature === undefined) {
        return '--';
      }
      return `${Number(this.sensorStatus.temperature).toFixed(1)}℃`;
    },
    humiText() {
      if (this.sensorStatus.humidity === null || this.sensorStatus.humidity === undefined) {
        return '--';
      }
      return `${Number(this.sensorStatus.humidity).toFixed(1)}%`;
    },
    currentSuggestion() {
      const temp = Number(this.sensorStatus.temperature);
      const humi = Number(this.sensorStatus.humidity);
      const actions = [];

      if (Number.isFinite(temp) && temp > this.thresholds.tempHigh) {
        actions.push({ key: 'fan', label: '温度偏高，建议优先开启风扇降温' });
      }
      if (Number.isFinite(humi) && humi < this.thresholds.humiLow) {
        actions.push({ key: 'pump', label: '湿度偏低，建议补水提升土壤湿度' });
      }
      if (Number.isFinite(temp) && temp < this.thresholds.tempLow) {
        actions.push({ key: 'led', label: '温度偏低，可结合补光维持生长环境' });
      }

      if (!actions.length) {
        return {
          title: '当前环境平稳',
          message: '温湿度处于可接受区间，可继续保持当前运行状态。',
          actions: [
            { key: 'steady', label: '继续监测，不触发额外联动动作' }
          ]
        };
      }

      return {
        title: '已生成联动建议',
        message: '当前环境已触发策略阈值，可执行下列建议动作以缩短处置时间。',
        actions
      };
    }
  },
  mounted() {
    this.initChart();
    this.refreshData();
    this.pollTimer = setInterval(() => this.refreshData(), 15000);
  },
  beforeUnmount() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
    window.removeEventListener('resize', this.handleResize);
    if (this.chart && !this.chart.isDisposed()) {
      this.chart.dispose();
    }
  },
  methods: {
    initChart() {
      this.chart = echarts.init(this.$refs.trendChart);
      this.renderChart();
      window.addEventListener('resize', this.handleResize);
    },
    handleResize() {
      if (this.chart && !this.chart.isDisposed()) {
        this.chart.resize();
      }
    },
    async refreshData() {
      await Promise.allSettled([
        this.fetchDeviceStatus(),
        this.fetchHistory(),
        this.fetchAlerts()
      ]);
      if (this.autoMode) {
        this.runRecommendedActions();
      }
    },
    async fetchDeviceStatus() {
      const [sensorRes, pumpRes] = await Promise.all([
        this.request.get('/aether/device/status'),
        this.request.get('/aether/device/new/status')
      ]);

      if (sensorRes.code === '200' && sensorRes.data) {
        this.sensorStatus = {
          online: sensorRes.data.online !== false,
          temperature: sensorRes.data.temperature,
          humidity: sensorRes.data.humidity
        };
        this.controls.led = sensorRes.data.led === 1 || sensorRes.data.led === true;
        this.controls.fan = !!sensorRes.data.fan;
        this.controls.pump = !!sensorRes.data.pump;
      }

      if (pumpRes.code === '200' && pumpRes.data) {
        this.controls.pump = !!pumpRes.data.bump || this.controls.pump;
      }
    },
    async fetchHistory() {
      const res = await this.request.get('/aether/readings/detail', {
        params: {
          days: 7
        }
      });
      if (res.code === '200' && res.data && Array.isArray(res.data.data)) {
        this.history = res.data.data.slice(-24);
        this.renderChart();
      }
    },
    async fetchAlerts() {
      const res = await this.request.get('/alert/pending');
      if (res.code === '200' && Array.isArray(res.data)) {
        this.pendingAlerts = res.data.slice(0, 6);
      } else {
        this.pendingAlerts = [];
      }
    },
    renderChart() {
      if (!this.chart) {
        return;
      }

      const xAxis = this.history.map(item => {
        const value = String(item.date || '');
        return value.length >= 16 ? value.slice(5, 16).replace('T', ' ') : value;
      });
      const tempData = this.history.map(item => item.temp);
      const humiData = this.history.map(item => item.humi);

      this.chart.setOption({
        tooltip: {
          trigger: 'axis'
        },
        legend: {
          data: ['温度', '湿度'],
          right: 0
        },
        grid: {
          left: 28,
          right: 24,
          top: 48,
          bottom: 40,
          containLabel: true
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: xAxis,
          axisLabel: {
            color: '#64748b'
          }
        },
        yAxis: [
          {
            type: 'value',
            name: '温度 ℃',
            axisLabel: {
              color: '#64748b'
            },
            splitLine: {
              lineStyle: {
                color: '#e2e8f0',
                type: 'dashed'
              }
            }
          },
          {
            type: 'value',
            name: '湿度 %',
            axisLabel: {
              color: '#64748b'
            }
          }
        ],
        series: [
          {
            name: '温度',
            type: 'line',
            smooth: true,
            data: tempData,
            showSymbol: false,
            lineStyle: {
              color: '#ea580c',
              width: 3
            },
            areaStyle: {
              color: 'rgba(234, 88, 12, 0.08)'
            }
          },
          {
            name: '湿度',
            type: 'line',
            smooth: true,
            yAxisIndex: 1,
            data: humiData,
            showSymbol: false,
            lineStyle: {
              color: '#0f766e',
              width: 3
            },
            areaStyle: {
              color: 'rgba(15, 118, 110, 0.08)'
            }
          }
        ]
      });
    },
    async toggleControl(key, value) {
      const config = controlMeta[key];
      if (!config) {
        return;
      }

      try {
        const res = await this.request.post(config.endpoint, config.payload(value));
        if (res.code === '200') {
          this.pushLog(config.title, value ? '已执行开启动作' : '已执行关闭动作');
        } else {
          throw new Error(res.msg || '设备控制失败');
        }
      } catch (error) {
        this.$message.error(error.message || '设备控制失败');
        this.controls[key] = !value;
      }
    },
    async runRecommendedActions() {
      const actions = this.currentSuggestion.actions.filter(action => controlMeta[action.key]);
      for (const action of actions) {
        if (!this.controls[action.key]) {
          this.controls[action.key] = true;
          await this.toggleControl(action.key, true);
        }
      }
    },
    pushLog(title, message) {
      this.actionLogs.unshift({
        id: `${Date.now()}-${Math.random()}`,
        title,
        message,
        time: new Date().toLocaleTimeString('zh-CN', { hour12: false })
      });
      this.actionLogs = this.actionLogs.slice(0, 8);
    }
  }
}
</script>

<style scoped>
.iot-monitor {
  min-height: calc(100vh - 60px);
  padding: 20px;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.14), transparent 20%),
    linear-gradient(180deg, #f5f7fb 0%, #eef3f8 100%);
}

.top-bar,
.stat-card,
.panel {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(203, 213, 225, 0.74);
  border-radius: 22px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
}

.top-bar {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px;
  margin-bottom: 18px;
}

.kicker,
.panel-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #0f766e;
  font-weight: 700;
}

.top-bar h1,
.panel-head h2 {
  margin: 0;
  color: #14213d;
}

.summary {
  margin: 10px 0 0;
  color: #475569;
  line-height: 1.7;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mode-box,
.chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 999px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.chip {
  color: #166534;
  background: rgba(22, 101, 52, 0.12);
}

.chip.muted {
  color: #9f1239;
  background: rgba(190, 24, 93, 0.12);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.stat-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-label {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}

.stat-card strong {
  font-size: 34px;
  color: #0f172a;
}

.stat-foot {
  color: #64748b;
  font-size: 12px;
}

.layout {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  grid-template-areas:
    'chart control'
    'chart log';
  gap: 16px;
}

.panel {
  padding: 20px 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 18px;
}

.chart-panel {
  grid-area: chart;
}

.control-panel {
  grid-area: control;
}

.log-panel {
  grid-area: log;
}

.trend-chart {
  height: 420px;
}

.control-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.control-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.control-item.active {
  border-color: #99f6e4;
  background: linear-gradient(135deg, #ecfeff 0%, #f0fdf4 100%);
}

.control-name {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
  font-weight: 700;
}

.control-item p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.suggestion-card {
  padding: 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f0fdfa 0%, #f8fafc 100%);
  border: 1px solid rgba(15, 118, 110, 0.18);
}

.suggestion-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-weight: 700;
  color: #0f766e;
}

.suggestion-card p {
  margin: 0 0 12px;
  color: #475569;
  line-height: 1.7;
}

.suggestion-list {
  margin: 0;
  padding-left: 18px;
  color: #0f172a;
}

.suggestion-list li + li {
  margin-top: 8px;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-item {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.log-item strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
}

.log-item p {
  margin: 0 0 8px;
  color: #475569;
  line-height: 1.6;
}

.log-item span {
  color: #64748b;
  font-size: 12px;
}

@media screen and (max-width: 1180px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .layout {
    grid-template-columns: 1fr;
    grid-template-areas:
      'chart'
      'control'
      'log';
  }
}

@media screen and (max-width: 768px) {
  .iot-monitor {
    padding: 14px;
  }

  .top-bar,
  .actions,
  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .trend-chart {
    height: 300px;
  }

  .control-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
