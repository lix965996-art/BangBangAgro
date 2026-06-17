<template>
  <div class="eco-monitor">
    <div class="page-header">
      <div class="header-content">
        <div class="title-section">
          <div class="title-bar"></div>
          <div>
            <h1 class="page-title">🌱 微气候精准调控舱</h1>
            <p class="page-subtitle">智慧农业环境监测与自动化控制系统</p>
          </div>
        </div>
        <div class="status-badge" :class="{online: deviceOnline}">
          <span class="status-dot"></span>
          <span>{{ deviceOnline ? '设备在线' : '设备离线' }}</span>
        </div>
      </div>
    </div>

    <el-row :gutter="16" class="metrics-row">
      <el-col :span="6">
        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-icon">🌡️</span>
            <span class="metric-label">室外温度</span>
            <span class="status-indicator normal"></span>
          </div>
          <div class="metric-value">
            {{ weatherNow && weatherNow.temp ? weatherNow.temp : '--' }}<span class="unit">℃</span>
          </div>
          <div class="metric-trend">
            <canvas ref="weatherSparkline" width="200" height="24"></canvas>
          </div>
          <div class="metric-desc">{{ weatherNow && weatherNow.text || '加载中...' }}</div>
        </div>
      </el-col>

      <el-col :span="6">
        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-icon">🌡️</span>
            <span class="metric-label">室内温度</span>
            <span class="status-indicator" :class="getStatusClass('temp')"></span>
          </div>
          <div class="metric-value">
            {{ temperature === null ? '--' : temperature }}<span class="unit">℃</span>
          </div>
          <div class="metric-trend">
            <canvas ref="tempSparkline" width="200" height="24"></canvas>
          </div>
          <div class="metric-desc">实时传感器监测<span v-if="sensorSourceLabel" class="source-hint">{{ sensorSourceLabel }}</span></div>
        </div>
      </el-col>

      <el-col :span="6">
        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-icon">💧</span>
            <span class="metric-label">室内湿度</span>
            <span class="status-indicator" :class="getStatusClass('humi')"></span>
          </div>
          <div class="metric-value">
            {{ humidity === null ? '--' : humidity }}<span class="unit">%</span>
          </div>
          <div class="metric-trend">
            <canvas ref="humiSparkline" width="200" height="24"></canvas>
          </div>
          <div class="metric-desc">实时传感器监测<span v-if="sensorSourceLabel" class="source-hint">{{ sensorSourceLabel }}</span></div>
        </div>
      </el-col>

      <el-col :span="6">
        <div class="metric-card metric-card--light">
          <div class="metric-header">
            <span class="metric-icon">💡</span>
            <span class="metric-label">灯照开关</span>
            <span class="status-indicator" :class="ledOn ? 'normal' : 'inactive'"></span>
          </div>
          <div class="light-switch-body">
            <el-switch
              v-model="ledOn"
              active-text="开"
              inactive-text="关"
              active-color="#10b981"
              inactive-color="#cbd5e1"
              :disabled="!deviceOnline || ledSwitchLoading"
              @change="onLedSwitchChange"
            />
          </div>
          <div class="metric-desc">
            <template v-if="!deviceOnline">设备离线，无法控制</template>
            <template v-else-if="ledSwitchLoading">正在下发指令…</template>
            <template v-else>{{ ledOn ? '补光灯已开启（IoT）' : '补光灯已关闭' }}</template>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="metrics-row device-row">
      <el-col :span="8">
        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-icon">🌾</span>
            <span class="metric-label">土壤湿度</span>
            <span class="status-indicator" :class="soil !== null && soil <= soilThresholdLow ? 'warning' : 'normal'"></span>
          </div>
          <div class="metric-value">
            {{ soil === null ? '--' : soil }}<span class="unit">%</span>
          </div>
          <div class="metric-desc">
            土壤传感器（PA4）·
            <template v-if="soil === null">等待上报</template>
            <template v-else-if="soil <= soilThresholdLow">偏干，注意灌溉</template>
            <template v-else>湿度正常</template>
          </div>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-icon">🌀</span>
            <span class="metric-label">风扇状态</span>
            <span class="status-indicator" :class="fanOn ? 'normal' : 'inactive'"></span>
          </div>
          <div class="metric-value status-text" :class="{ on: fanOn }">
            {{ fanOn ? '运行中' : '已停止' }}
          </div>
          <div class="metric-desc">设备按温度自动调控（≥30℃ 开 / ≤28℃ 关）</div>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="metric-card" :class="{ 'metric-card--alarm': buzzerOn }">
          <div class="metric-header">
            <span class="metric-icon">🚨</span>
            <span class="metric-label">报警器</span>
            <span class="status-indicator" :class="buzzerOn ? 'warning' : 'inactive'"></span>
          </div>
          <div class="metric-value status-text" :class="{ alarm: buzzerOn }">
            {{ buzzerOn ? '报警中' : '正常' }}
          </div>
          <div class="metric-desc">
            <template v-if="buzzerOn">{{ buzzerAlert }}</template>
            <template v-else>高温(≥35℃) 或 土壤过干(≤25%) 自动鸣响</template>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="weather-row" style="max-height: 240px;">
      <el-col :span="16">
        <el-card class="weather-forecast-card" style="max-height: 240px; overflow: hidden;">
          <template #header>
            <div class="card-header">
              <img src="@/assets/yubao.png" class="header-icon" alt="天气预报" /><span>天气预报</span>
              <div class="segment-control">
                <button :class="['segment-btn', { active: activeWeatherTab === '24h' }]" @click="activeWeatherTab = '24h'">24小时</button>
                <button :class="['segment-btn', { active: activeWeatherTab === '7d' }]" @click="activeWeatherTab = '7d'">4天</button>
              </div>
            </div>
          </template>
          
          <div class="forecast-rail" v-if="activeWeatherTab === '24h'">
            <template v-if="weather24h.length > 0">
              <button class="scroll-btn scroll-left" @click="scrollForecast(-200)">‹</button>
              <div class="forecast-scroll-wrapper">
                <div class="forecast-scroll" ref="forecastScroll24h">
                  
                  <svg class="temp-trend-svg" :width="weather24h.length * 64" height="150" preserveAspectRatio="none">
                    <defs>
                      <linearGradient id="lineGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                        <stop offset="0%" style="stop-color:rgba(16,185,129,0.4)"/>
                        <stop offset="50%" style="stop-color:rgba(16,185,129,1)"/>
                        <stop offset="100%" style="stop-color:rgba(16,185,129,0.4)"/>
                      </linearGradient>
                    </defs>
                    <path :d="getTrendPath()" fill="none" stroke="url(#lineGradient)" stroke-width="3" stroke-linecap="round"/>
                    
                    <g v-for="(item, index) in weather24h" :key="'svg-'+index">
                      <circle :cx="index * 64 + 32" :cy="getTempY(item.temp)" r="4" fill="#ffffff" stroke="#10b981" stroke-width="2"/>
                      <text :x="index * 64 + 32" :y="getTempY(item.temp) - 10" text-anchor="middle" fill="#059669" font-size="13" font-weight="bold" font-family="sans-serif">{{item.temp}}°</text>
                    </g>
                  </svg>

                  <div class="forecast-column" v-for="(item, index) in weather24h" :key="'col-'+index">
                    <div class="col-time">{{ formatTime(item.fxTime) }}</div>
                    <div class="col-icon">{{ getWeatherIcon(item.text, item.fxTime) }}</div>
                    <div class="col-spacer"></div>
                    <div class="col-wind">{{ item.windDir || '微风' }}</div>
                  </div>
                  
                </div>
              </div>
              <button class="scroll-btn scroll-right" @click="scrollForecast(200)">›</button>
            </template>
            <el-empty v-else description="暂无数据" :image-size="60"></el-empty>
          </div>

          <div class="forecast-rail" v-else>
            <div v-if="weather7d.length > 0" class="forecast-scroll forecast-scroll-7d">
              <div class="forecast-7d-col" v-for="(item, index) in weather7d" :key="'d-'+index">
                <div class="col-time">{{ formatDate(item.fxDate) }}</div>
                <div class="col-icon" style="margin-bottom: 8px;">{{ getWeatherIcon(item.textDay) }}</div>
                <div class="col-temp-range">{{ item.tempMin }}°<span class="temp-divider">/</span>{{ item.tempMax }}°</div>
              </div>
            </div>
            <el-empty v-else description="暂无数据" :image-size="60"></el-empty>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="alert-card" style="max-height: 240px; overflow: hidden;">
          <template #header>
            <div class="card-header">
              <span>预警信息</span>
            </div>
          </template>
          <div class="alert-list">
            <div v-if="weatherAlerts.length > 0">
              <div v-for="(alert, index) in weatherAlerts" :key="'weather-' + index" class="alert-item warning">
                <i class="el-icon-warning"></i>
                <div class="alert-content">
                  <div class="alert-title">{{ alert.title }}</div>
                  <div class="alert-desc">{{ alert.text }}</div>
                </div>
              </div>
            </div>
            <div v-if="tempAlert" class="alert-item danger">
              <i class="el-icon-warning"></i>
              <div class="alert-content">
                <div class="alert-title">温度异常</div>
                <div class="alert-desc">{{ tempAlert }}</div>
              </div>
            </div>
            <div v-if="humiAlert" class="alert-item warning">
              <i class="el-icon-warning"></i>
              <div class="alert-content">
                <div class="alert-title">湿度异常</div>
                <div class="alert-desc">{{ humiAlert }}</div>
              </div>
            </div>
            <div v-if="buzzerOn" class="alert-item danger">
              <i class="el-icon-warning"></i>
              <div class="alert-content">
                <div class="alert-title">🚨 现场报警器触发</div>
                <div class="alert-desc">{{ buzzerAlert || '硬件报警器正在鸣响' }}</div>
              </div>
            </div>
            <el-empty v-if="!weatherAlerts.length && !tempAlert && !humiAlert && !buzzerOn" description="系统运转平稳" :image-size="60"></el-empty>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <div class="glass-panel">
          <div class="panel-header">
            <div class="title-bar"></div>
            <div>
              <h3 class="panel-title">环境参数趋势与AI预测</h3>
              <p class="panel-subtitle">历史数据 + 未来4小时智能预测</p>
            </div>
          </div>
          <div ref="trendChart" style="height: 280px;"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="glass-panel">
          <div class="panel-header">
            <div class="title-bar"></div>
            <h3 class="panel-title">智能告警中心</h3>
          </div>
          <div class="alert-timeline">
            <div v-if="tempAlert || humiAlert || buzzerOn || weatherAlerts.length" class="timeline-items">
              <div v-if="buzzerOn" class="timeline-item danger">
                <div class="timeline-dot"></div>
                <div class="timeline-content">
                  <div class="timeline-time">{{ getCurrentTime() }}</div>
                  <div class="timeline-title">🚨 报警器触发</div>
                  <div class="timeline-desc">{{ buzzerAlert || '硬件报警器正在鸣响' }}</div>
                </div>
              </div>
              <div v-if="tempAlert" class="timeline-item warning">
                <div class="timeline-dot"></div>
                <div class="timeline-content">
                  <div class="timeline-time">{{ getCurrentTime() }}</div>
                  <div class="timeline-title">温度异常</div>
                  <div class="timeline-desc">{{ tempAlert }}</div>
                </div>
              </div>
              <div v-if="humiAlert" class="timeline-item warning">
                <div class="timeline-dot"></div>
                <div class="timeline-content">
                  <div class="timeline-time">{{ getCurrentTime() }}</div>
                  <div class="timeline-title">💧 湿度异常</div>
                  <div class="timeline-desc">{{ humiAlert }}</div>
                </div>
              </div>
              <div v-for="(alert, index) in weatherAlerts" :key="index" class="timeline-item danger">
                <div class="timeline-dot"></div>
                <div class="timeline-content">
                  <div class="timeline-time">{{ getCurrentTime() }}</div>
                  <div class="timeline-title">{{ alert.title }}</div>
                  <div class="timeline-desc">{{ alert.text }}</div>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">
              <div class="empty-icon"></div>
              <div class="empty-text">系统运行正常</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'AetherMonitor',
  data() {
    return {
      deviceOnline: false,
      temperature: null,
      humidity: null,
      soil: null,
      fanOn: false,
      buzzerOn: false,
      soilThresholdLow: 25,
      buzzerAlert: '',
      ledOn: false,
      weatherNow: null,
      weather24h: [],
      weather7d: [],
      weatherAlerts: [],
      activeWeatherTab: '24h',
      tempAlert: '',
      humiAlert: '',
      tempThresholdHigh: 30,
      tempThresholdLow: 10,
      humiThresholdHigh: 75,
      humiThresholdLow: 25,
      historyData: [],
      tempChartInstance: null,
      humiChartInstance: null,
      pollingTimer: null,
      ledSwitchLoading: false,
      historyTimer: null,
      weatherTimer: null,
      /** 后端 /aether/device/status 的 source：OneNET / Database / Cache */
      sensorSource: ''
    }
  },
  computed: {
    sensorSourceLabel() {
      if (!this.sensorSource || this.sensorSource === 'OneNET') return '';
      const map = { Database: '缓存库', Cache: '内存缓存' };
      const name = map[this.sensorSource] || this.sensorSource;
      return `（当前来源：${name}，非 OneNET 直连）`;
    }
  },
  mounted() {
    this.initCharts();
    this.fetchDeviceStatus();
    this.fetchWeatherData();
    this.fetchHistoryData();

    this.pollingTimer = setInterval(() => { this.fetchDeviceStatus(); }, 5000);
    this.historyTimer = setInterval(() => { this.fetchHistoryData(); }, 60000);
    this.weatherTimer = setInterval(() => { this.fetchWeatherData(); }, 300000);
  },
  beforeUnmount() {
    if (this.pollingTimer) clearInterval(this.pollingTimer);
    if (this.historyTimer) clearInterval(this.historyTimer);
    if (this.weatherTimer) clearInterval(this.weatherTimer);
    if (this.tempChartInstance && !this.tempChartInstance.isDisposed()) this.tempChartInstance.dispose();
    if (this.humiChartInstance && !this.humiChartInstance.isDisposed()) this.humiChartInstance.dispose();
    if (this.chartResizeHandler) window.removeEventListener('resize', this.chartResizeHandler);
  },
  methods: {
    scrollForecast(delta) {
      const el = this.$refs.forecastScroll24h;
      if (el) el.scrollBy({ left: delta, behavior: 'smooth' });
    },
    getTempY(temp) {
      if (!this.weather24h || this.weather24h.length === 0) return 85;
      const temps = this.weather24h.map(item => parseInt(item.temp));
      const minTemp = Math.min(...temps);
      const maxTemp = Math.max(...temps);
      const range = maxTemp - minTemp || 1;
      return 100 - ((parseInt(temp) - minTemp) / range) * 30;
    },
    getTrendPath() {
      if (!this.weather24h || this.weather24h.length < 2) return '';
      const points = this.weather24h.map((item, index) => ({ x: index * 64 + 32, y: this.getTempY(item.temp) }));
      let path = `M ${points[0].x} ${points[0].y}`;
      for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[Math.max(0, i - 1)];
        const p1 = points[i];
        const p2 = points[i + 1];
        const p3 = points[Math.min(points.length - 1, i + 2)];
        const cp1x = p1.x + (p2.x - p0.x) / 6;
        const cp1y = p1.y + (p2.y - p0.y) / 6;
        const cp2x = p2.x - (p3.x - p1.x) / 6;
        const cp2y = p2.y - (p3.y - p1.y) / 6;
        path += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`;
      }
      return path;
    },
    async fetchDeviceStatus() {
      try {
        const res = await this.request.get('/aether/device/status');
        if (res.code === '200' && res.data) {
          const data = res.data;
          this.sensorSource = data.source || '';
          this.deviceOnline = data.online;
          this.temperature = data.temperature;
          this.humidity = data.humidity;
          this.soil = (data.soil === undefined || data.soil === null) ? null : data.soil;
          this.fanOn = data.fan === true || data.fan === 1;
          this.buzzerOn = data.buzzer === true || data.buzzer === 1;
          this.ledOn = data.led === 1;
          this.checkThresholds();
        }
      } catch (error) {
        this.deviceOnline = false;
      }
    },
    async fetchWeatherData() {
      try {
        const nowRes = await this.request.get('/aether/weather/now');
        if (nowRes.code === '200' && nowRes.data && nowRes.data.data) {
          this.weatherNow = nowRes.data.data;
        }
        try {
          const hourlyRes = await this.request.get('/aether/weather/24h');
          if (hourlyRes.code === '200' && hourlyRes.data && hourlyRes.data.data) {
            this.weather24h = hourlyRes.data.data || [];
          }
        } catch (e) {}
        const weekRes = await this.request.get('/aether/weather/7d');
        if (weekRes.code === '200' && weekRes.data && weekRes.data.data) {
          this.weather7d = weekRes.data.data.daily || weekRes.data.data || [];
          if (!this.weather24h || this.weather24h.length === 0) {
            this.weather24h = weekRes.data.data.hourly || [];
          }
        }
        const alertRes = await this.request.get('/aether/weather/alerts');
        if (alertRes.code === '200' && alertRes.data) {
          this.weatherAlerts = alertRes.data.data || [];
        }
      } catch (error) {}
    },
    async fetchHistoryData() {
      try {
        const res = await this.request.get('/aether/readings/detail?days=30');
        if (res.code === '200' && res.data && res.data.data) {
          this.historyData = res.data.data || [];
          this.updateCharts();
        }
      } catch (error) {}
    },
    checkThresholds() {
      this.tempAlert = '';
      this.humiAlert = '';
      // 硬件报警器：由单片机按高温(≥35℃)或土壤过干(≤25%)自动触发，这里只做原因解释
      if (this.buzzerOn) {
        const reasons = [];
        if (this.temperature !== null && this.temperature >= 35) reasons.push(`高温 ${this.temperature}℃`);
        if (this.soil !== null && this.soil <= this.soilThresholdLow) reasons.push(`土壤过干 ${this.soil}%`);
        this.buzzerAlert = reasons.length ? `触发原因：${reasons.join('、')}` : '现场报警器正在鸣响';
      } else {
        this.buzzerAlert = '';
      }
      if (this.temperature !== null) {
        if (this.temperature > this.tempThresholdHigh) {
          this.tempAlert = `当前温度 ${this.temperature}℃ 超过上限 ${this.tempThresholdHigh}℃`;
        } else if (this.temperature < this.tempThresholdLow) {
          this.tempAlert = `当前温度 ${this.temperature}℃ 低于下限 ${this.tempThresholdLow}℃`;
        }
      }
      if (this.humidity !== null) {
        if (this.humidity > this.humiThresholdHigh) {
          this.humiAlert = `当前湿度 ${this.humidity}% 超过上限 ${this.humiThresholdHigh}%`;
        } else if (this.humidity < this.humiThresholdLow) {
          this.humiAlert = `当前湿度 ${this.humidity}% 低于下限 ${this.humiThresholdLow}%`;
        }
      }
    },
    initCharts() {
      this.$nextTick(() => {
        if (!this.$refs.trendChart) return;
        this.trendChartInstance = echarts.init(this.$refs.trendChart);
        this.chartResizeHandler = () => {
          if (this.trendChartInstance && !this.trendChartInstance.isDisposed()) {
            this.trendChartInstance.resize();
          }
        };
        window.addEventListener('resize', this.chartResizeHandler);
      });
    },
    updateCharts() {
      if (!this.historyData || this.historyData.length === 0) return;
      const sortedData = [...this.historyData].sort((a, b) => new Date(a.date) - new Date(b.date));
      const recentData = sortedData.slice(-20);
      const times = recentData.map(item => {
        const date = new Date(item.date);
        return `${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`;
      });
      const temps = recentData.map((item, idx) => {
        const value = Number(item.temp);
        if (Number.isFinite(value)) return value;
        if (idx > 0) {
          const prev = Number(recentData[idx - 1].temp);
          if (Number.isFinite(prev)) return prev;
        }
        return this.temperature !== null ? Number(this.temperature) : 0;
      });
      const predictTimes = ['1h', '2h', '3h', '4h'];
      const lastTemp = temps[temps.length - 1];
      const trendWindow = temps.slice(-6);
      const diffs = trendWindow.slice(1).map((value, index) => value - trendWindow[index]);
      const avgSlope = diffs.length
        ? Math.max(-2, Math.min(2, diffs.reduce((sum, value) => sum + value, 0) / diffs.length))
        : 0;
      const predictTemps = Array.from({ length: 4 }, (_, idx) =>
        Number((lastTemp + avgSlope * (idx + 1)).toFixed(1))
      );
      const volatility = diffs.length
        ? Math.max(0.6, Math.min(2.5, diffs.reduce((sum, value) => sum + Math.abs(value), 0) / diffs.length))
        : 1;
      const upperBound = predictTemps.map(t => Number((t + volatility).toFixed(1)));
      const lowerBound = predictTemps.map(t => Number((t - volatility).toFixed(1)));
      
      this.drawSparkline('tempSparkline', temps, '#059669');
      const humis = recentData.map((item, idx) => {
        const value = Number(item.humi);
        if (Number.isFinite(value)) return value;
        if (idx > 0) {
          const prev = Number(recentData[idx - 1].humi);
          if (Number.isFinite(prev)) return prev;
        }
        return this.humidity !== null ? Number(this.humidity) : 0;
      });
      this.drawSparkline('humiSparkline', humis, '#3b82f6');
      
      const option = {
        tooltip: { trigger: 'axis', backgroundColor: 'rgba(255, 255, 255, 0.95)', borderColor: '#e5e7eb', borderWidth: 1, textStyle: { color: '#374151' } },
        legend: { data: ['历史数据', 'AI预测', '置信区间'], bottom: 10, textStyle: { color: '#6b7280' } },
        grid: { left: '3%', right: '3%', top: '5%', bottom: '15%', containLabel: true },
        xAxis: { type: 'category', data: [...times, ...predictTimes], boundaryGap: false, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280', fontSize: 11 } },
        yAxis: { type: 'value', name: '温度(℃)', nameTextStyle: { color: '#6b7280' }, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280' }, splitLine: { lineStyle: { color: '#f3f4f6' } } },
        series: [
          { name: '适宜范围', type: 'line', markArea: { silent: true, itemStyle: { color: 'rgba(16, 185, 129, 0.08)' }, data: [[{ yAxis: 20 }, { yAxis: 28 }]] }, data: [] },
          { name: '历史数据', type: 'line', smooth: true, data: [...temps, ...Array(4).fill(null)], itemStyle: { color: '#059669' }, lineStyle: { width: 3 }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(5, 150, 105, 0.3)' }, { offset: 1, color: 'rgba(5, 150, 105, 0.05)' }] } } },
          { name: 'AI预测', type: 'line', smooth: true, data: [...Array(temps.length).fill(null), ...predictTemps], itemStyle: { color: '#f59e0b' }, lineStyle: { width: 2, type: 'dashed', dashOffset: 5 } },
          { name: '置信区间', type: 'line', data: [...Array(temps.length).fill(null), ...upperBound], lineStyle: { opacity: 0 }, stack: 'confidence', symbol: 'none' },
          { name: '置信区间', type: 'line', data: [...Array(temps.length).fill(null), ...lowerBound.map((v, i) => upperBound[i] - v)], lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(245, 158, 11, 0.15)' }, stack: 'confidence', symbol: 'none' }
        ]
      };
      if (this.trendChartInstance && !this.trendChartInstance.isDisposed()) this.trendChartInstance.setOption(option);
    },
    formatTime(timeStr) {
      const date = new Date(timeStr);
      return `${date.getHours()}:00`;
    },
    formatDate(dateStr) {
      const date = new Date(dateStr);
      return `${date.getMonth() + 1}/${date.getDate()}`;
    },
    getCurrentTime() {
      const now = new Date();
      return `${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}`;
    },
    drawSparkline(canvasRef, data, color = '#10b981') {
      const canvas = this.$refs[canvasRef];
      if (!canvas || !data || data.length === 0) return;
      const ctx = canvas.getContext('2d');
      const width = canvas.width;
      const height = canvas.height;
      const padding = 2; // 减少 padding
      ctx.clearRect(0, 0, width, height);
      const max = Math.max(...data);
      const min = Math.min(...data);
      const range = max - min || 1;
      
      const gradient = ctx.createLinearGradient(0, 0, 0, height);
      gradient.addColorStop(0, color + '40');
      gradient.addColorStop(1, color + '05');
      
      ctx.beginPath();
      data.forEach((value, index) => {
        const x = padding + (index / (data.length - 1)) * (width - padding * 2);
        const y = height - padding - ((value - min) / range) * (height - padding * 2);
        if (index === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.lineTo(width - padding, height - padding);
      ctx.lineTo(padding, height - padding);
      ctx.closePath();
      ctx.fillStyle = gradient;
      ctx.fill();
      
      ctx.beginPath();
      data.forEach((value, index) => {
        const x = padding + (index / (data.length - 1)) * (width - padding * 2);
        const y = height - padding - ((value - min) / range) * (height - padding * 2);
        if (index === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.stroke();
    },

    async onLedSwitchChange(on) {
      if (!this.deviceOnline) {
        this.ledOn = false;
        return;
      }
      const prev = !on;
      this.ledSwitchLoading = true;
      try {
        const res = await this.request.post('/aether/device/control/led', { led: on ? 1 : 0 });
        const ok = res && (res.code === '200' || res.code === 200);
        if (!ok) {
          throw new Error((res && res.msg) || '控制失败');
        }
        const msg = (res.data && res.data.message) || (on ? '补光灯已开启' : '补光灯已关闭');
        if (this.$message) this.$message.success(msg);
      } catch (e) {
        this.ledOn = prev;
        const err = (e && e.response && e.response.data && e.response.data.msg) || (e && e.message) || '控制失败';
        if (this.$message) this.$message.error(err);
      } finally {
        this.ledSwitchLoading = false;
      }
    },
    getWeatherIcon(text, timeStr) {
      let isNight = false;
      if (timeStr) {
        const hour = new Date(timeStr).getHours();
        isNight = hour >= 18 || hour < 6;
      }
      const iconMap = { '晴': isNight ? '🌙' : '☀️', '多云': isNight ? '☁️' : '⛅', '阴': '☁️', '雨': '🌧️', '雪': '❄️', '雷': '⚡' };
      for (let key in iconMap) {
        if (text && text.includes(key)) return iconMap[key];
      }
      return isNight ? '🌙' : '🌤️';
    },
    getStatusClass(type) {
      if (type === 'temp' && this.temperature !== null) {
        if (this.temperature > this.tempThresholdHigh || this.temperature < this.tempThresholdLow) return 'warning';
      }
      if (type === 'humi' && this.humidity !== null) {
        if (this.humidity > this.humiThresholdHigh || this.humidity < this.humiThresholdLow) return 'warning';
      }
      return 'normal';
    }
  }
}
</script>

<style scoped>
/* 🌱 现代生态科技风 - 恢复基础底色 */
.eco-monitor {
  padding: 20px;
  min-height: 100vh;
  background: #f8fafc;
  overflow-y: auto;
  scrollbar-width: none;
}
.eco-monitor::-webkit-scrollbar { display: none; }

/* --- 顶部与公共卡片 --- */
.page-header { margin-bottom: 24px; }
.header-content {
  background: white; border-radius: 16px; padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); display: flex; justify-content: space-between; align-items: center;
}
.title-section { display: flex; gap: 16px; align-items: center; }
.title-bar { width: 4px; height: 48px; background: linear-gradient(to bottom, #10b981, #059669); border-radius: 2px; }
.page-title { font-size: 24px; font-weight: 700; color: #1f2937; margin: 0 0 4px 0; }
.page-subtitle { font-size: 13px; color: #6b7280; margin: 0; }

.status-badge {
  display: flex; align-items: center; gap: 8px; padding: 8px 16px; border-radius: 20px;
  background: #fee2e2; color: #991b1b; font-size: 13px; font-weight: 600;
}
.status-badge.online { background: #d1fae5; color: #065f46; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #ef4444; animation: pulse 2s infinite; }
.status-badge.online .status-dot { background: #10b981; }

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

/* ✨ 修复点1：锁定指标卡片的统一高度 ✨ */
.metrics-row { margin-bottom: 24px; }
.metric-card {
  background: white; 
  border-radius: 16px; 
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); 
  border: 1px solid #f1f5f9;
  height: 130px; /* 强制统一高度 */
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-sizing: border-box;
}

.metric-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.metric-icon { font-size: 18px; }
.metric-label { flex: 1; font-size: 13px; color: #6b7280; font-weight: 600; }

.status-indicator { width: 8px; height: 8px; border-radius: 50%; background: #10b981; }
.status-indicator.inactive { background: #9ca3af; animation: none; }
.status-indicator.warning { background: #f59e0b; animation: warningPulse 2s infinite; }

@keyframes warningPulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
  50% { opacity: 0.8; box-shadow: 0 0 0 6px rgba(245, 158, 11, 0); }
}

.metric-value { font-size: 28px; font-weight: 800; color: #059669; line-height: 1; margin-bottom: 2px;}
.metric-value .unit { font-size: 16px; color: #6b7280; font-weight: 500; margin-left: 4px; }
.metric-trend { height: 24px; opacity: 0.6; }
.metric-desc { font-size: 12px; color: #9ca3af; }
.source-hint { color: #f59e0b; font-size: 11px; margin-left: 4px; }

.light-switch-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
}
.metric-card--light .metric-desc { min-height: 32px; }

/* 设备状态行（土壤 / 风扇 / 报警器） */
.device-row { margin-top: -8px; }
.status-text { font-size: 22px; font-weight: 800; color: #6b7280; }
.status-text.on { color: #059669; }
.status-text.alarm { color: #ef4444; }
.metric-card--alarm {
  border-color: #fecaca !important;
  background: #fef2f2;
  animation: alarmPulse 1.5s infinite;
}
@keyframes alarmPulse {
  0%, 100% { box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05); }
  50% { box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.15); }
}

.weather-row { margin-bottom: 4px; display: flex; align-items: stretch; }
.weather-row .el-col { display: flex; }
.weather-row .el-card { flex: 1; }

.glass-panel {
  background: white; border: 1px solid #f1f5f9; border-radius: 16px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}
.panel-header { display: flex; gap: 16px; align-items: flex-start; margin-bottom: 20px; }
.panel-title { font-size: 16px; font-weight: 700; color: #1f2937; margin: 0 0 4px 0; }
.panel-subtitle { font-size: 12px; color: #6b7280; margin: 0; }

.alert-timeline { max-height: 280px; overflow-y: auto; }
.timeline-items { display: flex; flex-direction: column; gap: 16px; }
.timeline-item { position: relative; padding-left: 24px; padding-bottom: 16px; border-left: 2px solid #e5e7eb; }
.timeline-item:last-child { border-left-color: transparent; }
.timeline-dot {
  position: absolute; left: -5px; top: 4px; width: 8px; height: 8px;
  border-radius: 50%; background: #10b981; border: 2px solid white;
}
.timeline-item.warning .timeline-dot { background: #f59e0b; }
.timeline-item.danger .timeline-dot { background: #ef4444; }
.timeline-time { font-size: 11px; color: #9ca3af; margin-bottom: 4px; }
.timeline-title { font-size: 13px; font-weight: 600; color: #374151; margin-bottom: 4px; }
.timeline-desc { font-size: 12px; color: #6b7280; }

.empty-state { text-align: center; padding: 40px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; color: #6b7280; }

.chart-row { margin-bottom: 24px; }
.card-header { font-weight: 600; font-size: 14px; color: #1F2937; display: flex; align-items: center; gap: 6px; }
.header-icon { width: 20px; height: 20px; object-fit: contain; }

.segment-control {
  margin-left: auto; display: flex; background: #F3F4F6; border-radius: 20px; padding: 3px; gap: 2px;
}
.segment-btn {
  padding: 5px 14px; border: none; background: transparent; border-radius: 16px;
  font-size: 12px; font-weight: 500; color: #6B7280; cursor: pointer; transition: all 0.2s ease;
}
.segment-btn.active {
  background: #FFFFFF; color: #059669; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); font-weight: 600;
}

.forecast-rail { position: relative; display: flex; align-items: center; gap: 8px; }
.forecast-scroll-wrapper { flex: 1; overflow: hidden; position: relative; }
.scroll-btn {
  flex-shrink: 0; width: 28px; height: 28px; border: none; border-radius: 50%;
  background: rgba(5, 150, 105, 0.1); color: #059669; font-size: 18px; font-weight: bold;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}

.forecast-scroll {
  position: relative; display: flex; gap: 0; overflow-x: auto;
  padding: 10px 0; scroll-behavior: smooth; scrollbar-width: none; min-height: 150px;
}
.forecast-scroll::-webkit-scrollbar { display: none; }

.temp-trend-svg { position: absolute; top: 10px; left: 0; pointer-events: none; z-index: 1; }

.forecast-column {
  flex: 0 0 64px; display: flex; flex-direction: column; align-items: center; z-index: 2; 
}
.col-time { font-size: 12px; color: #6b7280; margin-bottom: 6px; font-weight: 600; white-space: nowrap; }
.col-icon { font-size: 22px; margin-bottom: 4px; filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.08)); }
.col-spacer { height: 60px; width: 100%; }
.col-wind { font-size: 11px; color: #9ca3af; white-space: nowrap; font-weight: 500;}

.forecast-scroll-7d { padding: 10px 4px; gap: 12px; }
.forecast-7d-col {
  flex: 0 0 86px; background: rgba(248, 250, 252, 0.8); border: 1px solid #f1f5f9; border-radius: 12px;
  padding: 14px 0; display: flex; flex-direction: column; align-items: center;
}
.col-temp-range { font-size: 14px; font-weight: bold; color: #1f2937; }
.col-temp-range .temp-divider { color: #9ca3af; margin: 0 4px; font-weight: normal; }

.alert-list { min-height: 60px; display: flex; flex-direction: column; justify-content: center; }
.alert-item {
  display: flex; align-items: flex-start; padding: 10px; margin-bottom: 8px;
  border-radius: 8px; background: #FEF0F0; border-left: 3px solid #F56C6C;
}
.alert-item.warning { background: #FDF6EC; border-left-color: #E6A23C; }
.alert-item i { font-size: 18px; margin-right: 8px; color: #F56C6C; }
.alert-item.warning i { color: #E6A23C; }
.alert-content { flex: 1; }
.alert-title { font-weight: 600; font-size: 13px; color: #1F2937; margin-bottom: 4px; }
.alert-desc { font-size: 11px; color: #4B5563; line-height: 1.4; }

.weather-forecast-card, .alert-card, .chart-row .el-card {
  background: #ffffff !important; border-radius: 12px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04) !important; border: 1px solid #e5e7eb !important;
}
.alert-card { height: 100%; }
.alert-card .el-card__body { padding: 10px !important; height: calc(100% - 44px); display: flex; align-items: center; justify-content: center; }
.weather-forecast-card .el-card__header, .alert-card .el-card__header {
  background: #ffffff !important; padding: 6px 12px !important; border-bottom: 1px solid #f1f5f9 !important; border-radius: 12px 12px 0 0 !important;
}
.weather-forecast-card .el-card__body { padding: 6px 10px !important; }
.alert-card .el-card__body { padding: 6px !important; }
</style>
