<template>
  <div class="dashboard-container">
    <div class="header-section">
      <div class="header-left">
        <div class="greeting-box">
          <h1 class="fade-in">{{ greeting }}，{{ username }}场主</h1>
          <p class="subtitle">
            <span class="date-badge">{{ currentDate }}</span>
            <span class="weather-text">{{ weatherText }}，适宜农事巡检</span>
          </p>
        </div>
      </div>
      <div class="header-right">
        <el-button class="glass-btn" icon="el-icon-refresh" circle @click="initDashboard"></el-button>
        <div class="weather-widget glass-panel">
          <div class="w-icon"><img src="@/assets/wealth.png" alt="wealth" class="w-icon-img" /></div>
          <div class="w-info">
            <div class="w-val">{{ currentTemp }}°C</div>
            <div class="w-desc">空气优</div>
          </div>
        </div>
      </div>
    </div>

    <div class="bento-grid">
      
      <div class="card ai-card">
        <div class="card-bg-glow"></div>
        <div class="card-header">
          <div class="title-group">
            <div class="ai-avatar-box">
              <img src="@/assets/ai.png" alt="AI" class="ai-icon" />
            </div>
            <div>
              <div class="card-title">智能体农情日报</div>
              <div class="card-sub">AI 农脑 已连接</div>
            </div>
          </div>
          <el-tag size="mini" effect="dark" color="#8b5cf6" style="border:none">今日已生成</el-tag>
        </div>
        <div class="ai-body">
          <p class="typing-text">{{ aiAdvice }}<span class="cursor">|</span></p>
        </div>
        <div class="ai-footer-bar">
          <span><i class="el-icon-cloudy"></i> 气象关联分析</span>
          <span><i class="el-icon-cpu"></i> 历史数据比对</span>
        </div>
      </div>

      <div class="card stat-card revenue-card">
        <div class="stat-bg-icon"><i class="el-icon-data-line"></i></div>
        <div class="stat-content">
          <div class="stat-top">
            <span class="stat-label">本季预计收益</span>
            <span class="trend-badge" v-if="totalRevenue > 0">+12.4%</span>
          </div>
          <div class="stat-val">{{ totalRevenue > 0 ? '¥' + formatNumber(totalRevenue) : '暂无数据' }}</div>
        </div>
      </div>
      
      <div class="card stat-card yield-card">
        <div class="stat-bg-icon"><i class="el-icon-trophy"></i></div>
        <div class="stat-content">
          <div class="stat-top">
            <span class="stat-label">预计总产量</span>
            <span class="trend-badge" v-if="totalYield > 0">完成85%</span>
          </div>
          <div class="stat-val">{{ totalYield > 0 ? totalYield + ' 吨' : '暂无数据' }}</div>
        </div>
      </div>

      <div class="card monitor-card">
        <div class="monitor-left">
          <el-select 
            v-model="selectedFarmId" 
            placeholder="选择地块" 
            size="small"
            class="farm-select"
            @change="onFarmChange"
          >
            <el-option
              v-for="farm in farmList"
              :key="farm.id"
              :label="'🌱 ' + farm.farm"
              :value="farm.id"
            ></el-option>
          </el-select>
          <div class="device-status">
            <span class="dot" :class="{ online: deviceOnline }"></span>
            {{ deviceOnline ? '设备运行正常' : '设备离线' }}
          </div>
        </div>
        <div class="monitor-right">
          <div class="m-item">
            <div class="m-ring temp-ring">
              <img src="@/assets/temp.png" alt="温度" class="m-icon" />
            </div>
            <div class="m-data">
              <div class="m-val">{{ selectedFarm ? selectedFarm.temperature : sensorData.temp }}°C</div>
              <div class="m-label">空气温度</div>
            </div>
          </div>
          <div class="divider"></div>
          <div class="m-item">
            <div class="m-ring humi-ring">
              <img src="@/assets/water.png" alt="湿度" class="m-icon" />
            </div>
            <div class="m-data">
              <div class="m-val">{{ selectedFarm ? selectedFarm.airhumidity : sensorData.humi }}%</div>
              <div class="m-label">空气湿度</div>
            </div>
          </div>
          <div class="divider"></div>
          <div class="m-item">
            <div class="m-ring soil-ring">
              <img src="@/assets/solid.png" alt="土壤" class="m-icon" />
            </div>
            <div class="m-data">
              <div class="m-val">{{ getSoilStatus(selectedFarm) }}</div>
              <div class="m-label">土壤墒情</div>
            </div>
          </div>
        </div>
      </div>

      <div class="card control-card">
        <div class="card-header-simple">
          <span class="card-title-sm"><img src="@/assets/fast.png" alt="快捷控制" class="title-icon" /> 快捷控制</span>
        </div>
        <div class="control-grid">
          <div class="iot-btn" :class="{ active: iotState.pump }" @click="toggleDevice('pump')">
            <div class="btn-icon-box"><i class="el-icon-odometer"></i></div>
            <div class="btn-info">
              <span class="btn-text">水泵</span>
              <span class="btn-status">{{ iotState.pump ? '运行中' : '已关闭' }}</span>
            </div>
            <div class="led-point"></div>
          </div>
          <div class="iot-btn" :class="{ active: iotState.light }" @click="toggleDevice('light')">
            <div class="btn-icon-box"><i class="el-icon-sunny"></i></div>
            <div class="btn-info">
              <span class="btn-text">补光</span>
              <span class="btn-status">{{ iotState.light ? '开启' : '关闭' }}</span>
            </div>
            <div class="led-point"></div>
          </div>
          <div class="iot-btn" :class="{ active: iotState.fan }" @click="toggleDevice('fan')">
            <div class="btn-icon-box"><i class="el-icon-wind-power"></i></div>
            <div class="btn-info">
              <span class="btn-text">风机</span>
              <span class="btn-status">{{ iotState.fan ? '运行中' : '已关闭' }}</span>
            </div>
            <div class="led-point"></div>
          </div>
        </div>
      </div>

      <div class="card task-card">
        <div class="card-header-simple">
          <span class="card-title-sm"><img src="@/assets/thing.png" alt="待办农事" class="title-icon" /> 待办农事</span>
          <el-tag size="mini" type="info">3 项</el-tag>
        </div>
        <div class="task-list">
          <div class="task-item" v-for="(task, i) in tasks" :key="i" :class="{ done: task.done }">
            <el-checkbox v-model="task.done"></el-checkbox>
            <span class="task-desc">{{ task.text }}</span>
            <el-tag size="mini" :type="task.tagType" effect="light">{{ task.tag }}</el-tag>
          </div>
        </div>
      </div>

      <div class="card nav-card" @click="$router.push('/farm-map-gaode')">
        <div class="tech-bg"></div>
        <div class="nav-content">
          <div class="nav-top">
            <span class="nav-tag">核心功能</span>
            <div class="nav-icon-circle"><img src="@/assets/earth.png" alt="GIS" class="nav-icon" /></div>
          </div>
          <div class="nav-bottom">
            <div class="nav-title">GIS 指挥舱</div>
            <div class="nav-desc">全域地图 / 无人机巡检</div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script>
export default {
  name: 'Home',
  data() {
    return {
      username: '场主',
      currentDate: new Date().toLocaleDateString(),
      currentTemp: '26',
      weatherText: '晴转多云',
      aiAdvice: '',
      totalRevenue: 0,
      totalYield: 0,
      deviceOnline: true,
      sensorData: { temp: 25.6, humi: 58 },
      iotState: { pump: false, light: true, fan: false },
      // 地块选择相关
      farmList: [],  // 所有地块列表
      selectedFarmId: null,  // 当前选中的地块ID
      selectedFarm: null,  // 当前选中的地块数据
      tasks: [
        { text: 'A3 号田番茄需要追肥', done: false, tag: '紧急', tagType: 'danger' },
        { text: '检查 2 号传感器电池', done: false, tag: '重要', tagType: 'warning' },
        { text: 'B 区成熟草莓采摘', done: true, tag: '常规', tagType: 'info' }
      ],
      // 新增：农情日报相关数据
      reportLoading: false,
      weatherData: {},
      farmlandData: [],
      suggestions: [],
      typeWriterTimer: null
    }
  },
  beforeUnmount() {
    if (this.typeWriterTimer) {
      clearInterval(this.typeWriterTimer);
      this.typeWriterTimer = null;
    }
  },
  computed: {
    greeting() {
      const hour = new Date().getHours();
      if (hour < 6) return '凌晨好';
      if (hour < 12) return '早上好';
      if (hour < 14) return '中午好';
      if (hour < 18) return '下午好';
      return '晚上好';
    }
  },
  mounted() {
    this.getUserName();
    this.initDashboard();
  },
  methods: {
    getUserName() {
      try {
        const userStr = localStorage.getItem('user');
        if (userStr) {
          const user = JSON.parse(userStr);
          this.username = user.nickname || user.username || '场主';
        }
      } catch (e) {
        console.error('获取用户信息失败:', e);
      }
    },
    async initDashboard() {
      // 获取地块列表
      await this.fetchFarmList();
      // 获取动态农情日报
      this.fetchAgriDailyReport();
      this.fetchBusinessData();
      this.fetchSensorData();
      this.fetchWeatherData();
    },
    
    // 获取地块列表
    async fetchFarmList() {
      try {
        const res = await this.request.get('/statistic/dashboard');
        if (res.code === '200' && res.data && res.data.length > 0) {
          this.farmList = res.data;
          // 默认选中第一个地块
          this.selectedFarmId = res.data[0].id;
          this.selectedFarm = res.data[0];
          this.updateFarmSensorData();
        }
      } catch (e) {
        console.error('获取地块列表失败:', e);
      }
    },
    
    // 地块切换时更新数据
    onFarmChange(farmId) {
      this.selectedFarm = this.farmList.find(f => f.id === farmId);
      this.updateFarmSensorData();
    },
    
    // 更新选中地块的传感器数据
    async updateFarmSensorData() {
      if (!this.selectedFarm) return;
      
      // 根据地块ID决定使用哪个设备的数据
      const deviceEndpoint = this.selectedFarm.id % 2 === 0 
        ? '/aether/device/status' 
        : '/aether/device/new/status';
      
      try {
        const res = await this.request.get(deviceEndpoint);
        if (res.code === '200' && res.data) {
          // 直接使用传感器实时数据，不添加波动
          this.selectedFarm.temperature = res.data.temperature;
          this.selectedFarm.airhumidity = res.data.humidity;
          this.selectedFarm.soilhumidity = res.data.humidity;
          
          this.deviceOnline = res.data.online !== false;
        }
      } catch (e) {
        console.warn('获取传感器数据失败:', e);
      }
    },
    
    // 获取土壤墒情状态
    getSoilStatus(farm) {
      if (!farm || !farm.soilhumidity) return '适宜';
      const humidity = Number(farm.soilhumidity);
      if (humidity < 20) return '干旱';
      if (humidity < 40) return '偏干';
      if (humidity > 80) return '过湿';
      return '适宜';
    },
    typeWriterEffect(text = null) {
      if (!text) {
        text = "智能农情分析系统正在获取数据，请稍候...";
      }
      if (this.typeWriterTimer) {
        clearInterval(this.typeWriterTimer);
        this.typeWriterTimer = null;
      }
      let i = 0;
      this.aiAdvice = "";
      this.typeWriterTimer = setInterval(() => {
        this.aiAdvice += text.charAt(i);
        i++;
        if (i >= text.length) {
          clearInterval(this.typeWriterTimer);
          this.typeWriterTimer = null;
        }
      }, 50);
    },
    async fetchBusinessData() {
      try {
        // 从销售数据获取收益
        const salesRes = await this.request.get('/sales');
        if (salesRes.code === '200' && salesRes.data && salesRes.data.length > 0) {
          this.totalRevenue = salesRes.data.reduce((sum, item) => {
            return sum + (parseFloat(item.price || 0) * parseFloat(item.number || 0));
          }, 0);
          this.totalYield = (salesRes.data.reduce((sum, item) => sum + parseFloat(item.number || 0), 0) / 1000).toFixed(1);
        } else {
          // 没有数据时显示 0
          this.totalRevenue = 0;
          this.totalYield = 0;
        }
      } catch(e) {
        // 错误时显示 0
        this.totalRevenue = 0;
        this.totalYield = 0;
      }
    },
    async fetchSensorData() {
      try {
        const res = await this.request.get('/aether/device/status');
        if (res.code === '200' && res.data) {
          this.deviceOnline = res.data.online;
          this.sensorData.temp = res.data.temperature || 25.6;
          this.sensorData.humi = res.data.humidity || 58;
          this.iotState.light = res.data.led === 1;
          this.currentTemp = res.data.temperature || '26';
        }
      } catch(e) { console.warn('获取传感器数据失败', e) }
    },
    async toggleDevice(type) {
      const newState = !this.iotState[type];
      const deviceMap = { pump: '水泵', light: '补光灯', fan: '风机' };
      try {
        if (type === 'light') {
          await this.request.post('/aether/device/control/led', { led: newState ? 1 : 0 });
        } else if (type === 'pump') {
          await this.request.post('/aether/device/control/bump', { bump: newState });
        } else if (type === 'fan') {
          await this.request.post('/aether/device/control/fan', { fan: newState });
        }
        this.iotState[type] = newState;
        this.$message.success(`指令下发成功：${newState ? '开启' : '关闭'} ${deviceMap[type]}`);
      } catch (e) {
        this.$message.error(`${deviceMap[type]}控制失败，请重试`);
      }
    },
    formatNumber(num) {
      return num.toLocaleString();
    },
    
    /**
     * 获取动态农情日报
     */
    async fetchAgriDailyReport() {
      this.reportLoading = true;
      try {
        // 获取用户信息
        let userId = null;
        try {
          const userStr = localStorage.getItem('user');
          if (userStr) {
            const user = JSON.parse(userStr);
            userId = user.id;
          }
        } catch (e) { console.warn('获取用户信息失败', e) }
        
        // 调用农情日报API
        const res = await this.request.get('/agri-report/daily', {
          params: {
            userId: userId
          }
        });
        
        if (res.code === '200' && res.data) {
          const data = res.data;
          
          // 更新AI建议文本
          if (data.aiAdvice) {
            this.typeWriterEffect(data.aiAdvice);
          }
          
          // 更新天气数据
          if (data.weather && data.weather.current) {
            const weather = data.weather.current;
            this.currentTemp = weather.temperature || '26';
            this.weatherText = weather.weather || '晴';
          }
          
          // 更新农田数据
          if (data.farmlands) {
            this.farmlandData = data.farmlands;
          }
          
          // 更新建议列表
          if (data.suggestions && data.suggestions.length > 0) {
            // 将建议转换为任务列表格式
            this.tasks = data.suggestions.slice(0, 3).map((sugg, index) => ({
              text: sugg.content,
              done: false,
              tag: this.getPriorityTag(sugg.priority),
              tagType: this.getPriorityType(sugg.priority)
            }));
          }
        } else {
          // 如果获取失败，使用默认文本
          this.typeWriterEffect();
        }
      } catch (e) {
        console.error('获取农情日报失败:', e);
        // 使用默认文本
        this.typeWriterEffect();
      } finally {
        this.reportLoading = false;
      }
    },
    
    /**
     * 获取实时天气数据
     */
    async fetchWeatherData() {
      try {
        const res = await this.request.get('/aether/weather/now');
        if (res.code === '200' && res.data && res.data.data) {
          const weatherInfo = res.data.data;
          this.currentTemp = weatherInfo.temp || '26';
          this.weatherText = weatherInfo.text || '晴';
        }
      } catch (e) {
        console.error('获取天气数据失败:', e);
      }
    },
    
    /**
     * 优先级标签映射
     */
    getPriorityTag(priority) {
      const map = {
        'high': '紧急',
        'medium': '重要',
        'low': '常规'
      };
      return map[priority] || '常规';
    },
    
    /**
     * 优先级类型映射
     */
    getPriorityType(priority) {
      const map = {
        'high': 'danger',
        'medium': 'warning',
        'low': 'info'
      };
      return map[priority] || 'info';
    }
  }
}
</script>

<style>
/* 仅在首页隐藏滚动条 */
</style>

<style scoped>
/* 组件样式 */
.dashboard-container {
  padding: 12px 20px;
  padding-top: 38px; /* 增加顶部间距，向下移动内容 */
  background-color: #f4f6f9; /* 更高级的灰 */
  height: calc(100vh - 60px);
  box-sizing: border-box;
  overflow: hidden; /* 隐藏页面滚动条 */
}

/* 1. 头部样式升级 */
.header-section {
  display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 12px;
  height: 48px; /* 固定头部高度 */
}
.greeting-box h1 { margin: 0 0 6px 0; font-size: 24px; color: #1e293b; font-weight: 800; letter-spacing: -0.5px; }
.subtitle { margin: 0; color: #64748b; font-size: 14px; display: flex; align-items: center; gap: 10px; }
.date-badge { background: #e2e8f0; padding: 4px 8px; border-radius: 6px; font-weight: 600; font-size: 12px; color: #475569; }

.header-right { display: flex; gap: 16px; align-items: center; }
.glass-panel {
  background: white; padding: 8px 16px; border-radius: 16px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(255,255,255,0.5);
}
.glass-btn { box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); border: none; }
.w-icon { font-size: 24px; }
.w-icon-img { width: 24px; height: 24px; object-fit: contain; }
.w-val { font-weight: 800; font-size: 18px; color: #1e293b; }
.w-desc { font-size: 11px; color: #94a3b8; font-weight: 600; }

/* 2. Bento Grid 布局 - 比例精调 */
.bento-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr; /* 左侧更宽 */
  grid-template-rows: 200px 120px 200px; /* 增大行高，充分利用空间 */
  gap: 16px;
  height: calc(100% - 60px); /* 精确计算头部高度 */
}

/* 卡片通用基础 */
.card {
  background: white; border-radius: 22px; /* 稍大的圆角 */
  padding: 22px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.03), 0 4px 6px -2px rgba(0, 0, 0, 0.01); /* 更柔和的阴影 */
  transition: all 0.3s ease;
  position: relative; overflow: hidden;
  display: flex; flex-direction: column;
  border: 1px solid #ffffff;
}
.card:hover { box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.05); }

/* A. DeepSeek AI 卡片 - 科技感增强 */
.ai-card {
  grid-column: 1 / 2; grid-row: 1 / 2;
  background: radial-gradient(circle at top right, #f5f3ff, #ffffff 60%);
  border: 1px solid #ede9fe;
}
/* 增加一个背景光晕 */
.card-bg-glow {
  position: absolute; top: -50px; right: -50px; width: 200px; height: 200px;
  background: linear-gradient(135deg, #ddd6fe, transparent);
  border-radius: 50%; opacity: 0.5; filter: blur(40px);
}
.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; z-index: 1; }
.title-group { display: flex; gap: 12px; align-items: center; }
.ai-avatar-box {
  width: 42px; height: 42px; background: #7c3aed; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 10px rgba(124, 58, 237, 0.3);
}
.ai-icon { width: 28px; height: 28px; }
.card-title { font-weight: 800; color: #1e293b; font-size: 18px; line-height: 1.2; }
.card-sub { font-size: 12px; color: #8b5cf6; font-weight: 500; margin-top: 2px; }
.ai-body { flex: 1; z-index: 1; }
.typing-text { font-size: 15px; line-height: 1.7; color: #475569; margin: 0; text-align: justify; font-weight: 500; }
.cursor { animation: blink 1s infinite; font-weight: bold; color: #7c3aed; }
.ai-footer-bar {
  margin-top: auto; padding-top: 14px; border-top: 1px dashed #e2e8f0;
  display: flex; gap: 18px; font-size: 12px; color: #94a3b8; font-weight: 500;
}

/* B. 经营指标 - 弥散渐变 */
.revenue-card {
  grid-column: 2 / 3; grid-row: 1 / 2;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: white; justify-content: space-between;
}
.yield-card {
  grid-column: 3 / 4; grid-row: 1 / 2;
  background: linear-gradient(135deg, #059669, #047857);
  color: white; justify-content: space-between;
}
.stat-bg-icon {
  position: absolute; bottom: -10px; right: -10px; font-size: 120px;
  opacity: 0.1; transform: rotate(-15deg);
}
.stat-top { display: flex; justify-content: space-between; align-items: center; }
.stat-label { font-size: 13px; opacity: 0.9; font-weight: 500; }
.trend-badge { background: rgba(255,255,255,0.2); padding: 4px 8px; border-radius: 6px; font-size: 12px; font-weight: bold; }
.stat-val { font-size: 32px; font-weight: 800; letter-spacing: -1px; }
.unit { font-size: 16px; font-weight: 500; }

/* C. 环境监测 - 更加通透 */
.monitor-card {
  grid-column: 1 / 4; grid-row: 2 / 3;
  padding: 0 32px;
  flex-direction: row; align-items: center; justify-content: space-between;
}
.monitor-left { min-width: 200px; display: flex; flex-direction: column; justify-content: center; }
.card-title-lg { font-size: 20px; font-weight: 800; color: #1e293b; margin-bottom: 8px; }
.device-status { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #64748b; font-weight: 500; margin-top: 8px; }

/* 地块选择下拉框样式 */
.farm-select {
  width: 200px;
}
.farm-select .el-input__inner {
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  padding: 8px 12px;
  height: 44px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.farm-select .el-input__inner:hover {
  border-color: #10b981;
  background: linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%);
}
.farm-select .el-input.is-focus .el-input__inner {
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
}
.farm-select .el-input__suffix {
  right: 8px;
}
.farm-select .el-select__caret {
  color: #10b981;
  font-size: 14px;
  transition: transform 0.3s;
}
.dot { width: 8px; height: 8px; background: #cbd5e1; border-radius: 50%; }
.dot.online { background: #10b981; box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2); }

.monitor-right { display: flex; flex: 1; justify-content: space-around; align-items: center; padding-left: 50px; }
.m-item { display: flex; align-items: center; gap: 16px; }
.m-ring {
  width: 56px; height: 56px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 22px;
}
.temp-ring { background: #fee2e2; color: #ef4444; }
.humi-ring { background: #dbeafe; color: #3b82f6; }
.soil-ring { background: #dcfce7; color: #10b981; }
.m-val { font-size: 24px; font-weight: 800; color: #0f172a; }
.m-label { font-size: 13px; color: #64748b; font-weight: 500; }
.divider { width: 1px; height: 40px; background: #e2e8f0; }

/* D. 快捷控制 - 实体按钮感 */
.control-card {
  grid-column: 1 / 2; grid-row: 3 / 4;
}
.card-title-sm { font-size: 16px; font-weight: 700; color: #1e293b; display: block; margin-bottom: 16px; }
.control-grid { display: flex; gap: 16px; flex: 1; }
.iot-btn {
  flex: 1; background: #f8fafc; border-radius: 14px; cursor: pointer;
  display: flex; flex-direction: column; justify-content: space-between; padding: 16px;
  transition: all 0.2s; border: 2px solid #f1f5f9; position: relative;
}
.iot-btn:hover { background: #f1f5f9; }
.iot-btn.active { background: white; border-color: #10b981; box-shadow: 0 4px 15px rgba(16, 185, 129, 0.15); }
.btn-icon-box { font-size: 24px; color: #94a3b8; transition: 0.3s; }
.iot-btn.active .btn-icon-box { color: #10b981; }
.btn-text { font-size: 14px; font-weight: 700; color: #334155; display: block; }
.btn-status { font-size: 12px; color: #94a3b8; }
.led-point {
  position: absolute; top: 16px; right: 16px; width: 6px; height: 6px;
  background: #cbd5e1; border-radius: 50%;
}
.iot-btn.active .led-point { background: #10b981; box-shadow: 0 0 5px #10b981; }

/* E. 待办事项 */
.task-card {
  grid-column: 2 / 3; grid-row: 3 / 4;
}
.card-header-simple { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.task-list { 
  display: flex; 
  flex-direction: column; 
  gap: 10px; 
  overflow-y: auto; /* 保留待办农事的滚动条 */
  flex: 1;
  max-height: calc(100% - 50px); /* 限制最大高度 */
  padding-right: 4px; /* 为滚动条留出空间 */
}
.task-list::-webkit-scrollbar {
  width: 4px;
}

.task-list::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 2px;
}

.task-list::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 2px;
}

.task-list::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
.task-item {
  display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 10px;
  background: #f8fafc; transition: 0.2s;
}
.task-item:hover { background: #f1f5f9; }
.task-desc { flex: 1; font-size: 13px; color: #475569; font-weight: 500; }
.task-item.done .task-desc { text-decoration: line-through; color: #cbd5e1; }

/* F. GIS 入口 - 科技感 */
.nav-card {
  grid-column: 3 / 4; grid-row: 3 / 4;
  background: linear-gradient(135deg, #0f172a, #1e293b);
  color: white; border: none; padding: 0;
  cursor: pointer;
}
.tech-bg {
  position: absolute; inset: 0;
  background-image:
    radial-gradient(rgba(255,255,255,0.2) 1px, transparent 1px),
    linear-gradient(to bottom, transparent 60%, rgba(16, 185, 129, 0.2));
  background-size: 20px 20px, 100% 100%; /* 网格纹理 */
  opacity: 0.6; transition: 0.5s;
}
.nav-card:hover .tech-bg { opacity: 0.5; }
.nav-content {
  position: relative; z-index: 1; padding: 24px; height: 100%;
  display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;
}
.nav-top { display: flex; justify-content: space-between; align-items: flex-start; }
.nav-tag { background: rgba(16, 185, 129, 0.2); color: #34d399; padding: 4px 8px; border-radius: 4px; font-size: 10px; font-weight: 600; letter-spacing: 1px; border: 1px solid rgba(52, 211, 153, 0.3); }
.nav-icon-circle {
  width: 48px; height: 48px; 
  background: rgba(255,255,255,0.1);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24px;
  box-shadow: 0 4px 15px rgba(0,0,0,0.2);
}
.nav-title { font-size: 20px; font-weight: 800; margin-bottom: 4px; }
.nav-desc { font-size: 12px; color: #cbd5e1; }

/* Image icon styles */
.m-icon {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.title-icon {
  width: 16px;
  height: 16px;
  vertical-align: middle;
  margin-right: 4px;
}

.nav-icon {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

@media screen and (max-width: 1280px) {
  .dashboard-container {
    height: auto;
    min-height: calc(100vh - 60px);
    overflow-y: auto;
    padding-bottom: 20px;
  }

  .header-section {
    height: auto;
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 16px;
  }

  .subtitle {
    flex-wrap: wrap;
  }

  .bento-grid {
    height: auto;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: auto;
    grid-auto-rows: minmax(180px, auto);
  }

  .ai-card,
  .monitor-card {
    grid-column: 1 / -1;
    grid-row: auto;
  }

  .revenue-card,
  .yield-card,
  .control-card,
  .task-card,
  .nav-card {
    grid-row: auto;
  }

  .monitor-card {
    padding: 24px;
    flex-direction: column;
    align-items: stretch;
    gap: 20px;
  }

  .monitor-left {
    min-width: 0;
  }

  .monitor-right {
    padding-left: 0;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
  }

  .divider {
    display: none;
  }

  .control-grid {
    flex-wrap: wrap;
  }

  .iot-btn {
    min-width: 160px;
  }
}

@media screen and (max-width: 768px) {
  .dashboard-container {
    padding: 16px 12px 20px;
    min-height: calc(100vh - 50px);
  }

  .header-section,
  .header-right {
    flex-direction: column;
    align-items: stretch;
  }

  .header-right {
    width: 100%;
    gap: 12px;
  }

  .glass-panel {
    width: 100%;
    justify-content: center;
  }

  .glass-btn {
    align-self: flex-end;
  }

  .bento-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .ai-card,
  .revenue-card,
  .yield-card,
  .monitor-card,
  .control-card,
  .task-card,
  .nav-card {
    grid-column: auto !important;
    grid-row: auto !important;
  }

  .card {
    padding: 18px;
    border-radius: 18px;
  }

  .card-header,
  .card-header-simple,
  .nav-top {
    flex-wrap: wrap;
    gap: 10px;
  }

  .ai-footer-bar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .stat-val {
    font-size: 28px;
  }

  .farm-select {
    width: 100%;
  }

  .monitor-right {
    flex-direction: column;
    align-items: stretch;
  }

  .m-item {
    width: 100%;
  }

  .control-grid {
    flex-direction: column;
  }

  .iot-btn {
    min-width: 0;
    min-height: 108px;
  }

  .task-list {
    max-height: none;
  }

  .nav-content {
    min-height: 180px;
  }
}

@media screen and (max-width: 480px) {
  .greeting-box h1 {
    font-size: 20px;
  }

  .subtitle {
    font-size: 12px;
    gap: 8px;
  }

  .card-title,
  .nav-title {
    font-size: 18px;
  }

  .m-val {
    font-size: 22px;
  }
}
</style>
