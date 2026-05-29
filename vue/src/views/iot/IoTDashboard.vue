<template>
  <div class="iot-dashboard">
    <section class="hero-panel">
      <div class="hero-copy">
        <p class="hero-kicker">Competition Line</p>
        <h1>设施农业物联网总控驾驶舱</h1>
        <p class="hero-summary">
          聚焦温湿度感知、视觉辅助识别与设备联动处置的比赛版入口。
        </p>
      </div>
      <div class="hero-actions">
        <div class="time-chip">
          <i class="el-icon-time"></i>
          <span>{{ currentTime }}</span>
        </div>
        <el-button type="primary" icon="el-icon-refresh" size="mini" @click="refreshAll">
          刷新数据
        </el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <span class="metric-label">在线设备</span>
        <strong class="metric-value">{{ onlineDeviceCount }}</strong>
        <span class="metric-meta">传感器 / 水泵 / 摄像头链路</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">待处理预警</span>
        <strong class="metric-value warning">{{ pendingAlerts.length }}</strong>
        <span class="metric-meta">来自阈值监测与视觉巡检</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">当前环境</span>
        <strong class="metric-value">{{ environmentSnapshot }}</strong>
        <span class="metric-meta">{{ weatherText }}</span>
      </article>
      <article class="metric-card">
        <span class="metric-label">监测地块</span>
        <strong class="metric-value">{{ farmCount }}</strong>
        <span class="metric-meta">已纳入比赛版总控范围</span>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel card-snapshot">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Live Snapshot</p>
            <h2>设备联动快照</h2>
          </div>
          <span class="status-pill" :class="{ offline: !sensorStatus.online }">
            {{ sensorStatus.online ? '采集在线' : '采集离线' }}
          </span>
        </div>
        <div class="snapshot-grid">
          <div class="snapshot-item">
            <span class="snapshot-name">温度</span>
            <strong>{{ displayTemperature }}</strong>
          </div>
          <div class="snapshot-item">
            <span class="snapshot-name">湿度</span>
            <strong>{{ displayHumidity }}</strong>
          </div>
          <div class="snapshot-item">
            <span class="snapshot-name">补光灯</span>
            <strong>{{ sensorStatus.led ? '开启' : '关闭' }}</strong>
          </div>
          <div class="snapshot-item">
            <span class="snapshot-name">水泵</span>
            <strong>{{ sensorStatus.pump ? '运行中' : '待机' }}</strong>
          </div>
          <div class="snapshot-item">
            <span class="snapshot-name">风扇</span>
            <strong>{{ sensorStatus.fan ? '运行中' : '待机' }}</strong>
          </div>
          <div class="snapshot-item">
            <span class="snapshot-name">水泵链路</span>
            <strong>{{ pumpStatus.online ? '在线' : '离线' }}</strong>
          </div>
        </div>
      </article>

      <article class="panel card-alerts">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Alert Feed</p>
            <h2>今日预警摘要</h2>
          </div>
          <el-button link @click="$router.push('/iot-alert-center')">查看全部</el-button>
        </div>
        <div v-if="pendingAlerts.length" class="alert-list">
          <div
            v-for="alert in pendingAlerts.slice(0, 4)"
            :key="alert.id"
            class="alert-item"
          >
            <span class="alert-level" :class="alert.alertLevel || 'medium'">
              {{ levelLabel(alert.alertLevel) }}
            </span>
            <div class="alert-copy">
              <strong>{{ alert.farmlandName || '监测点位' }}</strong>
              <p>{{ alert.message }}</p>
            </div>
          </div>
        </div>
        <el-empty v-else description="当前没有待处理预警" :image-size="72"></el-empty>
      </article>

      <article class="panel card-vision">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Vision Assist</p>
            <h2>视觉巡检入口</h2>
          </div>
          <span class="camera-pill">{{ cameraStatusText }}</span>
        </div>
        <div class="vision-body">
          <div class="vision-frame">
            <i class="el-icon-video-camera-solid"></i>
          </div>
          <div class="vision-copy">
            <strong>摄像头辅助识别已保留</strong>
            <p>
              保留图像上传、实时流、病害辅助识别三种方式，并支持将结果写入预警闭环中心。
            </p>
            <el-button type="primary" size="mini" @click="$router.push('/iot-vision')">
              进入巡检页
            </el-button>
          </div>
        </div>
      </article>

      <article class="panel card-links">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Main Flow</p>
            <h2>比赛版主线入口</h2>
          </div>
        </div>
        <div class="route-grid">
          <button class="route-card" @click="$router.push('/iot-monitor')">
            <i class="el-icon-cpu"></i>
            <span>设备感知与联动</span>
          </button>
          <button class="route-card" @click="$router.push('/farm-map-gaode')">
            <i class="el-icon-map-location"></i>
            <span>GIS 布点与定位</span>
          </button>
          <button class="route-card" @click="$router.push('/iot-vision')">
            <i class="el-icon-view"></i>
            <span>视觉巡检辅助</span>
          </button>
          <button class="route-card" @click="$router.push('/iot-alert-center')">
            <i class="el-icon-warning-outline"></i>
            <span>预警闭环中心</span>
          </button>
          <button class="route-card secondary" @click="$router.push('/farmmap3d')">
            <i class="el-icon-office-building"></i>
            <span>农场 3D 孪生</span>
          </button>
        </div>
      </article>
    </section>
  </div>
</template>

<script>
export default {
  name: 'IoTDashboard',
  data() {
    return {
      currentTime: '',
      sensorStatus: {
        online: false,
        temperature: null,
        humidity: null,
        led: false,
        fan: false,
        pump: false
      },
      pumpStatus: {
        online: false,
        bump: false
      },
      pendingAlerts: [],
      farmCount: 0,
      weatherText: '等待气象数据',
      cameraStatusText: '摄像头待配置',
      clockTimer: null
    }
  },
  computed: {
    onlineDeviceCount() {
      const devices = [
        this.sensorStatus.online,
        this.pumpStatus.online,
        this.cameraStatusText !== '摄像头待配置'
      ];
      return devices.filter(Boolean).length;
    },
    displayTemperature() {
      if (this.sensorStatus.temperature === null || this.sensorStatus.temperature === undefined) {
        return '--';
      }
      return `${Number(this.sensorStatus.temperature).toFixed(1)}℃`;
    },
    displayHumidity() {
      if (this.sensorStatus.humidity === null || this.sensorStatus.humidity === undefined) {
        return '--';
      }
      return `${Number(this.sensorStatus.humidity).toFixed(1)}%`;
    },
    environmentSnapshot() {
      if (this.sensorStatus.temperature === null || this.sensorStatus.humidity === null) {
        return '待同步';
      }
      return `${Math.round(this.sensorStatus.temperature)}℃ / ${Math.round(this.sensorStatus.humidity)}%`;
    }
  },
  mounted() {
    this.updateClock();
    this.clockTimer = setInterval(() => this.updateClock(), 1000);
    this.refreshAll();
    this.restoreCameraStatus();
  },
  beforeUnmount() {
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
    }
  },
  methods: {
    updateClock() {
      this.currentTime = new Date().toLocaleString('zh-CN', { hour12: false });
    },
    restoreCameraStatus() {
      const savedUrl = localStorage.getItem('iot_camera_url');
      if (savedUrl) {
        this.cameraStatusText = '摄像头流已配置';
      }
    },
    levelLabel(level) {
      const map = {
        high: '高风险',
        medium: '中风险',
        low: '提示'
      };
      return map[level] || '提示';
    },
    async refreshAll() {
      await Promise.allSettled([
        this.fetchDeviceStatus(),
        this.fetchAlerts(),
        this.fetchFarmCount(),
        this.fetchWeather()
      ]);
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
          humidity: sensorRes.data.humidity,
          led: sensorRes.data.led === 1 || sensorRes.data.led === true,
          fan: !!sensorRes.data.fan,
          pump: !!sensorRes.data.pump
        };
      }

      if (pumpRes.code === '200' && pumpRes.data) {
        this.pumpStatus = {
          online: pumpRes.data.online === true,
          bump: !!pumpRes.data.bump
        };
        this.sensorStatus.pump = !!pumpRes.data.bump || this.sensorStatus.pump;
      }
    },
    async fetchAlerts() {
      const res = await this.request.get('/alert/pending');
      if (res.code === '200' && Array.isArray(res.data)) {
        this.pendingAlerts = res.data;
      } else {
        this.pendingAlerts = [];
      }
    },
    async fetchFarmCount() {
      const res = await this.request.get('/statistic/page', {
        params: {
          pageNum: 1,
          pageSize: 1000
        }
      });
      if (res.code === '200' && res.data) {
        this.farmCount = (res.data.records || []).length;
      }
    },
    async fetchWeather() {
      const res = await this.request.get('/aether/weather/now');
      if (res.code === '200' && res.data && res.data.data) {
        const weather = res.data.data;
        this.weatherText = `${weather.text || '天气稳定'} / ${weather.temp || '--'}℃`;
      }
    }
  }
}
</script>

<style scoped>
.iot-dashboard {
  min-height: calc(100vh - 60px);
  padding: 20px;
  background:
    radial-gradient(circle at top right, rgba(11, 135, 87, 0.12), transparent 24%),
    linear-gradient(180deg, #f4f8f4 0%, #eef3f6 100%);
}

.hero-panel,
.metric-card,
.panel {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(203, 213, 225, 0.72);
  border-radius: 22px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
  margin-bottom: 18px;
}

.hero-kicker,
.panel-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #0f766e;
}

.hero-copy h1,
.panel-head h2 {
  margin: 0;
  color: #14213d;
}

.hero-copy h1 {
  font-size: 34px;
  line-height: 1.1;
}

.hero-summary {
  max-width: 680px;
  margin: 12px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: #475569;
}

.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.time-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  background: #0f172a;
  color: #f8fafc;
  font-size: 13px;
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
  font-size: 34px;
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
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.panel {
  padding: 20px 22px;
}

.card-snapshot,
.card-alerts {
  min-height: 300px;
}

.card-links,
.card-vision {
  min-height: 260px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
}

.status-pill,
.camera-pill {
  display: inline-flex;
  align-items: center;
  padding: 7px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-pill {
  color: #166534;
  background: rgba(22, 101, 52, 0.12);
}

.status-pill.offline {
  color: #b91c1c;
  background: rgba(185, 28, 28, 0.12);
}

.camera-pill {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.snapshot-item {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fafc 0%, #eff6ff 100%);
  border: 1px solid #e2e8f0;
}

.snapshot-name {
  display: block;
  margin-bottom: 10px;
  color: #475569;
  font-size: 12px;
}

.snapshot-item strong {
  font-size: 18px;
  color: #0f172a;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.alert-level {
  display: inline-flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 700;
  color: #92400e;
  background: #fef3c7;
}

.alert-level.high {
  color: #991b1b;
  background: #fee2e2;
}

.alert-level.medium {
  color: #9a3412;
  background: #ffedd5;
}

.alert-copy strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
}

.alert-copy p {
  margin: 0;
  color: #475569;
  line-height: 1.6;
}

.vision-body {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 18px;
  align-items: center;
}

.vision-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  border-radius: 24px;
  background: linear-gradient(135deg, #0f172a 0%, #164e63 100%);
  color: #f8fafc;
  font-size: 40px;
}

.vision-copy strong {
  display: block;
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 18px;
}

.vision-copy p {
  margin: 0 0 14px;
  color: #475569;
  line-height: 1.7;
}

.route-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.route-card {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 0;
  border-radius: 16px;
  padding: 16px 18px;
  background: linear-gradient(135deg, #ebf8ef 0%, #f8fafc 100%);
  border: 1px solid #d9f99d;
  color: #14532d;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  text-align: left;
}

.route-card.secondary {
  background: linear-gradient(135deg, #eff6ff 0%, #f8fafc 100%);
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.route-card i {
  font-size: 18px;
}

@media screen and (max-width: 1180px) {
  .metric-grid,
  .content-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .snapshot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media screen and (max-width: 768px) {
  .iot-dashboard {
    padding: 14px;
  }

  .hero-panel,
  .metric-grid,
  .content-grid,
  .route-grid,
  .snapshot-grid,
  .vision-body {
    grid-template-columns: 1fr;
  }

  .hero-panel,
  .hero-actions,
  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .vision-frame {
    width: 100%;
  }
}
</style>
