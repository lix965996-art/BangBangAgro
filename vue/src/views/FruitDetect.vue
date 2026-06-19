<template>
  <div class="fruit-detect-container">
    <el-row :gutter="20" class="summary-cards">
      <el-col :span="8">
        <div class="kpi-card">
          <div class="kpi-icon green"><i class="el-icon-picture-outline-round"></i></div>
          <div class="kpi-text">
            <span class="kpi-label">今日识别对象</span>
            <span class="kpi-value">{{ allDetections.length || 0 }}<span class="kpi-unit">项</span></span>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi-card">
          <div class="kpi-icon teal"><i class="el-icon-data-line"></i></div>
          <div class="kpi-text">
            <span class="kpi-label">当前样本成熟占比</span>
            <span class="kpi-value">{{ ripenessStats.riped_ratio || 0 }}<span class="kpi-unit">%</span></span>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi-card">
          <div class="kpi-icon coral"><i class="el-icon-warning-outline"></i></div>
          <div class="kpi-text">
            <span class="kpi-label">病害风险发现</span>
            <span :class="['kpi-value', { 'warning-pulse': diseaseDetections.length > 0 }]">{{ diseaseDetections.length || 0 }}<span class="kpi-unit">处</span></span>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="main-layout">
      
      <el-col :span="14">
        <el-card shadow="never" class="inference-sandbox">
          <template #header>
            <div class="clearfix sandbox-header">
              <div class="header-title-group">
                <span class="section-title"><i class="el-icon-magic-stick"></i> 视觉模型分析沙盒</span>
                <el-tooltip
                  placement="bottom"
                  :content="analysisServiceBadge.tip"
                >
                  <span :class="['service-badge', analysisServiceBadge.cls]">
                    <i :class="analysisServiceBadge.icon"></i>
                    {{ analysisServiceBadge.label }}
                  </span>
                </el-tooltip>
              </div>
  
              <el-radio-group v-model="mode" size="small" class="mode-switch">
                <el-radio-button v-for="item in modeOptions" :key="item.value" :label="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </div>
          </template>
          
          <div class="sandbox-content">
            <div class="inference-display" :class="{ processing: loading }">
              
              <div v-if="mode === 'upload'" class="stage-full">
                <label class="upload-drop" @dragover.prevent @drop.prevent="handleDrop">
                  <input type="file" accept="image/*" @change="handleImageChange" style="display: none;" />
                  
                  <div v-if="!stageImageSrc" class="demo-placeholder">
                     <img :src="demoInspecImg" alt="概念图" class="inference-image demo-blur"/>
                     <div class="placeholder-overlay">
                       <i class="el-icon-upload cloud-icon"></i>
                       <h3>点击或拖拽上传巡检图像</h3>
                       <p>支持 {{ availableCropLabels || '各类农作物' }}，智能判读成熟度与病虫害</p>
                     </div>
                  </div>

                  <img v-else :src="stageImageSrc" :alt="stageCaptionTitle" class="inference-image"/>
                </label>
              </div>

              <div v-else-if="mode === 'video'" class="stage-full">
                <label v-if="!videoUrl" class="upload-drop">
                  <input type="file" accept="video/*" @change="handleVideoChange" style="display: none;" />
                  <div class="demo-placeholder">
                    <i class="el-icon-video-camera cloud-icon"></i>
                    <h3>导入视频文件</h3>
                    <p>自动抽取关键帧进入 AI 分析流</p>
                  </div>
                </label>
                <div v-else class="video-container">
                  <video ref="videoPlayer" :src="videoUrl" controls class="inference-image"></video>
                </div>
              </div>

              <div v-else class="stage-full stream-container">
                <img v-if="streamActive" :src="mjpegUrl" alt="camera stream" class="inference-image" @error="handleStreamError"/>
                <div v-else class="demo-placeholder">
                  <i class="el-icon-video-camera-solid cloud-icon"></i>
                  <h3>等待接入实时画面</h3>
                  <p>请在下方配置 MJPEG 地址并启动</p>
                </div>
              </div>

              <div v-if="loading" class="scan-overlay">
                <div class="scan-bar"></div>
                <span class="scan-text">{{ engine === 'llm' ? '多模态视觉分析中...' : 'YOLO 模型推理中...' }}</span>
              </div>
            </div>
            
            <div class="inference-controls">
              <div class="control-row">
                <div class="control-item">
                  <span class="control-label">识别引擎</span>
                  <el-select v-model="engine" size="small" class="custom-select">
                    <el-option v-for="item in engineOptions" :key="item.value" :label="item.label" :value="item.value"/>
                  </el-select>
                </div>
                <div class="control-item">
                  <span class="control-label">识别模式</span>
                  <el-select v-model="detectMode" size="small" class="custom-select" :disabled="engine === 'llm'">
                    <el-option v-for="item in detectModeOptions" :key="item.value" :label="item.label" :value="item.value"/>
                  </el-select>
                </div>
                <div class="control-item">
                  <span class="control-label">作物品种{{ engine === 'llm' ? '（可留空自动识别）' : '' }}</span>
                  <el-select v-model="cropType" size="small" class="custom-select"
                    :disabled="engine !== 'llm' && detectMode === 'ripeness'"
                    :filterable="engine === 'llm'"
                    :allow-create="engine === 'llm'"
                    :clearable="engine === 'llm'"
                    default-first-option
                    :placeholder="engine === 'llm' ? '自动识别 / 输入任意作物' : '选择作物'">
                    <el-option v-for="item in cropOptionsForEngine" :key="item.value" :label="item.label" :value="item.value"/>
                  </el-select>
                </div>
                <div class="control-item">
                  <span class="control-label">关联地块</span>
                  <el-select v-model="selectedFarmId" size="small" class="custom-select" placeholder="选择地块">
                    <el-option v-for="farm in farms" :key="farm.id" :label="farm.farm" :value="farm.id"/>
                  </el-select>
                </div>
              </div>

              <el-divider class="soft-divider"></el-divider>

              <div class="action-row">
                <div class="aux-actions">
                   <template v-if="mode === 'video' && videoUrl">
                     <el-button size="small" plain @click="extractVideoFrame">抽取当前帧</el-button>
                     <el-button size="small" link @click="clearVideo">重选视频</el-button>
                   </template>
                   <template v-if="mode === 'stream'">
                     <el-input v-model.trim="mjpegUrl" size="small" placeholder="流媒体URL" class="stream-input">
                       <template #append>
                         <el-button @click="saveCameraUrl">保存</el-button>
                       </template>
                     </el-input>
                     <el-button size="small" plain @click="startStream" v-if="!streamActive">启动</el-button>
                     <el-button size="small" plain @click="captureFrame" v-if="streamActive">抓拍分析</el-button>
                     <el-button size="small" type="danger" plain @click="stopStream" v-if="streamActive">停止</el-button>
                   </template>
                </div>

                <el-button 
                  type="primary" 
                  class="analyze-btn" 
                  :loading="loading" 
                  :disabled="!selectedFile && mode === 'upload'" 
                  @click="analyzeCurrentFrame">
                  <i class="el-icon-cpu"></i> 执行 AI 分析
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" class="dataset-management decision-hub">
          <template #header>
            <div class="clearfix decision-header">
              <span class="section-title"><i class="el-icon-s-opportunity"></i> AI 智能决策中心</span>
              <el-tag :type="analysisSummary.level === 'high' ? 'danger' : analysisSummary.level === 'medium' ? 'warning' : 'success'" effect="dark" size="small">
                {{ stageStatusText }}
              </el-tag>
            </div>
          </template>
          
          <div class="decision-content">
            <div v-if="!analysisResult" class="empty-state">
              <img src="../assets/ai.png" alt="AI" class="empty-icon-ai"/>
              <p class="empty-text">{{ stageModeHint }}</p>
              <div class="suggestion-pills">
                 <span v-for="item in compactSuggestions" :key="item" class="suggestion-chip">{{ item }}</span>
              </div>
            </div>

            <transition name="fade-slide" mode="out-in">
              <div v-if="analysisResult" class="result-panel">
                
                <div :class="['alert-box', analysisSummary.level]">
                  <div class="alert-header">
                    <i :class="analysisSummary.level === 'high' ? 'el-icon-warning' : 'el-icon-success'"></i> 
                    <span>系统判定：{{ analysisSummary.mainLabel }}</span>
                  </div>
                  <div class="alert-body">
                    <strong>分析摘要：</strong>{{ analysisSummary.message }}<br>
                    <strong>最高置信度：</strong>{{ analysisSummary.confidence }}<br>
                    <strong>地块绑定：</strong>{{ selectedFarm ? selectedFarm.farm : '未绑定' }}
                  </div>
                </div>

                <div v-if="analysisResult && (analysisResult.ai_summary || analysisResult.ai_advice)" class="ai-narrative">
                  <div class="module-title"><i class="el-icon-cpu"></i> AI 多维分析</div>
                  <p v-if="analysisResult.ai_summary" class="narrative-text">{{ analysisResult.ai_summary }}</p>
                  <div v-if="analysisResult.ai_advice" class="narrative-advice">
                    <span class="advice-label">处置建议</span>
                    <p class="narrative-text">{{ analysisResult.ai_advice }}</p>
                  </div>
                </div>

                <div v-if="engine === 'llm' && dimensionRows.length" class="ai-dimensions">
                  <div class="module-title"><i class="el-icon-data-analysis"></i> 多维判读</div>
                  <div class="dim-grid">
                    <div v-for="row in dimensionRows" :key="row.key" class="dim-cell">
                      <span class="dim-key">{{ row.label }}</span>
                      <span class="dim-val">{{ row.value }}</span>
                    </div>
                  </div>
                </div>

                <div v-if="allDetections.length" class="ai-prescription">
                  <div class="module-title">对象识别清单 ({{ allDetections.length }}项)</div>
                  <div class="objects-list-clean">
                    <div v-for="(item, index) in allDetections" :key="index" class="object-row-clean">
                      <div class="obj-left">
                        <span :class="['source-dot', item.source === '成熟度' ? 'dot-green' : 'dot-red']"></span>
                        <strong class="obj-name">{{ displayDetectionName(item) }}</strong>
                      </div>
                      <div class="obj-right">
                        <span class="obj-conf">准确率: {{ formatConfidence(item.confidence) }}</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-if="ripenessStats.total" class="mini-chart">
                  <span class="chart-label">成熟度分布：{{ ripenessStats.riped_ratio }}% 可采摘</span>
                  <div class="progress-bar-stack">
                    <el-tooltip :content="`已熟 ${ripenessStats.riped_count} 项`" placement="top">
                      <div class="bar ripe" :style="{ width: ripenessRatioWidth }"></div>
                    </el-tooltip>
                    <el-tooltip :content="`未熟 ${ripenessStats.unriped_count} 项`" placement="top">
                      <div class="bar unripe" :style="{ width: `calc(100% - ${ripenessRatioWidth})` }"></div>
                    </el-tooltip>
                  </div>
                </div>

                <div class="action-panel">
                  <el-button 
                    type="primary" 
                    icon="el-icon-document-checked" 
                    class="action-btn core-action-btn" 
                    :disabled="!canCreateAlert" 
                    @click="pushToAlertCenter">
                    生成研判工单并写入后台
                  </el-button>
                  <el-button 
                    plain 
                    icon="el-icon-data-board" 
                    class="action-btn" 
                    @click="$router.push('/alert-center')">
                    跳转研判指挥中心
                  </el-button>
                </div>
              </div>
            </transition>

            <div v-if="engine === 'llm' && selectedFile" class="ai-ask">
              <div class="module-title"><i class="el-icon-chat-dot-round"></i> 对图提问<span class="ask-hint">（多模态视觉，可追问）</span></div>
              <div v-if="qaList.length" class="qa-list">
                <div v-for="(qa, i) in qaList" :key="i" class="qa-item">
                  <div class="qa-q"><span class="qa-tag">问</span><span class="qa-text">{{ qa.q }}</span></div>
                  <div class="qa-a"><span class="qa-tag a">答</span><span class="qa-text">{{ qa.a }}</span></div>
                </div>
              </div>
              <div class="qa-input-row">
                <el-input v-model="qaInput" size="small" placeholder="如：这叶子发黄是缺肥还是病害？" :disabled="qaLoading" @keyup.enter="askQuestion"/>
                <el-button type="primary" size="small" :loading="qaLoading" @click="askQuestion">发送</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
// 【导入新界面的图片资产】
import demoInspecImg from '@/assets/crops/demo_inspection.jpg';

// 这里保留你原来所有的常量和逻辑配置！完全不动你的后端交互！
const MODE_OPTIONS = [
  { value: 'upload', label: '本地图像' },
  { value: 'video', label: '视频文件' },
  { value: 'stream', label: '监控设备' }
];

const DETECT_MODE_OPTIONS = [
  { value: 'both', label: '成熟度与病害双检' },
  { value: 'disease', label: '仅病虫害识别' },
  { value: 'ripeness', label: '仅成熟度分析' }
];

const CROP_OPTIONS = [
  { value: 'tomato', label: '番茄' },
  { value: 'corn', label: '玉米' },
  { value: 'strawberry', label: '草莓' },
  { value: 'rice', label: '水稻' }
];

// 识别引擎：多模态视觉（任意作物、多维分析，可配豆包/Qwen-VL/硅基流动等）/ 本地 YOLO（固定模型、精确框选计数）
const ENGINE_OPTIONS = [
  { value: 'llm', label: '多模态视觉' },
  { value: 'yolo', label: '本地YOLO' }
];

const MODE_LABELS = {
  upload: '图片上传',
  video: '视频抽帧',
  stream: '摄像头巡检'
};

const DETECT_MODE_LABELS = {
  both: '成熟度 + 病虫害',
  disease: '病虫害识别',
  ripeness: '成熟度分析'
};

export default {
  name: 'FruitDetect',
  data() {
    return {
      demoInspecImg, // 注入占位图
      mode: 'upload',
      engine: 'llm', // 默认多模态视觉；可切回本地 YOLO
      llmAvailable: false, // 多模态视觉是否已配置可用
      llmModel: '', // 当前视觉模型标识
      qaList: [], // 对图问答记录 [{q,a}]
      qaInput: '', // 问答输入框
      qaLoading: false, // 问答请求中
      detectMode: 'both',
      cropType: 'tomato',
      stageView: 'annotated',
      mjpegUrl: localStorage.getItem('camera_stream_url') || '',
      streamActive: false,
      imagePreview: '',
      videoUrl: '',
      selectedFile: null,
      loading: false,
      analysisResult: null,
      farms: [],
      selectedFarmId: null,
      serviceOnline: false,
      serviceHintText: '正在检查服务',
      supportedCrops: [],
      dragActive: false,
      healthTimer: null
    };
  },
  // =============== 以下是你原来的 computed 和 methods，我一行都没有删！===============
  computed: {
    modeOptions() { return MODE_OPTIONS; },
    engineOptions() { return ENGINE_OPTIONS; },
    detectModeOptions() { return DETECT_MODE_OPTIONS; },
    modeLabel() { return MODE_LABELS[this.mode] || '检测输入'; },
    detectModeLabel() { return DETECT_MODE_LABELS[this.detectMode] || 'AI 识别'; },
    currentCropLabel() {
      const current = CROP_OPTIONS.find(item => item.value === this.cropType);
      return current ? current.label : '未设置';
    },
    serviceNote() { return this.serviceOnline ? `支持 ${this.availableCropLabels}` : this.serviceHintText; },
    /** 摄像头/流媒体与分析服务是两条链路，避免画面已出仍显示「引擎离线」造成误解 */
    analysisServiceBadge() {
      if (this.engine === 'llm') {
        if (this.llmAvailable) {
          return {
            cls: 'online',
            icon: 'el-icon-success',
            label: '多模态视觉在线',
            tip: `视觉模型已就绪（${this.llmModel || '多模态'}），可执行「执行 AI 分析」，支持任意作物多维判读。`
          };
        }
        return {
          cls: 'offline',
          icon: 'el-icon-error',
          label: '视觉模型未配置',
          tip: '请到「个人中心 → AI 模型配置 → 视觉模型」填写视觉 Key/URL/模型，或切换到「本地YOLO」引擎。'
        };
      }
      if (this.serviceOnline) {
        return {
          cls: 'online',
          icon: 'el-icon-success',
          label: '分析服务在线',
          tip: '智能识别服务已就绪，可执行「执行 AI 分析」。'
        };
      }
      if (this.streamActive) {
        return {
          cls: 'degraded',
          icon: 'el-icon-warning-outline',
          label: '画面已接通 · 分析服务离线',
          tip:
            '当前监控画面已接通，但智能识别服务暂不可用。请检查分析服务状态后重试。'
        };
      }
      return {
        cls: 'offline',
        icon: 'el-icon-error',
        label: '分析服务离线',
        tip: '智能识别服务暂不可用，请检查分析服务状态后重试。'
      };
    },
    selectedFarm() { return this.farms.find(item => item.id === this.selectedFarmId) || null; },
    availableCropOptions() {
      if (!this.supportedCrops.length) return CROP_OPTIONS;
      return CROP_OPTIONS.filter(item => this.supportedCrops.includes(item.value));
    },
    availableCropLabels() { return this.availableCropOptions.map(item => item.label).join(' / '); },
    cropOptionsForEngine() {
      // 豆包引擎：4 种作物仅作快捷建议，可自由输入任意作物；YOLO：受本地模型限制
      return this.engine === 'llm' ? CROP_OPTIONS : this.availableCropOptions;
    },
    dimensionRows() {
      const d = this.analysisResult && this.analysisResult.dimensions;
      if (!d) return [];
      const labels = { growth_stage: '生长阶段', nutrition: '营养状况', pest: '虫害情况', quality: '品质分级', harvest: '采收期预估' };
      return Object.keys(labels).filter(k => d[k]).map(k => ({ key: k, label: labels[k], value: d[k] }));
    },
    resultImageSrc() {
      if (!this.analysisResult) return '';
      if (this.analysisResult.result_image) return this.analysisResult.result_image;
      if (this.analysisResult.disease_analysis && this.analysisResult.disease_analysis.result_image) return this.analysisResult.disease_analysis.result_image;
      if (this.analysisResult.ripeness_analysis && this.analysisResult.ripeness_analysis.result_image) return this.analysisResult.ripeness_analysis.result_image;
      return '';
    },
    stageImageSrc() {
      if (this.stageView === 'raw' || !this.resultImageSrc) return this.imagePreview;
      return this.resultImageSrc;
    },
    showStageToggle() { return !!this.imagePreview && !!this.resultImageSrc; },
    stageCaptionLabel() {
      if (!this.stageImageSrc) return '';
      return this.stageView === 'raw' || !this.resultImageSrc ? '原图' : '识别结果';
    },
    stageCaptionTitle() { return this.selectedFarm ? this.selectedFarm.farm : '当前任务'; },
    inputReady() { return !!this.selectedFile; },
    stageStatusText() {
      if (this.loading) return '分析引擎运转中...';
      if (this.analysisResult) return '分析报告已生成';
      if (this.inputReady || this.videoUrl || this.streamActive) return '画面已捕获就绪';
      return '等待输入图像样本';
    },
    stageModeHint() {
      if (this.mode === 'upload') return this.inputReady ? '样本已就绪，可直接发起识别' : '上传图片后直接进入识别流程';
      if (this.mode === 'video') return this.videoUrl ? '先抽取关键帧，再转入图片识别' : '导入视频后抽取关键帧';
      return this.streamActive ? '当前可抓拍并写入识别流程' : '连接摄像头后启动巡检';
    },
    ripenessDetections() {
      if (!this.analysisResult) return [];
      if (Array.isArray(this.analysisResult.detections) && this.detectMode === 'ripeness') return this.analysisResult.detections.map(item => Object.assign({}, item, { source: '成熟度' }));
      if (this.analysisResult.ripeness_analysis && Array.isArray(this.analysisResult.ripeness_analysis.detections)) return this.analysisResult.ripeness_analysis.detections.map(item => Object.assign({}, item, { source: '成熟度' }));
      return [];
    },
    diseaseDetections() {
      if (!this.analysisResult) return [];
      if (Array.isArray(this.analysisResult.detections) && this.detectMode === 'disease') return this.analysisResult.detections.map(item => Object.assign({}, item, { source: '病虫害' }));
      if (this.analysisResult.disease_analysis && Array.isArray(this.analysisResult.disease_analysis.detections)) return this.analysisResult.disease_analysis.detections.map(item => Object.assign({}, item, { source: '病虫害' }));
      return [];
    },
    allDetections() {
      return this.detectMode === 'ripeness' ? this.ripenessDetections : this.ripenessDetections.concat(this.diseaseDetections);
    },
    ripenessStats() {
      if (!this.analysisResult) return {};
      if (this.detectMode === 'ripeness') return this.analysisResult.statistics || {};
      return (this.analysisResult.ripeness_analysis && this.analysisResult.ripeness_analysis.statistics) || {};
    },
    highestConfidenceValue() {
      const values = this.allDetections.map(item => this.normalizeConfidence(item.confidence)).filter(item => item !== null);
      if (!values.length) return 0;
      return Math.round(Math.max.apply(null, values) * 100);
    },
    summaryMeta() {
      return [
        { label: '对象数量', value: String(this.allDetections.length) },
        { label: '检测时间', value: this.analysisSummary.detectTime },
        { label: '成熟占比', value: this.ripenessStats.total ? `${this.ripenessStats.riped_ratio || 0}%` : '--' },
        { label: '关联地块', value: this.selectedFarm ? this.selectedFarm.farm : '未绑定' }
      ];
    },
    confidenceRingStyle() {
      const degree = Math.max(0, Math.min(this.analysisSummary.confidenceValue, 100)) * 3.6;
      const colorMap = { steady: '#1f8a70', medium: '#b77934', high: '#bf5555' };
      const color = colorMap[this.analysisSummary.level] || colorMap.steady;
      return { background: `conic-gradient(${color} 0deg ${degree}deg, rgba(209, 220, 214, 0.5) ${degree}deg 360deg)` };
    },
    ripenessRatioWidth() {
      const ratio = this.ripenessStats.riped_ratio || 0;
      return `${Math.max(0, Math.min(Number(ratio), 100))}%`;
    },
    compactSuggestions() {
      return (this.analysisSummary.suggestions || []).slice(0, 3);
    },
    analysisSummary() {
      if (!this.analysisResult) {
        return {
          level: 'steady', levelLabel: '待识别', mainLabel: '系统待命中', confidence: '--', confidenceValue: 0,
          detectTime: '--', actionLabel: '准备阶段', message: '请在左侧上传作物图像或接通监控源，即可启动 AI 智能识别。',
          suggestions: ['步骤1: 选择地块', '步骤2: 确认模型引擎在线', '步骤3: 点击执行分析']
        };
      }

      const detectTime = this.analysisResult.detect_time || '--';
      const riskyDetections = this.diseaseDetections.filter(item => {
        const name = String(item.class_name || '').toLowerCase();
        return name.indexOf('healthy') === -1 && name.indexOf('health') === -1;
      }).sort((a, b) => {
        const aValue = this.normalizeConfidence(a.confidence) || 0;
        const bValue = this.normalizeConfidence(b.confidence) || 0;
        return bValue - aValue;
      });

      if (riskyDetections.length) {
        const main = riskyDetections[0];
        const confidenceValue = Math.round((this.normalizeConfidence(main.confidence) || 0) * 100);
        const highRisk = confidenceValue >= 80 || riskyDetections.length >= 3;
        return {
          level: highRisk ? 'high' : 'medium', levelLabel: highRisk ? '高风险' : '需关注', mainLabel: this.displayDetectionName(main),
          confidence: `${confidenceValue}%`, confidenceValue, detectTime, actionLabel: highRisk ? '尽快复核' : '持续观察',
          message: `自动巡检发现高概率的 [${this.displayDetectionName(main)}] 风险点，存在扩散可能。`,
          suggestions: ['立刻生成研判工单', '推荐指派飞防任务', '结合土壤湿度综合分析']
        };
      }

      if (this.ripenessStats.total) {
        const ratio = Number(this.ripenessStats.riped_ratio || 0);
        if (ratio >= 75) {
          return {
            level: 'steady', levelLabel: '可采收', mainLabel: '果实已充分成熟', confidence: `${this.highestConfidenceValue || ratio}%`,
            confidenceValue: this.highestConfidenceValue || ratio, detectTime, actionLabel: '安排采收',
            message: `此区域作物达标率达 ${ratio}%，处于最佳采收及口感窗口期。`,
            suggestions: ['生成前端采摘工单', '匹配近期采购单', '预测产值与收益']
          };
        }
        if (ratio >= 40) {
          return {
            level: 'medium', levelLabel: '转色期', mainLabel: '处于生长期-转色', confidence: `${this.highestConfidenceValue || ratio}%`,
            confidenceValue: this.highestConfidenceValue || ratio, detectTime, actionLabel: '继续巡检',
            message: '当前样本处于快速转色期，建议维持水肥节奏，密切关注后续气候变化。',
            suggestions: ['保持规律巡检', '关注近期气温聚变', '预防成熟期病害']
          };
        }

        return {
          level: 'steady', levelLabel: '生长期', mainLabel: '未达采收标准', confidence: `${this.highestConfidenceValue || ratio}%`,
          confidenceValue: this.highestConfidenceValue || ratio, detectTime, actionLabel: '维持养护',
          message: '整体成熟度偏低，未发现病虫害，作物长势健康稳定。',
          suggestions: ['无需特殊干预', '积攒长期生长数据模型', '维持当前排班']
        };
      }

      return {
        level: 'steady', levelLabel: '稳定', mainLabel: '作物表现健康', confidence: `${this.highestConfidenceValue || 0}%`,
        confidenceValue: this.highestConfidenceValue, detectTime, actionLabel: '保持巡检',
        message: '本次巡检未发现病虫害及异常斑点，植株状态良好。',
        suggestions: ['自动归档健康记录', '结束当前检查', '更新环境档案']
      };
    },
    canCreateAlert() { return !!this.analysisResult && !!this.selectedFarm; }
  },
  watch: {
    // 切引擎：豆包强制走「综合分析」(both 结构)；切回 YOLO 时把作物类型回退到其支持的英文值
    engine(val) {
      if (val === 'llm') {
        this.detectMode = 'both';
      } else {
        const valid = CROP_OPTIONS.some(o => o.value === this.cropType);
        if (!valid) {
          const first = this.availableCropOptions[0];
          this.cropType = first ? first.value : 'tomato';
        }
      }
      this.analysisResult = null;
      this.qaList = [];
      this.qaInput = '';
      this.stageView = this.imagePreview ? 'raw' : 'annotated';
    }
  },
  mounted() { this.initializePage(); },
  beforeUnmount() {
    this.revokePreview();
    this.revokeVideo();
    if (this.healthTimer) clearInterval(this.healthTimer);
  },
  methods: {
    async initializePage() {
      await Promise.allSettled([this.fetchFarms(), this.fetchServiceStatus(), this.fetchLlmStatus()]);
      this.healthTimer = setInterval(() => { this.fetchServiceStatus(); this.fetchLlmStatus(); }, 30000);
    },
    async fetchLlmStatus() {
      try {
        const res = await this.request.get('/crop-analysis/llm/health');
        const ok = res && (res.code === '200' || res.code === 200) && res.data;
        this.llmAvailable = !!(ok && res.data.configured);
        this.llmModel = ok ? (res.data.model || '') : '';
      } catch (error) {
        this.llmAvailable = false;
        this.llmModel = '';
      }
    },
    normalizeConfidence(value) {
      if (value === null || value === undefined || Number.isNaN(Number(value))) return null;
      const num = Number(value);
      if (num <= 1) return Math.max(0, Math.min(num, 1));
      return Math.max(0, Math.min(num / 100, 1));
    },
    formatConfidence(value) {
      const normalized = this.normalizeConfidence(value);
      return normalized === null ? '--' : `${Math.round(normalized * 100)}%`;
    },
    formatBbox(bbox) {
      if (!bbox) return '无坐标';
      return `(${bbox.x1}, ${bbox.y1}) - (${bbox.x2}, ${bbox.y2})`;
    },
    displayDetectionName(item) { return item.class_name_ch || item.class_name || '未命名对象'; },
    revokePreview() {
      if (this.imagePreview) { URL.revokeObjectURL(this.imagePreview); this.imagePreview = ''; }
    },
    revokeVideo() {
      if (this.videoUrl) { URL.revokeObjectURL(this.videoUrl); this.videoUrl = ''; }
    },
    async fetchFarms() {
      const res = await this.request.get('/statistic/page', { params: { pageNum: 1, pageSize: 1000 } });
      if (res.code === '200' && res.data) {
        this.farms = res.data.records || [];
        if (this.farms.length && !this.selectedFarmId) this.selectedFarmId = this.farms[0].id;
      }
    },
    async fetchServiceStatus() {
      try {
        const [healthRes, modelsRes] = await Promise.all([this.request.get('/crop-analysis/health'), this.request.get('/crop-analysis/models')]);
        const healthOk = healthRes && (healthRes.code === '200' || healthRes.code === 200);
        this.serviceOnline = !!healthOk;
        this.serviceHintText = this.serviceOnline ? '模型就绪' : (healthRes && healthRes.msg) || '离线';
        if ((modelsRes.code === '200' || modelsRes.code === 200) && modelsRes.data && modelsRes.data.disease_models) {
          this.supportedCrops = Object.keys(modelsRes.data.disease_models);
        } else if (healthOk && healthRes.data && Array.isArray(healthRes.data.supported_crops)) {
          this.supportedCrops = healthRes.data.supported_crops;
        } else {
          this.supportedCrops = [];
        }
        const available = this.supportedCrops.length ? CROP_OPTIONS.filter(item => this.supportedCrops.includes(item.value)) : CROP_OPTIONS;
        if (available.length && !available.find(item => item.value === this.cropType)) this.cropType = available[0].value;
      } catch (error) {
        this.serviceOnline = false;
        this.serviceHintText = '无法连接服务';
        this.supportedCrops = [];
      }
    },
    saveCameraUrl() {
      if (!this.mjpegUrl) { this.$message.warning('请输入摄像头地址'); return; }
      localStorage.setItem('camera_stream_url', this.mjpegUrl);
      this.$message.success('摄像头地址已保存');
    },
    startStream() {
      if (!this.mjpegUrl) { this.$message.warning('请输入摄像头地址'); return; }
      this.streamActive = true;
    },
    stopStream() { this.streamActive = false; },
    handleStreamError() {
      this.streamActive = false;
      this.$message.error('画面加载失败');
    },
    setImageFile(file) {
      if (!file) return;
      if (!file.type || file.type.indexOf('image/') !== 0) { this.$message.warning('请上传图片'); return; }
      this.revokePreview();
      this.selectedFile = file;
      this.imagePreview = URL.createObjectURL(file);
      this.analysisResult = null;
      this.qaList = [];
      this.qaInput = '';
      this.stageView = 'raw';
    },
    handleDrop(event) {
      this.dragActive = false;
      const file = event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files[0];
      this.setImageFile(file);
    },
    handleImageChange(event) {
      const file = event.target.files && event.target.files[0];
      this.setImageFile(file);
      event.target.value = '';
    },
    handleVideoChange(event) {
      const file = event.target.files && event.target.files[0];
      if (!file) return;
      if (!file.type || file.type.indexOf('video/') !== 0) { this.$message.warning('请上传视频'); event.target.value = ''; return; }
      this.revokePreview();
      this.revokeVideo();
      this.analysisResult = null;
      this.selectedFile = null;
      this.videoUrl = URL.createObjectURL(file);
      event.target.value = '';
    },
    clearVideo() {
      this.revokeVideo();
      this.selectedFile = null;
      this.analysisResult = null;
    },
    extractVideoFrame() {
      const video = this.$refs.videoPlayer;
      if (!video) return;
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth || 1280;
      canvas.height = video.videoHeight || 720;
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
      canvas.toBlob(blob => {
        if (!blob) return;
        const file = new File([blob], `video-frame-${Date.now()}.jpg`, { type: 'image/jpeg' });
        this.setImageFile(file);
        this.mode = 'upload';
        this.$message.success('抽帧成功，可分析');
      }, 'image/jpeg', 0.95);
    },
    captureFrame() {
      const streamImage = this.$el.querySelector('.stream-container img');
      if (!streamImage) { this.$message.warning('请先启动实时画面'); return; }
      const canvas = document.createElement('canvas');
      canvas.width = streamImage.naturalWidth || 1280;
      canvas.height = streamImage.naturalHeight || 720;
      canvas.getContext('2d').drawImage(streamImage, 0, 0, canvas.width, canvas.height);
      canvas.toBlob(blob => {
        if (!blob) return;
        const file = new File([blob], `capture-${Date.now()}.jpg`, { type: 'image/jpeg' });
        this.setImageFile(file);
        this.mode = 'upload';
        this.$message.success('画面抓拍成功');
      }, 'image/jpeg', 0.95);
    },
    buildEndpoint() {
      if (this.detectMode === 'ripeness') return '/crop-analysis/ripeness';
      if (this.detectMode === 'disease') return '/crop-analysis/disease';
      return '/crop-analysis/both';
    },
    async analyzeCurrentFrame() {
      if (!this.selectedFile) { this.$message.warning('尚未输入有效图像'); return; }
      if (this.engine === 'llm' && !this.llmAvailable) {
        this.$message.warning('视觉模型未配置，请到「个人中心 → AI 模型配置 → 视觉模型」填写，或切换到本地YOLO引擎');
        return;
      }
      this.loading = true;
      try {
        const formData = new FormData();
        formData.append('file', this.selectedFile);
        let endpoint;
        if (this.engine === 'llm') {
          // 豆包多模态：作物类型仅作提示，模型自行判定
          formData.append('crop_type', this.cropType);
          endpoint = '/crop-analysis/llm';
        } else {
          endpoint = this.buildEndpoint();
          if (this.detectMode !== 'ripeness') {
            formData.append('crop_type', this.cropType);
            formData.append('conf', '0.5');
          }
        }
        const res = await this.request.post(endpoint, formData);
        if (res.code !== '200') throw new Error(res.msg || '引擎分析异常');
        this.analysisResult = res.data || {};
        this.stageView = this.resultImageSrc ? 'annotated' : 'raw';
        this.$message.success('AI 解析生成完毕');
      } catch (error) {
        const fallback = this.engine === 'llm' ? '识别失败，请检查视觉模型配置与网络' : '识别失败，请检查 Python 引擎';
        this.$message.error(error.message || fallback);
      } finally {
        this.loading = false;
      }
    },
    buildAlertPayload() {
      const ripenessRatio = this.ripenessStats.riped_ratio || 0;
      const message = this.detectMode === 'ripeness' ? `AI 测算此区域可采摘成熟度约 ${ripenessRatio}%` : `巡检雷达发现 ${this.analysisSummary.mainLabel} 异常`;
      const suggestion = this.analysisSummary.suggestions[0] || '系统建议安排人工实地复核';
      return {
        farmlandId: this.selectedFarm.id, farmlandName: this.selectedFarm.farm, alertType: 'visual',
        alertLevel: this.analysisSummary.level === 'high' ? 'high' : 'medium',
        currentValue: this.detectMode === 'ripeness' ? ripenessRatio : this.analysisSummary.confidenceValue,
        message, suggestion
      };
    },
    async askQuestion() {
      const q = (this.qaInput || '').trim();
      if (!q) { this.$message.warning('请输入问题'); return; }
      if (!this.selectedFile) { this.$message.warning('请先选择或抓拍一张图片'); return; }
      if (!this.llmAvailable) { this.$message.warning('视觉模型未配置，请到「个人中心 → AI 模型配置 → 视觉模型」填写'); return; }
      this.qaLoading = true;
      try {
        const fd = new FormData();
        fd.append('file', this.selectedFile);
        fd.append('question', q);
        if (this.qaList.length) {
          fd.append('history', this.qaList.map(p => `问：${p.q}\n答：${p.a}`).join('\n'));
        }
        const res = await this.request.post('/crop-analysis/llm/ask', fd);
        if (res.code !== '200') throw new Error(res.msg || '提问失败');
        this.qaList.push({ q, a: (res.data && res.data.answer) || '（模型未返回回答）' });
        this.qaInput = '';
      } catch (error) {
        this.$message.error(error.message || '提问失败，请检查视觉模型配置与网络');
      } finally {
        this.qaLoading = false;
      }
    },
    async pushToAlertCenter() {
      if (!this.canCreateAlert) return;
      try {
        const res = await this.request.post('/alert/manual', this.buildAlertPayload());
        if (res.code !== '200') throw new Error(res.msg || '工单入库失败');
        this.$message.success('研判工单已生成并推送至业务中心！');
      } catch (error) {
        this.$message.error(error.message || '写入系统失败');
      }
    }
  }
};
</script>

<style scoped>
/* ========================================================== */
/* 新一代 Digital Organic 极简数字农业 UI 设计 */
/* ========================================================== */
.fruit-detect-container {
  padding: 24px;
  background-color: #f8faf9; /* 柔和奶油白底色 */
  min-height: 100vh;
  width: 100%;
  max-width: 1680px;
  margin: 0 auto;
}

/* --- 1. 顶部 KPI 卡片（白卡 + 柔色图标，克制端庄）--- */
.summary-cards { margin-bottom: 20px; }
.kpi-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border: 1px solid #ebf0ed;
  border-radius: 14px;
  padding: 18px 22px;
  box-shadow: 0 4px 18px rgba(47, 72, 59, 0.05);
  transition: transform .2s ease, box-shadow .2s ease;
}
.kpi-card:hover { transform: translateY(-2px); box-shadow: 0 10px 26px rgba(47, 72, 59, 0.08); }
.kpi-icon {
  width: 48px; height: 48px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}
.kpi-icon.green { background: #e7f1ea; color: #2f8a5f; }
.kpi-icon.teal  { background: #e3eeec; color: #2f807a; }
.kpi-icon.coral { background: #fdeee6; color: #e8825a; }
.kpi-text { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.kpi-label { font-size: 13px; color: #8a958f; font-weight: 500; letter-spacing: .2px; }
.kpi-value { font-size: 28px; font-weight: 750; color: #1f2d27; line-height: 1; }
.kpi-unit { font-size: 15px; font-weight: 600; color: #9aa6a0; margin-left: 3px; }

/* 病害风险呼吸（>0 时变珊瑚色并轻微脉动）*/
.warning-pulse { color: #e8825a; animation: pulse 2s infinite; }
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); }
}

/* --- 2. 主体布局 --- */
.main-layout { display: flex; align-items: stretch; }
.el-card__header {
  border-bottom: 1px solid #e8ebe8;
  background-color: transparent !important;
  padding: 16px 20px;
}
.sandbox-header, .decision-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-title-group { display: flex; align-items: center; gap: 16px; }
.section-title { color: #1e293b; font-weight: 700; font-size: 16px; }
.section-title i { margin-right: 8px; color: #517161;}

/* 引擎状态徽章 */
.service-badge {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 600;
}
.service-badge.online { color: #34d399; background: rgba(52, 211, 153, 0.1); }
.service-badge.offline { color: #ff9b74; background: rgba(255, 155, 116, 0.1); }
.service-badge.degraded { color: #b45309; background: rgba(245, 158, 11, 0.14); }

/* 原生 Element UI 单选按钮美化 */
:deep(.mode-switch .el-radio-button__inner ){
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  color: #64748b;
  background: white;
  box-shadow: none !important;
  margin-left: 8px;
}
:deep(.mode-switch .el-radio-button__orig-radio:checked + .el-radio-button__inner ){
  background-color: #517161;
  border-color: #517161;
  color: white;
}

/* --- 3. 左侧视觉沙盒 --- */
.inference-sandbox { border-radius: 16px; background: white; border: 1px solid #ebf0ed; box-shadow: 0 4px 20px rgba(47, 72, 59, 0.05); height: 100%; display: flex; flex-direction: column;}
:deep(.inference-sandbox .el-card__body ){ padding: 16px; flex: 1; display: flex; flex-direction: column; }

/* 图像渲染区 */
.inference-display {
  position: relative; 
  border-radius: 12px;
  overflow: hidden;
  border: 2px dashed #cbdecb; /* 淡淡的虚线框 */
  background-color: #f0f3f1;
  flex: 1;
  min-height: clamp(360px, 42vh, 560px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.inference-display.processing { border-color: #739d89; border-style: solid;}

.stage-full { width: 100%; height: 100%; }
.upload-drop { width: 100%; height: 100%; cursor: pointer; display: block; position: relative;}

/* Demo 占位图蒙版 */
.demo-placeholder { width: 100%; height: 100%; position: relative; display: flex; align-items: center; justify-content: center; flex-direction: column;}
.demo-blur { filter: blur(4px) opacity(0.6); position: absolute; inset: 0; z-index: 0; }
.placeholder-overlay { position: relative; z-index: 1; text-align: center; color: #517161;}
.cloud-icon { font-size: 54px; color: #739d89; margin-bottom: 12px;}
.placeholder-overlay h3 { margin: 0; font-size: 20px; font-weight: 700; color: #1e293b;}
.placeholder-overlay p { margin-top: 8px; font-size: 13px; color: #64748b;}

/* 识别图与视频 */
.inference-image { width: 100%; height: 100%; object-fit: contain; z-index: 2; position: relative;}
.video-container, .stream-container { width: 100%; height: 100%; background: #000; }

/* AI 扫描动画 */
.scan-overlay {
  position: absolute; inset: 0; z-index: 10;
  background: rgba(255,255,255,0.7);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.scan-bar { width: 60%; height: 4px; background: linear-gradient(90deg, transparent, #739d89, transparent); animation: scan 1.5s infinite ease-in-out; border-radius: 2px;}
.scan-text { margin-top: 16px; font-weight: 600; color: #517161; letter-spacing: 1px;}
@keyframes scan { 0% { transform: translateX(-50%); opacity: 0; } 50% { opacity: 1; } 100% { transform: translateX(50%); opacity: 0; } }

/* 底部操作区 */
.inference-controls { margin-top: 16px; background: #f6f9f7; padding: 18px; border-radius: 12px; border: 1px solid #eef2f0; }
.control-row { display: flex; gap: 16px; }
.control-item { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 7px;}
.control-label { color: #6b7770; font-size: 12px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
:deep(.custom-select .el-input__wrapper ){ border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: none; }
:deep(.custom-select .el-input__wrapper.is-focus ){ border-color: #739d89 !important; box-shadow: none; }
:deep(.custom-select .el-input__inner ){ border: none; border-radius: 0; box-shadow: none; }
:deep(.custom-select .el-input__inner:focus ){ border: none !important; box-shadow: none; }

.soft-divider { margin: 16px 0; background-color: #e8ebe8; }

.action-row { display: flex; justify-content: space-between; align-items: center;}
.aux-actions { display: flex; gap: 8px;}
.stream-input { width: 220px; }
.analyze-btn {
  background: linear-gradient(135deg, #517161, #2f483b);
  border: none;
  border-radius: 8px;
  padding: 10px 24px;
  box-shadow: 0 4px 12px rgba(81, 113, 97, 0.3);
  transition: transform 0.2s;
}
.analyze-btn:hover { transform: translateY(-2px); }

/* --- 4. 右侧决策中枢 --- */
.decision-hub { border-radius: 16px; background: white; border: 1px solid #ebf0ed; box-shadow: 0 4px 20px rgba(47, 72, 59, 0.05); height: 100%; display: flex; flex-direction: column;}
:deep(.decision-hub .el-card__body ){ padding: 20px; flex: 1; }

.empty-state { text-align: center; margin-top: 60px; }
.empty-icon-ai { width: 120px; opacity: 0.6; margin-bottom: 20px;}
.empty-text { color: #64748b; font-size: 15px; margin-bottom: 20px;}
.suggestion-pills { display: flex; justify-content: center; gap: 8px; flex-wrap: wrap;}
.suggestion-chip { background: #f0f3f1; color: #739d89; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 600;}

/* 分析结果面板 */
.alert-box { border-radius: 12px; padding: 16px; margin-bottom: 24px; border-left: 4px solid; }
.alert-box.high { background-color: #fff5f5; border-left-color: #ff9b74; }
.alert-box.medium { background-color: #fffbef; border-left-color: #f59e0b; }
.alert-box.steady { background-color: #f0fdf4; border-left-color: #34d399; }

.alert-header { display: flex; align-items: center; font-size: 15px; font-weight: 700; margin-bottom: 8px; }
.alert-box.high .alert-header { color: #d84315; }
.alert-box.medium .alert-header { color: #b45309; }
.alert-box.steady .alert-header { color: #166534; }
.alert-header i { margin-right: 8px; font-size: 18px; }
.alert-body { font-size: 13px; color: #475569; line-height: 1.8; }

/* AI 多维分析叙述卡 */
.ai-narrative {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #f0f7f3, #eef3f6);
  border: 1px solid #d9e6df;
  border-radius: 12px;
  padding: 14px 16px;
}
.ai-narrative .module-title { border-bottom: none; margin-bottom: 8px; padding-bottom: 0; }
.ai-narrative .module-title i { color: #517161; margin-right: 6px; }
.narrative-text { font-size: 13px; color: #3f4a44; line-height: 1.8; margin: 0; white-space: pre-line; }
.narrative-advice { margin-top: 12px; padding-top: 12px; border-top: 1px dashed #cfdcd4; }
.advice-label { display: inline-block; font-size: 12px; font-weight: 700; color: #2f6b4f; background: #dff0e6; padding: 2px 10px; border-radius: 999px; margin-bottom: 6px; }

/* 多维判读网格 */
.ai-dimensions { margin-bottom: 24px; }
.dim-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.dim-cell { background: #f8faf9; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 12px; display: flex; flex-direction: column; gap: 2px; }
.dim-key { font-size: 11px; color: #94a3b8; font-weight: 600; }
.dim-val { font-size: 13px; color: #1e293b; line-height: 1.5; }

/* 对图提问 */
.ai-ask { margin-top: 8px; border-top: 1px dashed #e2e8f0; padding-top: 16px; }
.ask-hint { font-size: 12px; color: #94a3b8; font-weight: 400; margin-left: 6px; }
.qa-list { max-height: 220px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; margin-bottom: 12px; padding-right: 4px; }
.qa-list::-webkit-scrollbar { width: 4px; }
.qa-list::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.qa-item { display: flex; flex-direction: column; gap: 6px; }
.qa-q, .qa-a { display: flex; gap: 8px; font-size: 13px; line-height: 1.7; }
.qa-q .qa-text { color: #1e293b; font-weight: 600; }
.qa-a .qa-text { color: #475569; background: #f0f7f3; border-radius: 8px; padding: 8px 12px; flex: 1; white-space: pre-line; }
.qa-tag { flex-shrink: 0; width: 20px; height: 20px; border-radius: 6px; font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: center; color: #fff; background: #739d89; }
.qa-tag.a { background: #517161; }
.qa-input-row { display: flex; gap: 8px; }

/* 识别清单列表 */
.ai-prescription { margin-bottom: 24px; }
.module-title { font-size: 14px; font-weight: 700; color: #1e293b; margin-bottom: 12px; border-bottom: 2px solid #e8ebe8; padding-bottom: 8px; }
.objects-list-clean { max-height: 180px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding-right: 4px;}
.objects-list-clean::-webkit-scrollbar { width: 4px; }
.objects-list-clean::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.object-row-clean { display: flex; justify-content: space-between; background: #f8faf9; padding: 10px 14px; border-radius: 8px; border: 1px solid #e2e8f0;}
.obj-left { display: flex; align-items: center; gap: 8px;}
.source-dot { width: 8px; height: 8px; border-radius: 50%; }
.dot-green { background: #34d399; }
.dot-red { background: #ff9b74; }
.obj-name { font-size: 13px; color: #1e293b;}
.obj-conf { font-size: 12px; color: #64748b;}

/* 进度条看板 */
.mini-chart { margin-bottom: 24px; background: #f8faf9; padding: 14px; border-radius: 12px; border: 1px solid #e2e8f0;}
.chart-label { font-size: 13px; color: #1e293b; margin-bottom: 10px; display: block; font-weight: 600; }
.progress-bar-stack { display: flex; height: 12px; border-radius: 6px; overflow: hidden; background: #e2e8f0; }
.bar { height: 100%; transition: width 0.5s ease; cursor: pointer; }
.bar.ripe { background-color: #ff7a59; }
.bar.unripe { background-color: #a7c0b0; }

/* 底部行动按钮 */
.action-panel { display: flex; flex-direction: column; gap: 12px;}
.action-btn { width: 100%; border-radius: 8px; font-weight: 600; padding: 12px; margin-left: 0 !important;}
.core-action-btn { background-color: #739d89; border-color: #739d89; }
.core-action-btn:hover { background-color: #517161; border-color: #517161; }

.fade-slide-enter-active, .fade-slide-leave-active { transition: all 0.4s ease; }
.fade-slide-enter, .fade-slide-leave-to { opacity: 0; transform: translateX(20px); }

@media (max-width: 1200px) {
  .summary-cards :deep(.el-col),
  .main-layout :deep(.el-col) {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }

  .main-layout {
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .fruit-detect-container {
    padding: 16px;
  }

  .sandbox-header,
  .decision-header,
  .control-row,
  .action-row {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .aux-actions {
    flex-wrap: wrap;
  }

  .stream-input {
    width: 100%;
  }
}
</style>
