<template>
  <div class="iot-vision">
    <section class="vision-hero">
      <div>
        <p class="hero-kicker">Vision Assist</p>
        <h1>视觉巡检辅助</h1>
        <p class="hero-summary">
          保留摄像头实时流、图片上传与病害辅助识别，并支持将结果写入预警闭环中心。
        </p>
      </div>
      <div class="hero-actions">
        <el-select v-model="detectMode" size="mini" class="hero-select">
          <el-option label="病害辅助识别" value="disease"></el-option>
          <el-option label="双检分析" value="both"></el-option>
        </el-select>
        <el-select v-model="cropType" size="mini" class="hero-select">
          <el-option label="番茄" value="tomato"></el-option>
          <el-option label="玉米" value="corn"></el-option>
          <el-option label="草莓" value="strawberry"></el-option>
          <el-option label="水稻" value="rice"></el-option>
        </el-select>
      </div>
    </section>

    <section class="vision-layout">
      <article class="panel preview-panel">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Input</p>
            <h2>巡检画面</h2>
          </div>
          <div class="mode-tabs">
            <button :class="{ active: mode === 'stream' }" @click="mode = 'stream'">实时流</button>
            <button :class="{ active: mode === 'upload' }" @click="mode = 'upload'">图片上传</button>
          </div>
        </div>

        <div class="preview-stage">
          <div v-if="mode === 'stream'" class="stream-box">
            <div class="stream-toolbar">
              <input v-model.trim="mjpegUrl" type="text" placeholder="输入 MJPEG 流地址" />
              <el-button size="mini" type="primary" @click="saveCameraUrl">保存地址</el-button>
            </div>
            <div class="stream-frame">
              <img v-if="streamActive" :src="mjpegUrl" alt="camera stream" @error="handleStreamError" />
              <div v-else class="empty-preview">
                <i class="el-icon-video-camera"></i>
                <span>实时流待启动</span>
              </div>
            </div>
            <div class="stream-actions">
              <el-button size="mini" type="primary" @click="startStream">启动实时流</el-button>
              <el-button size="mini" @click="captureFrame">截图分析</el-button>
            </div>
          </div>

          <div v-else class="upload-box">
            <label class="upload-drop">
              <input type="file" accept="image/*" @change="handleFileChange" />
              <div v-if="!imagePreview" class="empty-preview">
                <i class="el-icon-picture-outline"></i>
                <span>点击选择巡检图片</span>
              </div>
              <img v-else :src="imagePreview" alt="preview" />
            </label>
          </div>
        </div>

        <div class="analyze-bar">
          <el-select v-model="selectedFarmId" size="mini" placeholder="选择关联地块">
            <el-option
              v-for="farm in farms"
              :key="farm.id"
              :label="farm.farm"
              :value="farm.id"
            ></el-option>
          </el-select>
          <el-button type="primary" size="mini" :loading="loading" @click="analyzeCurrentFrame">
            开始识别
          </el-button>
        </div>
      </article>

      <article class="panel result-panel">
        <div class="panel-head">
          <div>
            <p class="panel-eyebrow">Result</p>
            <h2>识别结论</h2>
          </div>
          <span class="risk-badge" :class="analysisSummary.level">{{ analysisSummary.levelLabel }}</span>
        </div>

        <div class="result-summary">
          <div class="summary-card">
            <span>识别对象</span>
            <strong>{{ analysisSummary.mainLabel }}</strong>
          </div>
          <div class="summary-card">
            <span>置信度</span>
            <strong>{{ analysisSummary.confidence }}</strong>
          </div>
          <div class="summary-card">
            <span>异常数</span>
            <strong>{{ analysisSummary.detectionCount }}</strong>
          </div>
        </div>

        <div class="insight-card">
          <h3>辅助判断</h3>
          <p>{{ analysisSummary.message }}</p>
          <ul class="insight-list">
            <li v-for="item in analysisSummary.suggestions" :key="item">{{ item }}</li>
          </ul>
        </div>

        <div class="result-actions">
          <el-button type="primary" size="mini" :disabled="!canCreateAlert" @click="pushToAlertCenter">
            写入预警中心
          </el-button>
          <el-button size="mini" @click="$router.push('/iot-alert-center')">查看闭环</el-button>
        </div>

        <div class="detection-list">
          <h3>原始检测明细</h3>
          <template v-if="analysisDetections.length">
            <div class="detection-item" v-for="(item, index) in analysisDetections" :key="index">
              <strong>{{ item.class_name_ch || item.class_name || '未知对象' }}</strong>
              <span>{{ formatConfidence(item.confidence) }}</span>
            </div>
          </template>
          <el-empty v-else description="等待识别结果" :image-size="72"></el-empty>
        </div>
      </article>
    </section>
  </div>
</template>

<script>
export default {
  name: 'IoTVision',
  data() {
    return {
      mode: 'stream',
      detectMode: 'disease',
      cropType: 'tomato',
      mjpegUrl: localStorage.getItem('iot_camera_url') || 'http://192.168.137.192/mjpeg/1',
      streamActive: false,
      imagePreview: '',
      selectedFile: null,
      loading: false,
      analysisResult: null,
      farms: [],
      selectedFarmId: null
    }
  },
  computed: {
    selectedFarm() {
      return this.farms.find(item => item.id === this.selectedFarmId) || null;
    },
    analysisDetections() {
      if (!this.analysisResult) {
        return [];
      }
      if (this.analysisResult.disease_analysis && Array.isArray(this.analysisResult.disease_analysis.detections)) {
        return this.analysisResult.disease_analysis.detections;
      }
      if (Array.isArray(this.analysisResult.detections)) {
        return this.analysisResult.detections;
      }
      return [];
    },
    analysisSummary() {
      if (!this.analysisResult) {
        return {
          level: 'steady',
          levelLabel: '待识别',
          mainLabel: '等待输入',
          confidence: '--',
          detectionCount: 0,
          message: '上传图片或截取实时流后，可以将识别结果写入预警中心。',
          suggestions: ['建议优先针对病害和长势异常做辅助识别']
        };
      }

      const riskyDetections = this.analysisDetections.filter(item => {
        const name = String(item.class_name || '').toLowerCase();
        return !name.includes('healthy') && !name.includes('health');
      });
      const ordered = riskyDetections.slice().sort((a, b) => (b.confidence || 0) - (a.confidence || 0));
      const main = ordered[0];
      const confidence = main ? this.formatConfidence(main.confidence) : '98%';

      if (!main) {
        return {
          level: 'steady',
          levelLabel: '状态平稳',
          mainLabel: '未发现显著病害',
          confidence,
          detectionCount: this.analysisDetections.length,
          message: '当前识别结果未发现明显病害风险，可保持巡检频率并继续结合环境数据观察。',
          suggestions: [
            '保持当前巡检节奏',
            '如环境参数持续异常，可再执行一次复核识别'
          ]
        };
      }

      const label = main.class_name_ch || main.class_name || '异常对象';
      return {
        level: (main.confidence || 0) > 0.8 ? 'high' : 'medium',
        levelLabel: (main.confidence || 0) > 0.8 ? '需尽快复核' : '建议关注',
        mainLabel: label,
        confidence,
        detectionCount: riskyDetections.length,
        message: `当前识别到疑似 ${label}，建议结合温湿度与现场情况进行人工复核，再决定是否触发联动处置。`,
        suggestions: [
          '将识别结果写入预警中心',
          '切换至 GIS 页面确认所在地块',
          '必要时触发人工巡检或设备联动'
        ]
      };
    },
    canCreateAlert() {
      return !!this.analysisResult && !!this.selectedFarm;
    }
  },
  mounted() {
    this.fetchFarms();
  },
  beforeUnmount() {
    if (this.imagePreview) {
      URL.revokeObjectURL(this.imagePreview);
    }
  },
  methods: {
    formatConfidence(value) {
      if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return '--';
      }
      return `${Math.round(Number(value) * 100)}%`;
    },
    async fetchFarms() {
      const res = await this.request.get('/statistic/page', {
        params: {
          pageNum: 1,
          pageSize: 1000
        }
      });
      if (res.code === '200' && res.data) {
        this.farms = res.data.records || [];
        if (this.farms.length && !this.selectedFarmId) {
          this.selectedFarmId = this.farms[0].id;
        }
      }
    },
    saveCameraUrl() {
      if (!this.mjpegUrl) {
        this.$message.warning('请输入摄像头流地址');
        return;
      }
      localStorage.setItem('iot_camera_url', this.mjpegUrl);
      this.$message.success('摄像头地址已保存');
    },
    startStream() {
      if (!this.mjpegUrl) {
        this.$message.warning('请输入摄像头流地址');
        return;
      }
      this.streamActive = true;
    },
    handleStreamError() {
      this.streamActive = false;
      this.$message.error('实时流加载失败，请检查摄像头地址');
    },
    handleFileChange(event) {
      const file = event.target.files && event.target.files[0];
      if (!file) {
        return;
      }
      if (this.imagePreview) {
        URL.revokeObjectURL(this.imagePreview);
      }
      this.selectedFile = file;
      this.imagePreview = URL.createObjectURL(file);
      this.mode = 'upload';
    },
    captureFrame() {
      const streamImage = this.$el.querySelector('.stream-frame img');
      if (!streamImage) {
        this.$message.warning('请先启动摄像头实时流');
        return;
      }

      const canvas = document.createElement('canvas');
      canvas.width = streamImage.naturalWidth || 1280;
      canvas.height = streamImage.naturalHeight || 720;
      const context = canvas.getContext('2d');
      context.drawImage(streamImage, 0, 0, canvas.width, canvas.height);

      canvas.toBlob(blob => {
        if (!blob) {
          return;
        }
        if (this.imagePreview) {
          URL.revokeObjectURL(this.imagePreview);
        }
        this.selectedFile = new File([blob], `vision-capture-${Date.now()}.jpg`, { type: 'image/jpeg' });
        this.imagePreview = URL.createObjectURL(blob);
        this.mode = 'upload';
      }, 'image/jpeg', 0.95);
    },
    async analyzeCurrentFrame() {
      if (!this.selectedFile) {
        this.$message.warning('请先准备巡检图像');
        return;
      }

      this.loading = true;
      try {
        const formData = new FormData();
        formData.append('file', this.selectedFile);
        formData.append('crop_type', this.cropType);
        formData.append('conf', '0.5');

        const endpoint = this.detectMode === 'both' ? '/crop-analysis/both' : '/crop-analysis/disease';
        const res = await this.request.post(endpoint, formData);

        if (res.code === '200') {
          this.analysisResult = res.data || {};
          this.$message.success('巡检识别完成');
        } else {
          throw new Error(res.msg || '识别失败');
        }
      } catch (error) {
        this.$message.error(error.message || '识别失败，请确认后端模型服务已启动');
      } finally {
        this.loading = false;
      }
    },
    async pushToAlertCenter() {
      if (!this.canCreateAlert) {
        return;
      }

      const res = await this.request.post('/alert/manual', {
        farmlandId: this.selectedFarm.id,
        farmlandName: this.selectedFarm.farm,
        alertType: 'visual',
        alertLevel: this.analysisSummary.level === 'high' ? 'high' : 'medium',
        message: `视觉巡检识别到 ${this.analysisSummary.mainLabel}，建议尽快复核现场情况`,
        suggestion: this.analysisSummary.suggestions[0]
      });

      if (res.code === '200') {
        this.$message.success('识别结果已写入预警中心');
      } else {
        this.$message.error(res.msg || '写入预警中心失败');
      }
    }
  }
}
</script>

<style scoped>
.iot-vision {
  min-height: calc(100vh - 60px);
  padding: 20px;
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.12), transparent 22%),
    linear-gradient(180deg, #f5f7fb 0%, #eef3f8 100%);
}

.vision-hero,
.panel {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(203, 213, 225, 0.74);
  border-radius: 22px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
}

.vision-hero {
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
  color: #2563eb;
  font-weight: 700;
}

.vision-hero h1,
.panel-head h2 {
  margin: 0;
  color: #14213d;
}

.hero-summary {
  max-width: 720px;
  margin: 10px 0 0;
  color: #475569;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.hero-select {
  width: 140px;
}

.vision-layout {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 16px;
}

.panel {
  padding: 20px 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.mode-tabs {
  display: inline-flex;
  padding: 4px;
  border-radius: 999px;
  background: #f1f5f9;
}

.mode-tabs button {
  border: 0;
  background: transparent;
  padding: 8px 14px;
  border-radius: 999px;
  color: #475569;
  cursor: pointer;
}

.mode-tabs button.active {
  background: #ffffff;
  color: #14213d;
  font-weight: 700;
}

.preview-stage {
  min-height: 420px;
}

.stream-box,
.upload-box {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 420px;
}

.stream-toolbar {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}

.stream-toolbar input,
.analyze-bar :deep(.el-input__inner) {
  height: 38px;
}

.stream-toolbar input {
  width: 100%;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  outline: 0;
}

.stream-frame,
.upload-drop {
  flex: 1;
  min-height: 340px;
  border-radius: 22px;
  overflow: hidden;
  border: 1px dashed #cbd5e1;
  background: linear-gradient(180deg, #f8fafc 0%, #eef3f8 100%);
}

.stream-frame img,
.upload-drop img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-drop {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.upload-drop input {
  display: none;
}

.empty-preview {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #64748b;
  font-size: 14px;
}

.empty-preview i {
  font-size: 42px;
}

.stream-actions,
.analyze-bar,
.result-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.analyze-bar {
  margin-top: 14px;
}

.result-panel {
  display: flex;
  flex-direction: column;
}

.risk-badge {
  display: inline-flex;
  align-items: center;
  padding: 8px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: #e2e8f0;
  color: #334155;
}

.risk-badge.high {
  background: #fee2e2;
  color: #991b1b;
}

.risk-badge.medium {
  background: #ffedd5;
  color: #9a3412;
}

.risk-badge.steady {
  background: #dcfce7;
  color: #166534;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.summary-card,
.insight-card,
.detection-list {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.summary-card span {
  display: block;
  margin-bottom: 10px;
  color: #64748b;
  font-size: 12px;
}

.summary-card strong {
  color: #0f172a;
  font-size: 18px;
}

.insight-card {
  margin-bottom: 16px;
}

.insight-card h3,
.detection-list h3 {
  margin: 0 0 10px;
  color: #14213d;
}

.insight-card p {
  margin: 0 0 12px;
  color: #475569;
  line-height: 1.7;
}

.insight-list {
  margin: 0;
  padding-left: 18px;
  color: #0f172a;
}

.insight-list li + li {
  margin-top: 8px;
}

.result-actions {
  margin-bottom: 16px;
}

.detection-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #e2e8f0;
}

.detection-item:last-child {
  border-bottom: 0;
}

.detection-item strong {
  color: #0f172a;
}

.detection-item span {
  color: #64748b;
  font-size: 13px;
}

@media screen and (max-width: 1180px) {
  .vision-layout,
  .result-summary {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .iot-vision {
    padding: 14px;
  }

  .vision-hero,
  .hero-actions,
  .panel-head,
  .stream-toolbar,
  .stream-actions,
  .analyze-bar,
  .result-actions {
    flex-direction: column;
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .hero-select {
    width: 100%;
  }

  .mode-tabs {
    width: 100%;
    justify-content: stretch;
  }

  .mode-tabs button {
    flex: 1;
  }
}
</style>
