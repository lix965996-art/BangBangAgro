<template>
  <div class="ios-dashboard">
    <div class="ios-header-bar">
      <div class="header-left">
        <div class="title-box">
          <i class="el-icon-data-analysis icon-blue"></i>
          <h1>智能环境与农事指挥中心</h1>
        </div>
        <div class="status-pill">
          <span class="status-dot"></span> 系统运行正常
        </div>
      </div>
      <div class="header-right">
        <el-select
          v-model="selectedFarmId"
          placeholder="切换监测农田"
          @change="onFarmSelect"
          size="small"
          class="ios-select farm-select">
          <el-option v-for="farm in farmList" :key="farm.id" :label="farm.name" :value="farm.id">
            <span style="float: left; font-weight: 500;">{{ farm.name }}</span>
            <span style="float: right; color: #8E8E93; font-size: 13px">{{ farm.crop || '未种植' }}</span>
          </el-option>
        </el-select>
        <el-button class="ios-btn-secondary" size="small" icon="el-icon-refresh" @click="refreshData" :loading="loading">更新数据</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6" v-for="(kpi, index) in kpiData" :key="index">
        <div class="ios-card kpi-card">
          <div class="kpi-icon-box" :class="kpi.colorClass">
            <i :class="kpi.icon"></i>
          </div>
          <div class="kpi-info">
            <div class="kpi-label">{{ kpi.label }}</div>
            <div class="kpi-value">
              {{ kpi.value }}<span class="unit">{{ kpi.unit }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="middle-row">
      <el-col :span="14">
        <div class="ios-card ai-card">
          <div class="card-header border-bottom">
            <div class="header-title">
              <i class="el-icon-cpu" style="color: #AF52DE;"></i> AI 农事决策引擎
            </div>
            <el-tag v-if="qwenAnalyzing" size="mini" class="ios-tag-loading"><i class="el-icon-loading"></i> 分析中</el-tag>
            <el-tag v-else size="mini" class="ios-tag-success">引擎在线</el-tag>
          </div>
          
          <div class="ai-body">
            <div class="ai-chat-container">
              <div class="ai-avatar">
                <img src="@/assets/ai.png" alt="AI" @error="handleImgError" />
              </div>
              <div class="ai-chat-bubble">
                <p class="typing-text">{{ aiText }}<span class="cursor" v-if="typing">|</span></p>
              </div>
            </div>

            <div class="ai-metrics" v-if="aiPredictions.growthRate > 0">
              <div class="metric-item">
                <span class="metric-label">环境评分</span>
                <el-progress :percentage="Number(aiPredictions.growthRate)" :color="getScoreColor(aiPredictions.growthRate)" :stroke-width="6"></el-progress>
              </div>
              <div class="metric-item">
                <span class="metric-label">预计采收</span>
                <span class="metric-val">{{ aiPredictions.harvestDate }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">市场评估</span>
                <span class="metric-val text-blue">{{ aiPredictions.marketAnalysis }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="10">
        <div class="ios-card radar-card">
          <div class="card-header">
            <div class="header-title">
              <i class="el-icon-data-line" style="color: #34C759;"></i> 实时环境监测
            </div>
            <div class="score-text" :style="{ color: getScoreColor(envScore) }">
              综合 {{ envScore }} 分
            </div>
          </div>
          <div class="chart-container" ref="radarChart"></div>
        </div>
      </el-col>
    </el-row>

    <div class="ios-card compare-card">
      <div class="card-header border-bottom">
        <div class="header-title">
          <i class="el-icon-s-marketing" style="color: #FF9500;"></i> 跨地块数据对比分析
        </div>
        <div class="checkbox-group-ios">
          <el-checkbox-group v-model="selectedFarms" @change="onFarmsChange" size="small">
            <el-checkbox v-for="farm in farmList" :key="farm.id" :label="farm.id" border>
              {{ farm.name }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
      <div class="chart-container large" ref="compareChart"></div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'DashbordNew',
  data() {
    return {
      loading: false,
      farmList: [],
      selectedFarms: [],
      selectedFarmId: null, 
      currentFarmData: null,

      // 实时硬件数据
      sensorData: {
        temperature: 24, humidity: 55, soilMoisture: 45, lightIntensity: 600, co2Level: 420
      },

      // AI 状态
      aiText: '正在初始化农事分析模型...',
      typing: false,
      qwenAnalyzing: false,
      typingTimeout: null,
      aiTimer: null,
      dataTimer: null,
      _isDestroyed: false,

      kpiData: [
        { label: '预估总产量', value: 0, unit: ' 吨', icon: 'el-icon-box', colorClass: 'bg-blue' },
        { label: '预计总收入', value: 0, unit: ' 万元', icon: 'el-icon-wallet', colorClass: 'bg-green' },
        { label: '种植总面积', value: 0, unit: ' 亩', icon: 'el-icon-map-location', colorClass: 'bg-orange' },
        { label: '距采收剩余', value: 0, unit: ' 天', icon: 'el-icon-time', colorClass: 'bg-purple' }
      ],

      aiPredictions: {
        growthRate: 0, harvestDate: '--', expectedYield: 0, marketAnalysis: '--'
      },

      radarChart: null,
      compareChart: null,
      envScore: 75,
      fallbackAiImg: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="%23AF52DE"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm0 10c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4z"/></svg>'
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.loadFarmList();
      
      this.debouncedResize = this.debounce(this.handleResize, 200);
      window.addEventListener('resize', this.debouncedResize);

      this.startAiAnalysis();
      this.dataTimer = setInterval(() => { this.refreshSensorData(); }, 10000);
    });
  },
  beforeUnmount() {
    this._isDestroyed = true;
    if(this.aiTimer) clearInterval(this.aiTimer);
    if(this.dataTimer) clearInterval(this.dataTimer);
    if(this.typingTimeout) clearTimeout(this.typingTimeout);
    window.removeEventListener('resize', this.debouncedResize);
    
    if(this.radarChart) this.radarChart.dispose();
    if(this.compareChart) this.compareChart.dispose();
  },
  methods: {
    handleImgError(e) {
      e.target.src = this.fallbackAiImg;
    },
    debounce(func, wait) {
      let timeout;
      return function(...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
      };
    },
    getScoreColor(score) {
      if (score >= 80) return '#34C759'; 
      if (score >= 60) return '#FF9500'; 
      return '#FF3B30'; 
    },
    async loadFarmList() {
      this.loading = true;
      try {
        const res = await this.request.get('/statistic/page', { params: { pageNum: 1, pageSize: 100, farm: '' } });
        if (res.code === '200' && res.data && res.data.records) {
          this.farmList = res.data.records.map(item => ({
            id: item.id,
            name: item.farm || `农田${item.id}`,
            area: item.area || 100,
            crop: item.crop || '小麦',
            growthDays: item.growthDays || 90
          }));
          
          if (this.farmList.length > 0) {
            this.selectedFarmId = this.farmList[0].id;
            this.selectedFarms = this.farmList.slice(0, 3).map(f => f.id);
            this.onFarmSelect(this.selectedFarmId);
            this.initCompareChart();
          }
        }
      } catch (e) {
        this.$message.error('地块数据加载失败');
      } finally {
        this.loading = false;
      }
    },
    async onFarmSelect(farmId) {
      this.currentFarmData = this.farmList.find(f => f.id === farmId);
      if (this.currentFarmData) {
        this.updateKPIs();
        this.generateAiAnalysis();
        this.envScore = this.calculateEnvironmentScore();
        this.initRadarChart();
      }
    },
    onFarmsChange() {
      this.initCompareChart();
    },
    async refreshData() {
      this.loading = true;
      try {
        await this.onFarmSelect(this.selectedFarmId);
        this.$message.success('数据已更新');
      } catch (error) {
        this.$message.error('刷新失败');
      } finally {
        this.loading = false;
      }
    },
    startAiAnalysis() {
      this.aiTimer = setInterval(() => {
        if (this.currentFarmData) this.generateAiAnalysis();
      }, 30000);
    },
    generateAiAnalysis() {
      this.qwenAnalyzing = true;
      this.aiText = '模型正在分析当前地块数据...';
      this.typing = false;
      clearTimeout(this.typingTimeout);

      setTimeout(() => {
        const month = new Date().getMonth() + 1;
        const crop = this.currentFarmData.crop;
        
        let suggestion = '环境稳定，作物生长正常，请继续保持巡检。';
        if (month >= 3 && month <= 5) suggestion = '当前处于春季生长关键期，系统建议您加强水肥管理并监测土壤湿度。';
        else if (month >= 6 && month <= 8) suggestion = '高温预警：请注意防暑降温，系统已就绪，可随时开启自动滴灌策略。';
        else if (month >= 9 && month <= 11) suggestion = '秋季成熟期：作物长势喜人，预计品质优良，请合理安排采收计划。';

        if (crop === '水稻') suggestion += ' 水稻处于分蘖期，建议保持浅水层。';
        else if (crop === '小麦') suggestion += ' 小麦拔节期，需重点关注氮肥施用情况。';

        const growthRate = Math.floor(70 + Math.random() * 20);
        const expectedYield = (2.5 + Math.random() * 2).toFixed(1);
        const harvestDate = new Date(Date.now() + (90 - this.currentFarmData.growthDays) * 86400000);

        this.aiPredictions = {
          growthRate,
          harvestDate: harvestDate.toLocaleDateString('zh-CN'),
          expectedYield: parseFloat(expectedYield),
          marketAnalysis: '近期市场需求旺盛，预计价格有 5% 的上浮空间'
        };

        this.updateKPIs();
        this.aiText = suggestion;
        this.typeAiText();
        this.qwenAnalyzing = false;
      }, 1500);
    },
    typeAiText() {
      this.typing = true;
      const fullText = this.aiText;
      let index = 0;

      const type = () => {
        if (this._isDestroyed) return;
        if (index < fullText.length) {
          this.aiText = fullText.substring(0, index + 1);
          index++;
          this.typingTimeout = setTimeout(type, 30); // 稍微加快打字速度
        } else {
          this.typing = false;
        }
      };
      type();
    },
    updateKPIs() {
      if (!this.currentFarmData) return;
      this.kpiData[0].value = this.aiPredictions.expectedYield || 2.8;
      
      const prices = { '小麦': 2800, '水稻': 3200, '玉米': 2600 };
      const marketPrice = prices[this.currentFarmData.crop] || 3000;
      this.kpiData[1].value = ((this.kpiData[0].value * marketPrice) / 10000).toFixed(1);
      
      this.kpiData[2].value = this.currentFarmData.area || 100;
      
      const now = new Date();
      const harvestDate = new Date(now.getTime() + (90 - this.currentFarmData.growthDays) * 86400000);
      this.kpiData[3].value = Math.max(0, Math.ceil((harvestDate - now) / 86400000));
    },
    calculateEnvironmentScore() {
      let score = 75 + (Math.random() * 10 - 5);
      return Math.max(0, Math.min(100, score)).toFixed(1);
    },
    async refreshSensorData() {
      this.sensorData = {
        temperature: 20 + Math.random() * 10,
        humidity: 40 + Math.random() * 30,
        soilMoisture: 30 + Math.random() * 40,
        lightIntensity: 100 + Math.random() * 800,
        co2Level: 400 + Math.random() * 100
      };
      this.envScore = this.calculateEnvironmentScore();
      this.initRadarChart();
    },

    // ✨精修：柔和的雷达图
    initRadarChart() {
      if (!this.$refs.radarChart) return;
      if (this.radarChart) this.radarChart.dispose();
      this.radarChart = echarts.init(this.$refs.radarChart);

      const data = [
        this.sensorData.temperature,
        this.sensorData.humidity,
        this.sensorData.soilMoisture,
        this.sensorData.lightIntensity / 10,
        this.sensorData.co2Level / 10
      ];

      const color = this.getScoreColor(this.envScore);

      this.radarChart.setOption({
        radar: {
          indicator: [
            { name: '温度', max: 40 },
            { name: '空气湿度', max: 100 },
            { name: '土壤湿度', max: 100 },
            { name: '光照(x10)', max: 100 },
            { name: 'CO2(x10)', max: 100 }
          ],
          radius: '65%',
          center: ['50%', '55%'],
          splitNumber: 4,
          axisName: { color: '#8E8E93', fontSize: 12, fontWeight: 600 },
          splitLine: { lineStyle: { color: '#E5E5EA', width: 1 } },
          splitArea: { show: true, areaStyle: { color: ['#F8F9FA', '#FFFFFF'] } }, // 极柔和的背景交替
          axisLine: { lineStyle: { color: '#E5E5EA' } }
        },
        series: [{
          type: 'radar',
          data: [{
            value: data,
            name: '实时环境',
            itemStyle: { color: color },
            areaStyle: { color: echarts.color.modifyAlpha(color, 0.08) }, // ✨ 精修：透明度降到0.08，极其高级的磨砂感
            lineStyle: { width: 1.5 } // ✨ 精修：线条变细
          }]
        }]
      });
    },

    // ✨精修：去线化的原生图表 + 稳定伪随机数据
    initCompareChart() {
      if (!this.$refs.compareChart) return;
      if (this.compareChart) this.compareChart.dispose();
      this.compareChart = echarts.init(this.$refs.compareChart);
      
      const farms = this.farmList.filter(f => this.selectedFarms.includes(f.id));
      if(farms.length === 0) return;

      const farmNames = farms.map(f => f.name);
      
      // 生成稳定的伪随机数据(根据地块ID计算，确保每次渲染数据不乱跳)
      const tempDatas = farms.map(f => (20 + (f.id % 5) * 2).toFixed(1));
      const humiDatas = farms.map(f => (50 + (f.id % 4) * 5).toFixed(1));

      this.compareChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'none' }, backgroundColor: 'rgba(255,255,255,0.9)', borderColor: '#E5E5EA', textStyle: { color: '#1C1C1E' } },
        legend: { data: ['平均温度 (℃)', '平均湿度 (%)'], bottom: 0, textStyle: { color: '#8E8E93', fontWeight: 500 }, icon: 'circle' },
        grid: { left: '2%', right: '2%', bottom: '15%', top: '15%', containLabel: true },
        xAxis: { 
          type: 'category', 
          data: farmNames, 
          axisLine: { show: false }, // ✨ 精修：隐藏X轴底线
          axisTick: { show: false }, // ✨ 精修：隐藏X轴刻度
          axisLabel: { color: '#8E8E93', fontWeight: 600, margin: 12 } 
        },
        yAxis: { 
          type: 'value', 
          axisLine: { show: false }, // ✨ 精修：隐藏Y轴轴线
          axisTick: { show: false },
          splitLine: { lineStyle: { color: '#F2F2F7', type: 'solid' } }, // ✨ 精修：使用固态浅灰网格线
          axisLabel: { color: '#8E8E93' } 
        },
        series: [
          {
            name: '平均温度 (℃)', type: 'bar', barWidth: '15%', barGap: '20%',
            itemStyle: { color: '#FF9500', borderRadius: [6, 6, 0, 0] }, // 圆角稍微增大
            data: tempDatas
          },
          {
            name: '平均湿度 (%)', type: 'bar', barWidth: '15%',
            itemStyle: { color: '#34C759', borderRadius: [6, 6, 0, 0] },
            data: humiDatas
          }
        ]
      });
    },
    handleResize() {
      if(this.radarChart) this.radarChart.resize();
      if(this.compareChart) this.compareChart.resize();
    }
  }
}
</script>

<style scoped>
/* ========================================================== */
/* 苹果 iOS 极简扁平风格 - 仪表盘 (Final Polish) */
/* ========================================================== */
.ios-dashboard {
  padding: 20px;
  background-color: #F2F2F7;
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* 公共卡片样式 */
.ios-card {
  background: #FFFFFF;
  border-radius: 18px; /* 更加圆润 */
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03); /* 更柔和的弥散阴影 */
  border: 1px solid rgba(229, 231, 235, 0.4);
  margin-bottom: 20px;
  overflow: hidden;
}

.card-header {
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header.border-bottom {
  border-bottom: 1px solid #F2F2F7;
}
.header-title {
  font-size: 16px;
  font-weight: 700;
  color: #1C1C1E;
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-title i { font-size: 18px; }

/* 1. 顶部操作栏 */
.ios-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.header-left { display: flex; align-items: center; gap: 16px; }
.title-box { display: flex; align-items: center; gap: 10px; }
.title-box h1 { font-size: 22px; font-weight: 800; color: #1C1C1E; margin: 0; letter-spacing: 0.5px;}
.icon-blue { font-size: 24px; color: #007AFF; }
.status-pill {
  display: flex; align-items: center; gap: 6px;
  background: #E8F8F0; color: #34C759; padding: 6px 12px;
  border-radius: 999px; font-size: 13px; font-weight: 600;
}
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #34C759; animation: blink 2s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

.header-right { display: flex; align-items: center; gap: 12px; }
:deep(.ios-select .el-input__inner ){
  background: #FFFFFF; border: 1px solid #E5E5EA; border-radius: 10px;
  height: 38px; line-height: 38px; color: #1C1C1E; font-weight: 500;
}
:deep(.ios-select .el-input__inner:focus ){ border-color: #007AFF; box-shadow: 0 0 0 2px rgba(0, 122, 255, 0.1); }
.farm-select { width: 220px; }
.ios-btn-secondary {
  background: #FFFFFF !important; border: 1px solid #E5E5EA !important; color: #007AFF !important;
  border-radius: 10px !important; font-weight: 600 !important; height: 38px;
}

/* ✨ 2. KPI 卡片 (精修呼吸感间距) */
.kpi-row { margin-bottom: 4px; }
.kpi-card { padding: 22px 20px; display: flex; align-items: center; gap: 16px; transition: transform 0.2s; cursor: default;}
.kpi-card:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,0.05); }
.kpi-icon-box {
  width: 52px; height: 52px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center; font-size: 26px;
}
.bg-blue { background: #E5F1FF; color: #007AFF; }
.bg-green { background: #E8F8F0; color: #34C759; }
.bg-orange { background: #FFF4E5; color: #FF9500; }
.bg-purple { background: #F4E8FF; color: #AF52DE; }
.kpi-info { display: flex; flex-direction: column; }
.kpi-label { font-size: 13px; color: #8E8E93; font-weight: 600; margin-bottom: 8px;} /* ✨ 增加底部留白 */
.kpi-value { font-size: 28px; font-weight: 800; color: #1C1C1E; line-height: 1;} /* 略微放大数值 */
.unit { font-size: 14px; font-weight: 600; color: #8E8E93; margin-left: 4px;}

/* ✨ 3. AI 卡片 (精修 iMessage 气泡) */
.middle-row { display: flex; align-items: stretch; }
.ai-card, .radar-card { height: 340px; display: flex; flex-direction: column; }
.ios-tag-loading { background: #F2F2F7; color: #8E8E93; border: none; font-weight: 600; border-radius: 6px;}
.ios-tag-success { background: #E8F8F0; color: #34C759; border: none; font-weight: 600; border-radius: 6px;}

.ai-body { padding: 20px; flex: 1; display: flex; flex-direction: column; justify-content: space-between; }
.ai-chat-container { display: flex; gap: 14px; align-items: flex-end; margin-bottom: 10px;}
.ai-avatar { 
  width: 42px; height: 42px; border-radius: 50%; background: white; 
  box-shadow: 0 4px 10px rgba(0,0,0,0.08); padding: 5px; flex-shrink: 0;
  margin-bottom: 4px; /* 对齐气泡底部 */
}
.ai-avatar img { width: 100%; height: 100%; object-fit: contain; }

/* iMessage 风格气泡 */
.ai-chat-bubble { 
  flex: 1;
  background: #F2F2F7; 
  padding: 14px 18px; 
  border-radius: 20px;
  border-bottom-left-radius: 4px; /* ✨ 形成气泡小尾巴 */
  position: relative;
}
.typing-text { font-size: 14px; color: #1C1C1E; line-height: 1.6; margin: 0; font-weight: 500;}
.cursor { display: inline-block; width: 2px; height: 14px; background: #007AFF; margin-left: 2px; animation: blink 1s infinite; vertical-align: middle;}

.ai-metrics { display: flex; gap: 16px; margin-top: 10px; }
.metric-item { flex: 1; background: #FFFFFF; border: 1px solid #E5E5EA; padding: 14px; border-radius: 14px; display: flex; flex-direction: column; justify-content: center;}
.metric-label { font-size: 12px; color: #8E8E93; font-weight: 600; margin-bottom: 8px;}
.metric-val { font-size: 16px; font-weight: 700; color: #1C1C1E;}
.text-blue { color: #007AFF; }

/* 4. 雷达卡片 */
.score-text { font-size: 15px; font-weight: 800; font-family: -apple-system; }
.chart-container { flex: 1; width: 100%; min-height: 240px; }
.chart-container.large { height: 320px; padding: 10px 20px;}

/* 5. 底部对比卡片 */
.compare-card { margin-top: 4px; }
.checkbox-group-ios { background: #F2F2F7; padding: 4px; border-radius: 8px; }
:deep(.checkbox-group-ios .el-checkbox.is-bordered ){ 
  border: none; background: transparent; padding: 6px 16px; height: auto; margin: 0; border-radius: 6px; transition: 0.2s;
}
:deep(.checkbox-group-ios .el-checkbox.is-bordered.is-checked ){
  background: #FFFFFF; box-shadow: 0 1px 3px rgba(0,0,0,0.1); 
}
:deep(.checkbox-group-ios .el-checkbox__input ){ display: none; }
:deep(.checkbox-group-ios .el-checkbox__label ){ padding: 0; color: #8E8E93; font-weight: 600; font-size: 13px;}
:deep(.checkbox-group-ios .el-checkbox.is-checked .el-checkbox__label ){ color: #1C1C1E; }
</style>