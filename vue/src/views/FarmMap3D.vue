<template>
  <div class="farm-map-nature">
    <div class="top-bar">
      <div class="title">
        <div class="title-text">
          <span class="main">智慧农场</span>
          <span class="sub">生态监测平台</span>
        </div>
      </div>
      <div class="right-area">
        <div class="weather-badge">
           <img src="@/assets/sun.png" class="weather-img" alt="天气" />
           <span>{{ weather.temp }}°C {{ weatherModeText }}</span>
        </div>
        <div class="time-badge"><img src="@/assets/shijian.png" class="time-img" alt="时间" /> {{ currentTime }}</div>
      </div>
    </div>

    <div class="main-container">
      <div class="left-panel">
        <div class="stats-row">
          <div class="nature-card stat-box">
            <img src="@/assets/place.png" class="stat-img" alt="地块" />
            <div>
              <div class="stat-num">{{ summary.farmCount }}</div>
              <div class="stat-label">地块数量</div>
            </div>
          </div>
          <div class="nature-card stat-box">
            <img src="@/assets/ceniang.png" class="stat-img" alt="面积" />
            <div>
              <div class="stat-num">{{ summary.totalArea }}<small>亩</small></div>
              <div class="stat-label">监测面积</div>
            </div>
          </div>
        </div>

        <div class="nature-card list-card">
          <div class="card-header">
            <img src="@/assets/jiance.png" class="header-img" alt="监测" />
            <span>监测点位</span>
          </div>
          <div class="farm-list">
            <div 
              v-for="(farm, index) in farms" 
              :key="index"
              class="farm-item"
              :class="{ active: selectedFarm && selectedFarm.id === farm.id }"
              @click="handleFarmClick(farm)"
            >
              <div class="item-left">
                <img :src="getCropIconByType(farm.crop)" class="crop-icon-img" alt="作物" />
              </div>
              <div class="item-center">
                <div class="f-name">{{ farm.farm }}</div>
                <div class="f-info">
                  <img src="@/assets/shuidi.png" class="info-icon" alt="湿度" /><span :class="Number(farm.soilhumidity)<20 ? 'text-danger':''" style="margin-right: 6px">{{ farm.soilhumidity }}%</span>
                  <img src="@/assets/wendu.png" class="info-icon" alt="温度" /><span :class="Number(farm.temperature)>30 ? 'text-danger':''">{{ farm.temperature }}°</span>
                </div>
              </div>
              <div class="item-right">
                <span class="status-tag" :class="getFarmStatusClass(farm)">
                  {{ getFarmStatusText(farm) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="center-panel">
        <div class="map-3d" ref="threeContainer"></div>
        <div class="scene-controls">
          <div class="capsule-btn" @click="resetCamera" title="重置视角">复位</div>
          <div class="divider"></div>
          <div class="icon-group">
            <el-tooltip content="晴朗" placement="top"><span @click="setWeather('sunny')" :class="{active: weatherMode==='sunny'}"><img src="@/assets/qing.png" class="weather-icon" alt="晴朗" /></span></el-tooltip>
            <el-tooltip content="降雨" placement="top"><span @click="setWeather('rain')" :class="{active: weatherMode==='rain'}"><img src="@/assets/xiayu.png" class="weather-icon" alt="降雨" /></span></el-tooltip>
            <el-tooltip content="降雪" placement="top"><span @click="setWeather('snow')" :class="{active: weatherMode==='snow'}"><img src="@/assets/snow.png" class="weather-icon" alt="降雪" /></span></el-tooltip>
            <el-tooltip content="雷暴" placement="top"><span @click="setWeather('storm')" :class="{active: weatherMode==='storm'}"><img src="@/assets/bingbao.png" class="weather-icon" alt="雷暴" /></span></el-tooltip>
          </div>
        </div>
      </div>

      <div class="right-panel" v-if="selectedFarm">
        <div class="nature-card detail-card">
          <div class="detail-top">
            <div class="farm-badge">
              <h3>{{ selectedFarm.farm }}</h3>
              <span class="type-pill">{{ selectedFarm.crop }}</span>
            </div>
            <div class="health-circle">
              <el-progress type="circle" :percentage="farmStatus.health" :width="90" :color="customColors" :stroke-width="8"></el-progress>
              <div class="circle-text">健康评分</div>
            </div>
          </div>

          <div class="env-grid">
            <div class="env-item">
              <span class="label">空气温度</span>
              <div class="bar-box">
                <span class="val">{{ selectedFarm.temperature }}°C</span>
                <el-progress :percentage="normalize(selectedFarm.temperature, 40)" :show-text="false" color="#FF9800"></el-progress>
              </div>
            </div>
            <div class="env-item">
              <span class="label">土壤湿度</span>
              <div class="bar-box">
                <span class="val">{{ selectedFarm.soilhumidity }}%</span>
                <el-progress :percentage="Number(selectedFarm.soilhumidity)" :show-text="false" color="#795548"></el-progress>
              </div>
            </div>
          </div>
          <div class="data-source" v-if="selectedFarm.dataSource">
            <span class="source-tag">数据来源: {{ selectedFarm.dataSource }}</span>
          </div>
        </div>
        
        <div class="nature-card ai-note-fixed">
          <div class="note-header">
             <img src="@/assets/danao.png" class="header-img" alt="智能" />
             <span>农艺师智能决策</span>
          </div>
          
          <div class="note-content">
            <div v-if="farmStatus.health < 90" class="warning-text"><img src="@/assets/jingao.png" class="warning-icon" alt="警告" /> {{ farmStatus.msg }}</div>
            <div v-else class="normal-text">当前各项指标优异，建议维持现状。</div>
          </div>
        </div>

        <div class="nature-card control-card-white">
          <div class="switch-grid">
            <div class="switch-item" :class="{ active: switchState.water }">
              <div class="switch-left">
                <div class="switch-icon water-icon">
                  <img src="@/assets/guangai.png" class="switch-img" alt="灌溉" />
                </div>
                <div class="switch-info">
                  <span class="switch-name">智能灌溉</span>
                  <span class="switch-status">{{ switchState.water ? '运行中' : '已关闭' }}</span>
                </div>
              </div>
              <el-switch 
                v-model="switchState.water" 
                active-color="#10b981"
                @change="handleSwitchChange('water', $event)"
              ></el-switch>
            </div>

            <div class="switch-item" :class="{ active: switchState.fertilizer }">
              <div class="switch-left">
                <div class="switch-icon fertilizer-icon">
                  <img src="@/assets/shifei.png" class="switch-img" alt="施肥" />
                </div>
                <div class="switch-info">
                  <span class="switch-name">精准施肥</span>
                  <span class="switch-status">{{ switchState.fertilizer ? '运行中' : '已关闭' }}</span>
                </div>
              </div>
              <el-switch 
                v-model="switchState.fertilizer" 
                active-color="#f59e0b"
                @change="handleSwitchChange('fertilizer', $event)"
              ></el-switch>
            </div>

            <div class="switch-item" :class="{ active: switchState.pest }">
              <div class="switch-left">
                <div class="switch-icon pest-icon">
                  <img src="@/assets/xiaosha.png" class="switch-img" alt="消杀" />
                </div>
                <div class="switch-info">
                  <span class="switch-name">飞防消杀</span>
                  <span class="switch-status">{{ switchState.pest ? '运行中' : '已关闭' }}</span>
                </div>
              </div>
              <el-switch 
                v-model="switchState.pest" 
                active-color="#3b82f6"
                @change="handleSwitchChange('pest', $event)"
              ></el-switch>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script>
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';
// ✨ 引入 RGBELoader 用于加载 HDR
import { RGBELoader } from 'three/examples/jsm/loaders/RGBELoader';
import gsap from 'gsap';
import { eventBus, EVENTS } from '@/utils/eventBus';
import { assetUrl } from '@/utils/assetUrl';

export default {
  name: 'FarmMapNature',
  data() {
    return {
      currentTime: '',
      selectedFarm: null,
      weather: { temp: 26 },
      summary: { farmCount: 0, totalArea: 0, totalStock: 0, normalCount: 0 },
      farms: [],
      weatherMode: 'sunny',
      loadingState: { water: false, fertilizer: false, pest: false },
      switchState: { water: false, fertilizer: false, pest: false },
      textureMap: {},
      lightningBolts: [],
      lightningMesh: null,
      activeDrone: null,
      droneTemplate: null,
      droneLoadPromise: null,
      droneFlightFrame: null,
      droneFogFrame: null,
      activeDroneFog: null,
      droneTaskToken: 0,
      activeWaterValve: null,
      clockTimer: null,
      stm32Data: {
        device1: { temperature: null, humidity: null },
        device2: { temperature: null, humidity: null, bump: false }
      },
      farmDeviceMap: {},
      cropConfig: {
        wheat: { model: '/models/wheat.glb', scale: 3.0, color: 0xe6c658, fallback: 'spike', yOffset: 8.0 },
        mugwort: { model: '/models/rhy.glb', scale: 6, color: 0x8BC34A, fallback: 'spike' },
        bamboo: { model: '/models/bamboo.glb', scale: 0.1, color: 0x4CAF50, fallback: 'spike' },
        maize: { model: '/models/maize.glb', scale: 6, color: 0xFFC107, fallback: 'column' },
        palm: { model: '/models/tree.glb', scale: 1.0, color: 0x388E3C, fallback: 'bush' },
        tomato: { model: '/models/tomato.glb', scale: 0.05, color: 0xFF5722, fallback: 'fruit', fruitColor: 0xD84315 },
        default: { scale: 1.0, color: 0x8BC34A, fallback: 'bush' }
      },
      customColors: [
        { color: '#F44336', percentage: 60 },
        { color: '#FF9800', percentage: 80 },
        { color: '#4CAF50', percentage: 100 }
      ]
    };
  },
  
  computed: {
    currentWeatherIcon() {
      const map = { sunny: '\u2600', rain: '\u2614', snow: '\u2744', storm: '\u26c8' };
      return map[this.weatherMode] || '\u2600';
    },
    weatherModeText() {
      const map = { sunny: 'Sunny', rain: 'Rain', snow: 'Snow', storm: 'Storm' };
      return map[this.weatherMode];
    },
    farmStatus() {
      if (!this.selectedFarm) {
        return { health: 100, msg: '', needsWater: false, needsFertilizer: false, hasBugs: false, tempHigh: false };
      }

      const temperature = Number(this.selectedFarm.temperature || 0);
      const humidity = Number(this.selectedFarm.soilhumidity || 0);
      const stateText = String(this.selectedFarm.state || this.selectedFarm.status || '');
      const tempHigh = temperature > 30;
      const needsWater = humidity > 0 && humidity < 20;
      const needsFertilizer = humidity >= 20 && humidity < 35 && !this.selectedFarm.fertilizerFixed;
      const hasBugs = /(bug|disease|worm|pest)/i.test(stateText) && !this.selectedFarm.bugFixed;

      let msg = '';
      let health = 100;

      if (tempHigh) {
        msg = 'Temperature is high, ventilation is recommended';
        health -= 30;
      } else if (needsWater) {
        msg = 'Soil humidity is low, irrigation is recommended';
        health -= 25;
      } else if (needsFertilizer) {
        msg = 'Soil fertility is dropping, fertilization is recommended';
        health -= 15;
      } else if (hasBugs) {
        msg = 'Pest risk detected, treatment is recommended';
        health -= 20;
      }

      return { needsWater, needsFertilizer, hasBugs, tempHigh, msg, health };
    }
  },

  mounted() {
    this.scene = null;
    this.camera = null;
    this.renderer = null;
    this.controls = null;
    this.raycaster = new THREE.Raycaster();
    this.mouse = new THREE.Vector2();
    this.gltfLoader = new GLTFLoader();
    this.mouseDownPos = { x: 0, y: 0 };
    this.mouseDownTime = 0;
    this.farmBlocks = [];
    this.labels = [];
    this.requestId = null;

    this.loadTextures();
    this.updateTime();
    this.clockTimer = setInterval(() => this.updateTime(), 1000);
    this.fetchData();
    this.preloadDroneModel();

    this.stm32Timer = setInterval(() => {
      this.fetchSTM32Data();
      this.updateFarmsWithSTM32Data();
    }, 30000);

    window.addEventListener('resize', this._boundOnWindowResize = () => this.onWindowResize());

    eventBus.on(EVENTS.IRRIGATION_ON, () => {
      this.switchState.water = true;
      this.$message.success('AI irrigation enabled');
    });
    eventBus.on(EVENTS.IRRIGATION_OFF, () => {
      this.switchState.water = false;
      this.$message.info('AI irrigation disabled');
    });
    eventBus.on('DRONE_SPRAY_ON', () => {
      this.switchState.pest = true;
      if (this.selectedFarm) {
        this.handleAction('pest');
      }
    });
  },
  
  beforeUnmount() {
    cancelAnimationFrame(this.requestId);
    this.stopDroneOperation(true);
    if (this._boundOnWindowResize) {
      window.removeEventListener('resize', this._boundOnWindowResize);
      this._boundOnWindowResize = null;
    }
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
    }
    eventBus.off(EVENTS.IRRIGATION_ON);
    eventBus.off(EVENTS.IRRIGATION_OFF);
    eventBus.off('DRONE_SPRAY_ON');
    if (this.stm32Timer) {
      clearInterval(this.stm32Timer);
    }
    if (this.renderer) {
      this.renderer.dispose();
      this.renderer.forceContextLoss();
    }
    if (this.matGrass) this.matGrass.dispose();
    if (this.matSoil) this.matSoil.dispose();
  },
  
  methods: {
    updateTime() { this.currentTime = new Date().toLocaleString('zh-CN', { hour12: false }); },
    normalize(val, max) { return Math.min((Number(val) / max) * 100, 100); },
    preloadDroneModel() {
      this.getDroneTemplate().catch(error => {
        console.warn('Drone preload failed:', error);
      });
    },
    getDroneTemplate() {
      if (this.droneTemplate) {
        return Promise.resolve(this.droneTemplate);
      }
      if (this.droneLoadPromise) {
        return this.droneLoadPromise;
      }

      this.droneLoadPromise = new Promise((resolve, reject) => {
        this.gltfLoader.load(
          '/models/drone.glb',
          (gltf) => {
            const drone = gltf.scene;
            drone.visible = false;
            drone.traverse((node) => {
              if (node.isMesh) {
                node.castShadow = false;
                node.receiveShadow = false;
                node.frustumCulled = true;
                if (node.material) {
                  if (Array.isArray(node.material)) {
                    node.material.forEach((material) => {
                      material.flatShading = true;
                      material.needsUpdate = true;
                    });
                  } else {
                    node.material.flatShading = true;
                    node.material.needsUpdate = true;
                  }
                }
              }
            });
            this.droneTemplate = drone;
            resolve(drone);
          },
          undefined,
          (error) => {
            this.droneLoadPromise = null;
            reject(error);
          }
        );
      });

      return this.droneLoadPromise;
    },
    cleanupPoints(points) {
      if (!points) return;
      if (points.parent) {
        points.parent.remove(points);
      }
      if (points.geometry) {
        points.geometry.dispose();
      }
      if (points.material) {
        if (Array.isArray(points.material)) {
          points.material.forEach(material => material.dispose && material.dispose());
        } else if (points.material.dispose) {
          points.material.dispose();
        }
      }
    },
    stopDroneOperation(silent = false) {
      this.droneTaskToken += 1;

      if (this.droneFlightFrame) {
        cancelAnimationFrame(this.droneFlightFrame);
        this.droneFlightFrame = null;
      }
      if (this.droneFogFrame) {
        cancelAnimationFrame(this.droneFogFrame);
        this.droneFogFrame = null;
      }

      if (this.activeDrone && this.activeDrone.parent) {
        this.activeDrone.parent.remove(this.activeDrone);
      }
      if (this.activeDroneFog) {
        this.cleanupPoints(this.activeDroneFog);
      }

      this.activeDrone = null;
      this.activeDroneFog = null;

      if (!silent) {
        this.switchState.pest = false;
      }
    },
    resolveCropType(crop) {
        const raw = String(crop || '').trim();
        const lower = raw.toLowerCase();
        if (!raw) return null;
        if (this.cropConfig[lower]) return lower;
        if (raw.includes('小麦') || lower.includes('wheat')) return 'wheat';
        if (raw.includes('艾') || lower.includes('mugwort') || lower.includes('rhy')) return 'mugwort';
        if (raw.includes('竹') || lower.includes('bamboo')) return 'bamboo';
        if (raw.includes('玉米') || lower.includes('maize') || lower.includes('corn')) return 'maize';
        if (raw.includes('棕') || raw.includes('榈') || lower.includes('palm') || lower.includes('tree')) return 'palm';
        if (raw.includes('西红柿') || raw.includes('番茄') || lower.includes('tomato')) return 'tomato';
        return 'default';
    },
    getCropIconByType(crop) {
        const cropType = this.resolveCropType(crop);
        const icons = {
            wheat: assetUrl('xiaomai.png'),
            mugwort: assetUrl('aiye.png'),
            bamboo: assetUrl('zhuzi.png'),
            maize: assetUrl('yumi.png'),
            palm: assetUrl('zongshu.png'),
            tomato: assetUrl('xihongshi.png'),
            default: assetUrl('moreng.png')
        };
        return icons[cropType] || icons.default;
    },
    getCropIcon(crop) {
        const icons = {
            '小麦': assetUrl('xiaomai.png'),
            '艾叶': assetUrl('aiye.png'),
            '竹子': assetUrl('zhuzi.png'),
            '玉米': assetUrl('yumi.png'),
            '棕树': assetUrl('zongshu.png'),
            '西红柿': assetUrl('xihongshi.png')
        };
        return icons[crop] || assetUrl('moreng.png');
    },
    
    // 获取农田状态样式类
    getFarmStatusClass(farm) {
      if (!farm) return 'bg-green';
      const temp = Number(farm.temperature);
      const humidity = Number(farm.soilhumidity);
      
      // 温度过高或湿度过低显示红色警告
      if (temp > 30 || humidity < 20) {
        return 'bg-red';
      }
      return 'bg-green';
    },
    
    // 获取农田状态文本
    getFarmStatusText(farm) {
      if (!farm) return '正常';
      const temp = Number(farm.temperature);
      const humidity = Number(farm.soilhumidity);
      
      // 优先显示温度警告
      if (temp > 30) {
        return '温度过高';
      }
      // 其次显示湿度警告
      if (humidity < 20) {
        return '缺水';
      }
      return '正常';
    },

    async fetchData() {
      try {
        // 先获取STM32传感器数据（两个设备）
        
        const res = await this.request.get('/statistic/dashboard');
        if (res.code === '200') {
            this.farms = Array.isArray(res.data) ? res.data : [];
            this.applyFarmFallbackData();
            this.initializeFarmScene();

            this.fetchSTM32Data()
              .then(() => {
                this.applyFarmFallbackData();
              })
              .catch(error => {
                console.warn('STM32 data refresh failed:', error);
              });
            return;
        }
      } catch (e) { console.error(e); }
    },

    applyFarmFallbackData() {
      const selectedFarmId = this.selectedFarm ? this.selectedFarm.id : null;
      this.farms = (this.farms || []).map(farm => {
        const updatedFarm = { ...farm };
        const deviceKey = this.assignDeviceToFarm(farm.id);
        const deviceData = this.getDeviceData(deviceKey);

        if (!updatedFarm.temperature || updatedFarm.temperature <= 0) {
          updatedFarm.temperature = deviceData.temperature;
          updatedFarm.dataSource = deviceKey === 'device1' ? 'STM32-001' : 'STM32-PUMP';
        } else {
          updatedFarm.dataSource = 'Database';
        }

        if (!updatedFarm.soilhumidity || updatedFarm.soilhumidity <= 0) {
          updatedFarm.soilhumidity = deviceData.humidity;
        }

        return updatedFarm;
      });

      this.summary.farmCount = this.farms.length;
      this.summary.totalArea = this.farms.reduce((acc, cur) => acc + (Number(cur.area) || 0), 0);

      if (!this.farms.length) {
        this.selectedFarm = null;
        return;
      }

      this.selectedFarm = this.farms.find(farm => farm.id === selectedFarmId) || this.farms[0];
    },

    initializeFarmScene() {
      this.$nextTick(() => {
        if (!this.scene && this.$refs.threeContainer) {
          this.initThreeJS();
        }
      });
    },
    
    // 获取STM32传感器实时数据（两个设备）
    async fetchSTM32Data() {
      // 获取旧设备数据 (CzL61ga8FI)
      try {
        const res1 = await this.request.get('/aether/device/status');
        if (res1.code === '200' && res1.data) {
          this.stm32Data.device1.temperature = res1.data.temperature || 25;
          this.stm32Data.device1.humidity = res1.data.humidity || 50;
        }
      } catch (e) {
        console.warn('设备1数据获取失败', e);
        this.stm32Data.device1.temperature = 25;
        this.stm32Data.device1.humidity = 50;
      }
      
      // 获取新设备数据 (KK57iNOm8d)
      try {
        const res2 = await this.request.get('/aether/device/new/status');
        if (res2.code === '200' && res2.data) {
          this.stm32Data.device2.temperature = res2.data.temperature || 26;
          this.stm32Data.device2.humidity = res2.data.humidity || 55;
          this.stm32Data.device2.bump = res2.data.bump || false;
        }
      } catch (e) {
        console.warn('设备2数据获取失败', e);
        this.stm32Data.device2.temperature = 26;
        this.stm32Data.device2.humidity = 55;
      }
    },
    
    // 为农田分配设备（随机分配，但保持稳定）
    assignDeviceToFarm(farmId) {
      if (!this.farmDeviceMap[farmId]) {
        // 根据farmId的奇偶性分配设备，保证分配稳定
        this.farmDeviceMap[farmId] = farmId % 2 === 0 ? 'device1' : 'device2';
      }
      return this.farmDeviceMap[farmId];
    },
    
    // 获取指定设备的数据
    getDeviceData(deviceKey) {
      const device = this.stm32Data[deviceKey];
      if (!device) return { temperature: 25, humidity: 50 };
      return {
        temperature: device.temperature || 25,
        humidity: device.humidity || 50
      };
    },
    
    // 使用真实传感器数据更新所有农田（定时刷新时不覆盖数据库数据）
    updateFarmsWithSTM32Data() {
      if (!this.farms || this.farms.length === 0) return;
      
      // 注释掉自动更新功能，保持数据库数据不变
      // 只在初次加载时使用传感器数据作为备用
      return;
      
      /* 原有的传感器数据覆盖逻辑已禁用
      this.farms = this.farms.map(farm => {
        const updatedFarm = { ...farm };
        
        // 为该农田分配一个设备
        const deviceKey = this.assignDeviceToFarm(farm.id);
        const deviceData = this.getDeviceData(deviceKey);
        
        // 直接使用传感器实时数据，不添加波动
        updatedFarm.temperature = deviceData.temperature;
        updatedFarm.soilhumidity = deviceData.humidity;
        
        // 标记数据来源
        updatedFarm.dataSource = deviceKey === 'device1' ? 'STM32-001' : 'STM32-PUMP';
        
        return updatedFarm;
      });
      
      // 同步更新选中的农田
      if (this.selectedFarm) {
        const updatedSelected = this.farms.find(f => f.id === this.selectedFarm.id);
        if (updatedSelected) {
          this.selectedFarm = updatedSelected;
        }
      }
      
      // 更新3D标签显示
      this.updateAllLabels();
      
      */
    },
    
    // 更新所有3D标签
    updateAllLabels() {
      if (!this.labels || this.labels.length === 0) return;
      this.labels.forEach((label, index) => {
        if (this.farms[index]) {
          this.updateLabelTexture(label, this.farms[index]);
        }
      });
    },

    loadTextures() {
        const loader = new THREE.TextureLoader();
        
        // 1. 草地材质 (PBR)
        const grassCol = loader.load('/textures/grass_color.jpg');
        grassCol.colorSpace = THREE.SRGBColorSpace; 
        const grassNor = loader.load('/textures/grass_normal.jpg');
        const grassRgh = loader.load('/textures/grass_rough.jpg');
        
        [grassCol, grassNor, grassRgh].forEach(t => {
            t.wrapS = THREE.RepeatWrapping; 
            t.wrapT = THREE.RepeatWrapping; 
            t.repeat.set(15, 15); 
        });
        
        this.matGrass = new THREE.MeshStandardMaterial({
            map: grassCol, 
            normalMap: grassNor, 
            roughnessMap: grassRgh,
            normalScale: new THREE.Vector2(3, 3), 
            roughness: 0.8,
            color: 0xdddddd 
        });

        // 2. 泥土材质 (PBR) - 修复像素跳动
        const soilCol = loader.load('/textures/soil_color.jpg');
        soilCol.colorSpace = THREE.SRGBColorSpace;
        const soilNor = loader.load('/textures/soil_normal.jpg');
        const soilRgh = loader.load('/textures/soil_rough.jpg');
        
        // 关键修复：高质量纹理过滤，防止像素跳动
        [soilCol, soilNor, soilRgh].forEach(t => {
            t.wrapS = THREE.RepeatWrapping; 
            t.wrapT = THREE.RepeatWrapping; 
            t.repeat.set(1, 1); // 降低重复次数从2到1，减少纹理采样频率变化
            // 启用最高级别各向异性过滤
            t.anisotropy = 16;
            // 使用三线性过滤，最平滑的过渡
            t.minFilter = THREE.LinearMipmapLinearFilter;
            t.magFilter = THREE.LinearFilter;
            // 启用 mipmap 但使用最平滑的过滤
            t.generateMipmaps = true;
        });
        
        this.matSoil = new THREE.MeshStandardMaterial({
            map: soilCol, 
            normalMap: soilNor, 
            roughnessMap: soilRgh,
            normalScale: new THREE.Vector2(2, 2), // 降低法线强度，减少视角变化时的抖动
            roughness: 1.0
        });

        // 3. 特效粒子 (JPG)
        this.textureMap = {};
        this.textureMap.rain = loader.load('/textures/rain.jpg');
        this.textureMap.snow = loader.load('/textures/snow.jpg');
        this.textureMap.smoke = loader.load('/textures/smoke.jpg');
        this.textureMap.lightning = loader.load('/textures/lightning.jpg');

        // 4. 加载 HDR 天空盒（延迟加载，避免scene为null）
        setTimeout(() => {
          if (this.scene) {
            new RGBELoader().setPath('/textures/').load('sky.hdr', (texture) => {
                texture.mapping = THREE.EquirectangularReflectionMapping;
                if (this.scene) {  // 再次检查
                  this.scene.background = texture;
                  this.scene.environment = texture;
                }
            }, undefined, (err) => {
                console.warn('HDR天空盒加载失败，使用默认背景', err);
            });
          }
        }, 500);
    },

    // 🎮 开关切换处理
    handleSwitchChange(type, value) {
        if (!this.selectedFarm) {
          this.$message.warning('请先选择一个地块');
          this.switchState[type] = false;
          return;
        }
        
        const actionNames = { water: '智能灌溉', fertilizer: '精准施肥', pest: '飞防消杀' };
        
        if (value) {
          // 开启
          this.handleAction(type);
        } else {
          // 关闭
          if (type === 'water') {
            this.controlWaterPump(false);
            // 移除水阀模型
            if (this.activeWaterValve) {
              this.scene.remove(this.activeWaterValve);
              this.activeWaterValve = null;
            }
          }
          if (type === 'pest') {
            this.stopDroneOperation(true);
          }
          this.$message.info(`[${this.selectedFarm.farm}] ${actionNames[type]}已关闭`);
        }
    },
    
    // 🎮 农事操作逻辑
    handleAction(type) {
        if (!this.selectedFarm) return;
        if (this.loadingState[type]) return;
        
        this.loadingState[type] = true;
        const targetMesh = this.farmBlocks.find(m => m.userData.farm.id === this.selectedFarm.id);
        if (!targetMesh) {
             this.loadingState[type] = false;
             return;
        }
        const { x, z } = targetMesh.position;

        if (type === 'water') {
            this.$message.success(`[${this.selectedFarm.farm}] 智能喷灌系统已启动`);
            this.spawnSprinklerEffect(x, z);
            this.modifyFarmData('soilhumidity', 40);
            // 调用OneNET控制水泵开启
            this.controlWaterPump(true);
        } else if (type === 'fertilizer') {
            this.$message.success(`[${this.selectedFarm.farm}] 无人机正在施肥...`);
            this.spawnFertilizerDust(x, z);
            this.selectedFarm.fertilizerFixed = true;
            this.selectedFarm.state = '正常';
            this.syncFarmDataToBackend(this.selectedFarm);
        } else if (type === 'pest') {
            this.$message.success(`[${this.selectedFarm.farm}] 无人机正在进行消杀作业`);
            this.spawnDroneFog(x, z);
            this.selectedFarm.bugFixed = true;
            this.selectedFarm.state = '正常';
            this.syncFarmDataToBackend(this.selectedFarm);
        }

        setTimeout(() => {
            this.loadingState[type] = false;
            if (type === 'pest' && this.switchState.pest) {
              this.stopDroneOperation();
            }
            this.$message.success('农事作业完成');
        }, 4000);
    },

    modifyFarmData(key, amount) {
        setTimeout(() => {
            let val = Number(this.selectedFarm[key]) + amount;
            if (key === 'soilhumidity' && val > 100) val = 90;
            this.selectedFarm[key] = val;
            
            // 同步保存到后端数据库
            this.syncFarmDataToBackend(this.selectedFarm);
        }, 2000);
    },
    
    // 同步农田数据到后端
    async syncFarmDataToBackend(farm) {
        try {
            await this.request.post('/statistic', {
                id: farm.id,
                farm: farm.farm,
                crop: farm.crop,
                area: farm.area,
                keeper: farm.keeper,
                temperature: farm.temperature,
                soilhumidity: farm.soilhumidity,
                airhumidity: farm.airhumidity,
                light: farm.light,
                state: farm.state
            });
        } catch (e) {
            console.error('同步数据失败:', e);
        }
    },
    
    // 控制水泵开关（OneNET设备）
    async controlWaterPump(state) {
        try {
            const res = await this.request.post('/aether/device/control/bump', {
                bump: state
            });
            if (res.code === '200') {
                // 4秒后自动关闭水泵
                if (state) {
                    setTimeout(() => {
                        this.controlWaterPump(false);
                        this.switchState.water = false;
                    }, 4000);
                }
            } else {
                console.warn('水泵控制返回非200:', res);
            }
        } catch (e) {
            // 静默处理错误，只在控制台输出
            console.warn('水泵控制请求:', state ? '开启' : '关闭', e.message || e);
        }
    },

    // 💦 喷灌特效 (极速版 - 高密度)
    spawnSprinklerEffect(x, z) {
        // 粒子数量：300个（密度提升，性能仍优秀）
        const particleCount = 300;
        const geo = new THREE.BufferGeometry();
        const positions = new Float32Array(particleCount * 3);
        const velocities = new Float32Array(particleCount * 3);

        // 预计算所有粒子初始状态 - 增加密度
        for (let i = 0; i < particleCount; i++) {
            const i3 = i * 3;
            // 从中心点稍微分散起始位置，增加密度感
            const offsetX = (Math.random() - 0.5) * 3;
            const offsetZ = (Math.random() - 0.5) * 3;
            positions[i3] = x + offsetX;
            positions[i3 + 1] = 8 + Math.random() * 2; // 高度稍有差异
            positions[i3 + 2] = z + offsetZ;
            
            const angle = Math.random() * Math.PI * 2;
            const speed = 0.8 + Math.random() * 2.0;
            velocities[i3] = Math.cos(angle) * speed;
            velocities[i3 + 1] = 5.0 + Math.random() * 3.0;
            velocities[i3 + 2] = Math.sin(angle) * speed;
        }
        geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));

        const mat = new THREE.PointsMaterial({
            color: 0x00FFFF,
            size: 3.5, // 稍小粒子，但数量多，密度高
            map: this.textureMap.rain,
            transparent: true,
            opacity: 0.9, // 提高不透明度
            depthWrite: false,
            blending: THREE.AdditiveBlending,
            sizeAttenuation: true
        });

        const particles = new THREE.Points(geo, mat);
        this.scene.add(particles);

        // 60fps全速运行（最流畅）
        const animate = () => {
            if (!particles.parent) return;
            
            const pos = positions;
            let activeCount = 0;
            
            // 极速更新
            for (let i = 0; i < particleCount; i++) {
                const i3 = i * 3;
                const y = pos[i3 + 1];
                
                if (y < -2) continue;
                
                pos[i3] += velocities[i3];
                pos[i3 + 1] += velocities[i3 + 1];
                pos[i3 + 2] += velocities[i3 + 2];
                
                velocities[i3 + 1] -= 0.25;
                
                activeCount++;
            }
            
            particles.geometry.attributes.position.needsUpdate = true;
            
            if (activeCount > 0) {
                requestAnimationFrame(animate);
            } else {
                this.scene.remove(particles);
                geo.dispose();
                mat.dispose();
            }
        };
        animate();
    },

    // ☁️ 杀虫特效（性能修复版）
    async spawnDroneFog(x, z) {
        if (!this.scene) return;

        this.stopDroneOperation(true);
        const taskToken = ++this.droneTaskToken;

        const particleCount = 48;
        const geo = new THREE.BufferGeometry();
        const positions = [];
        for (let i = 0; i < particleCount; i++) {
            positions.push(x + (Math.random() - 0.5) * 24, 14 + Math.random() * 5, z + (Math.random() - 0.5) * 24);
        }
        geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));

        const mat = new THREE.PointsMaterial({
            color: 0xffffff,
            size: 10,
            map: this.textureMap.smoke,
            transparent: true,
            opacity: 0,
            depthWrite: false,
            sizeAttenuation: true,
            blending: THREE.AdditiveBlending
        });

        const particles = new THREE.Points(geo, mat);
        this.activeDroneFog = particles;
        this.scene.add(particles);

        let life = 0;
        const animateFog = () => {
            if (taskToken !== this.droneTaskToken || !particles.parent) return;

            life += 0.02;
            if (life < 0.25) mat.opacity = life * 2.8;
            else if (life > 0.7) mat.opacity = Math.max(0, (1 - life) * 2.2);
            else mat.opacity = 0.6;

            const pos = particles.geometry.attributes.position.array;
            for (let i = 0; i < particleCount; i++) {
                pos[i * 3 + 1] -= 0.02;
            }
            particles.geometry.attributes.position.needsUpdate = true;

            if (life < 1) {
                this.droneFogFrame = requestAnimationFrame(animateFog);
            } else {
                this.cleanupPoints(particles);
                if (this.activeDroneFog === particles) {
                  this.activeDroneFog = null;
                }
                this.droneFogFrame = null;
            }
        };
        this.droneFogFrame = requestAnimationFrame(animateFog);

        try {
            const template = await this.getDroneTemplate();
            if (taskToken !== this.droneTaskToken || !this.scene) return;

            const drone = template.clone(true);
            const size = 17.5;
            const startX = x - size;
            const startZ = z - size;
            drone.visible = true;
            drone.position.set(startX, 25, startZ);
            drone.scale.set(12, 12, 12);
            this.activeDrone = drone;
            this.scene.add(drone);

            const path = [
                { x: startX, z: startZ },
                { x: x + size, z: startZ },
                { x: x + size, z: z + size },
                { x: startX, z: z + size },
                { x: startX, z: startZ }
            ];

            let pathIndex = 0;
            let progress = 0;
            const flySpeed = 0.02;
            let lastUpdate = performance.now();

            const flyDrone = (now) => {
                if (taskToken !== this.droneTaskToken || !drone.parent) {
                    return;
                }

                if (now - lastUpdate < 33) {
                    this.droneFlightFrame = requestAnimationFrame(flyDrone);
                    return;
                }
                lastUpdate = now;

                if (pathIndex >= path.length - 1) {
                    this.stopDroneOperation(true);
                    return;
                }

                progress += flySpeed;
                const current = path[pathIndex];
                const next = path[pathIndex + 1];

                drone.position.x = current.x + (next.x - current.x) * progress;
                drone.position.z = current.z + (next.z - current.z) * progress;

                const angle = Math.atan2(next.z - current.z, next.x - current.x);
                drone.rotation.y = angle - Math.PI / 2;

                if (progress >= 1) {
                    progress = 0;
                    pathIndex += 1;
                }

                this.droneFlightFrame = requestAnimationFrame(flyDrone);
            };

            this.droneFlightFrame = requestAnimationFrame(flyDrone);
        } catch (error) {
            console.error("无人机模型加载失败:", error);
            this.stopDroneOperation(true);
            this.switchState.pest = false;
        }
    },

    // ✨ 施肥特效 (增强版)
    spawnFertilizerDust(x, z) {
        const particleCount = 500;
        const geo = new THREE.BufferGeometry();
        const positions = [];
        for (let i = 0; i < particleCount; i++) {
            positions.push(x + (Math.random()-0.5)*10, 2 + Math.random()*10, z + (Math.random()-0.5)*10);
        }
        geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));

        const mat = new THREE.PointsMaterial({
            color: 0xFFD700, size: 1.5, map: this.textureMap.snow, 
            transparent: true, opacity: 1.0, blending: THREE.AdditiveBlending
        });

        const particles = new THREE.Points(geo, mat);
        this.scene.add(particles);

        let frame = 0;
        const animate = () => {
            if (!particles.parent) return;
            frame++;
            const pos = particles.geometry.attributes.position.array;
            for(let i=0; i<particleCount; i++) {
                pos[i*3+1] += Math.sin(frame * 0.05 + i) * 0.05;
                pos[i*3] += Math.cos(frame * 0.02 + i) * 0.02;
            }
            particles.geometry.attributes.position.needsUpdate = true;
            mat.opacity -= 0.008;
            if (mat.opacity > 0) requestAnimationFrame(animate);
            else { this.scene.remove(particles); geo.dispose(); mat.dispose(); }
        };
        animate();
    },

    initThreeJS() {
      const container = this.$refs.threeContainer;
      if (!container) {
        console.error('threeContainer DOM元素未找到，无法初始化3D场景');
        return;
      }
      const width = container.clientWidth;
      const height = container.clientHeight;

      this.scene = new THREE.Scene();
      this.scene.background = new THREE.Color(0x87CEEB); 
      this.scene.fog = new THREE.Fog(0x87CEEB, 100, 600);

      this.camera = new THREE.PerspectiveCamera(60, width / height, 1, 1000);
      this.camera.position.set(50, 60, 80);

      this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      this.renderer.setSize(width, height);
      this.renderer.setPixelRatio(window.devicePixelRatio);
      this.renderer.shadowMap.enabled = true;
      this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
      this.renderer.outputColorSpace = THREE.SRGBColorSpace;
      
      // ✨【曝光控制】防止 HDR 太亮
      this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
      this.renderer.toneMappingExposure = 0.6; 
      
      container.appendChild(this.renderer.domElement);

      this.controls = new OrbitControls(this.camera, this.renderer.domElement);
      this.controls.enableDamping = true;
      this.controls.maxPolarAngle = Math.PI / 2.1;

      // ✨【灯光控制】
      const ambientLight = new THREE.AmbientLight(0xffffff, 0.1); 
      this.scene.add(ambientLight);
      
      const dirLight = new THREE.DirectionalLight(0xFFFEE0, 1.2); 
      dirLight.position.set(80, 100, -50); 
      dirLight.castShadow = true;
      dirLight.shadow.mapSize.width = 2048;
      dirLight.shadow.mapSize.height = 2048;
      dirLight.shadow.bias = -0.0005;
      this.scene.add(dirLight);
      
      // 闪电光
      this.lightningLight = new THREE.PointLight(0xaaddff, 0, 1000);
      this.scene.add(this.lightningLight);

      this.createGround();
      this.createFarmBlocks();
      this.initRainSystem();
      this.initSnowSystem();

      // 使用 pointerdown/pointerup 区分拖拽和点击
      container.addEventListener('pointerdown', this.onPointerDown, false);
      container.addEventListener('pointerup', this.onPointerUp, false);
      this.animate();
      this.resetCamera();
      
      // 强制初始化为晴朗天气 (不弹窗)
      this.setWeather('sunny', false);
    },

    createGround() {
      const planeGeo = new THREE.PlaneGeometry(1000, 1000);
      const plane = new THREE.Mesh(planeGeo, this.matGrass);
      plane.rotation.x = -Math.PI / 2;
      plane.position.y = -1.0; // 大幅降低地面高度，避免与土地块冲突
      plane.receiveShadow = true;
      this.scene.add(plane);
    },

    createFarmBlocks() {
      // 整齐的网格布局：每块农田一个独立的土地板子
      const cols = Math.ceil(Math.sqrt(this.farms.length));
      const baseSize = 35; // 基础尺寸（大板子）
      const spacing = 48; // 板子间距（适中的间隙）
      
      this.farms.forEach((farm, index) => {
        const row = Math.floor(index / cols);
        const col = index % cols;
        
        // 简洁的网格位置计算
        const x = (col - cols/2 + 0.5) * spacing;
        const z = (row - cols/2 + 0.5) * spacing;

        // 根据面积略微调整大小，但保持整体一致性
        const areaFactor = Math.sqrt(Number(farm.area) / 10) || 1;
        const size = baseSize * Math.max(0.8, Math.min(1.2, areaFactor));
        const geo = new THREE.BoxGeometry(size, 0.8, size);
        
        const currentMat = this.matSoil.clone();
        // 修复闪烁：使用FrontSide，禁用polygonOffset
        currentMat.side = THREE.FrontSide;
        currentMat.polygonOffset = false;
        if (this.calculateHealthScore(farm) < 60) {
            currentMat.color.setHex(0xdddddd); 
        }
        
        const mesh = new THREE.Mesh(geo, currentMat);
        mesh.position.set(x, 1.0, z); // 抬高土地块，远离地面
        mesh.castShadow = true;
        mesh.receiveShadow = true;
        mesh.userData = { farm: farm };
        
        const edgeGeo = new THREE.EdgesGeometry(geo);
        const edgeMat = new THREE.LineBasicMaterial({ 
            color: 0xFFFFFF, 
            opacity: 0.6, 
            transparent: true,
            depthTest: true, // 启用深度测试，避免渲染顺序混乱
            depthWrite: false // 不写入深度，避免影响其他对象
        });
        const edge = new THREE.LineSegments(edgeGeo, edgeMat);
        edge.position.y = 0.41; // 稍微抬高边框，避免与土地表面完全重合
        edge.renderOrder = 1; // 适度的渲染顺序
        mesh.add(edge);
        mesh.userData.edge = edge;
        mesh.userData.edgeMat = edgeMat;

        this.scene.add(mesh);
        this.farmBlocks.push(mesh);

        this.createSimpleLabel(farm, x, size, z);
        
        // 支持多种作物混种（严格过滤，只加载已配置的作物）
        if (farm.crop && typeof farm.crop === 'string' && farm.crop.trim()) {
          const crops = [...new Set(
            farm.crop.split(',')
            .map(c => this.resolveCropType(c))
            .filter(Boolean)
          )];
          
          if (crops.length > 0) {
            const cropCount = crops.length;
            crops.forEach((cropName, i) => {
               this.loadCropsWithFallback(mesh, cropName, size, i, cropCount);
            });
          }
        }
        // 如果没有设置作物或作物未配置，土地保持空白
      });
    },
    
    createSimpleLabel(farm, x, yOffset, z) {
      const canvas = document.createElement('canvas');
      canvas.width = 256; canvas.height = 128;
      const ctx = canvas.getContext('2d');
      
      ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
      ctx.beginPath();
      ctx.roundRect(10, 20, 236, 88, 15);
      ctx.fill();
      ctx.strokeStyle = '#4CAF50'; 
      ctx.lineWidth = 4;
      ctx.stroke();

      ctx.font = 'bold 36px "Microsoft YaHei"';
      ctx.fillStyle = '#333333';
      ctx.fillText(farm.farm, 30, 65);
      
      ctx.font = '26px "Microsoft YaHei"';
      ctx.fillStyle = '#666666';
      ctx.fillText(`${farm.crop}`, 30, 98);
      
      // 温度过高警告（优先级最高）
      if(Number(farm.temperature) > 30) {
        ctx.fillStyle = '#F44336';
        ctx.fillText('高温', 140, 98);
      }
      // 湿度过低警告
      else if(farm.soilhumidity < 20) {
        ctx.fillStyle = '#F44336';
        ctx.fillText('💧缺水', 140, 98);
      }

      const texture = new THREE.CanvasTexture(canvas);
      texture.colorSpace = THREE.SRGBColorSpace;
      
      const spriteMat = new THREE.SpriteMaterial({ 
          map: texture, transparent: true, depthTest: false,
      });
      
      const sprite = new THREE.Sprite(spriteMat);
      sprite.position.set(x, 6, z);
      sprite.scale.set(10, 5, 1);
      sprite.renderOrder = 999;
      this.scene.add(sprite);
      this.labels.push(sprite);
    },
    
    // 更新标签纹理（用于实时数据更新）
    updateLabelTexture(sprite, farm) {
      if (!sprite || !farm) return;
      
      const canvas = document.createElement('canvas');
      canvas.width = 256; canvas.height = 128;
      const ctx = canvas.getContext('2d');
      
      ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
      ctx.beginPath();
      ctx.roundRect(10, 20, 236, 88, 15);
      ctx.fill();
      ctx.strokeStyle = '#4CAF50'; 
      ctx.lineWidth = 4;
      ctx.stroke();

      ctx.font = 'bold 36px "Microsoft YaHei"';
      ctx.fillStyle = '#333333';
      ctx.fillText(farm.farm, 30, 65);
      
      ctx.font = '26px "Microsoft YaHei"';
      ctx.fillStyle = '#666666';
      ctx.fillText(`${farm.crop}`, 30, 98);
      
      // 温度过高警告（优先级最高）
      if(Number(farm.temperature) > 30) {
        ctx.fillStyle = '#F44336';
        ctx.fillText('高温', 140, 98);
      }
      // 湿度过低警告
      else if(farm.soilhumidity < 20) {
        ctx.fillStyle = '#F44336';
        ctx.fillText('💧缺水', 140, 98);
      }

      // 更新纹理
      const newTexture = new THREE.CanvasTexture(canvas);
      newTexture.colorSpace = THREE.SRGBColorSpace;
      
      // 释放旧纹理
      if (sprite.material.map) {
        sprite.material.map.dispose();
      }
      sprite.material.map = newTexture;
      sprite.material.needsUpdate = true;
    },

    loadCropsWithFallback(parentMesh, cropType, size, index = 0, totalCount = 1) {
      const resolvedCropType = this.resolveCropType(cropType);
      const config = this.cropConfig[resolvedCropType] || this.cropConfig.default;
      if (!config) {
          console.warn(`作物 "${cropType}" 未配置，跳过加载`);
          return;  // 不使用 default，直接跳过
      }
      if (!config.model) {
          this.addGeometricCrops(parentMesh, config, size, index, totalCount);
          return;
      }
      this.gltfLoader.load(config.model, (gltf) => {
          const model = gltf.scene;
          this.scatterModels(parentMesh, model, size, config.scale, index, totalCount, config.yOffset || 0);
        }, undefined, (error) => {
          console.warn(`作物模型 ${config.model} 加载失败，使用几何体代替`);
          this.addGeometricCrops(parentMesh, config, size, index, totalCount);
        }
      );
    },

    scatterModels(parentMesh, modelTemplate, size, scaleFactor, index = 0, totalCount = 1, yOffset = 0) {
      let density = Math.ceil(size / 4); 
      if(density < 2) density = 2; if(density > 4) density = 4;
      for(let i=0; i<density; i++) {
         for(let j=0; j<density; j++) {
            // 根据 index 和 totalCount 分配网格位置，实现混种
            if ((i + j) % totalCount !== index) continue;
            
            const clone = modelTemplate.clone();
            const offsetX = (Math.random() - 0.5) * 1.5;
            const offsetZ = (Math.random() - 0.5) * 1.5;
            const x = (i / density) * size - size/2 + (size/density/2) + offsetX;
            const z = (j / density) * size - size/2 + (size/density/2) + offsetZ;
            if(Math.abs(x) > size/2) continue;
            if(Math.abs(z) > size/2) continue;
            clone.position.set(x, 0.8 + yOffset, z); // 应用作物特定Y偏移 
            clone.rotation.y = Math.random() * Math.PI * 2;
            const s = scaleFactor * (0.8 + Math.random() * 0.4);
            clone.scale.set(s, s, s);
            clone.traverse((node) => { if (node.isMesh) { node.castShadow = true; node.receiveShadow = true; } });
            parentMesh.add(clone);
         }
      }
    },
    
    addGeometricCrops(parentMesh, config, size, index = 0, totalCount = 1) {
      let density = 3;
      let geo;
      if (config.fallback === 'spike') geo = new THREE.ConeGeometry(0.2, 1.2, 4);
      else if (config.fallback === 'column') geo = new THREE.CylinderGeometry(0.3, 0.3, 1.8, 5);
      else geo = new THREE.DodecahedronGeometry(0.4, 0);
      const mat = new THREE.MeshLambertMaterial({ color: config.color });
      for(let i=0; i<density; i++) {
         for(let j=0; j<density; j++) {
            // 根据 index 和 totalCount 分配网格位置
            if ((i + j) % totalCount !== index) continue;

            const x = (i / density) * size - size/2 + (size/density/2);
            const z = (j / density) * size - size/2 + (size/density/2);
            const mesh = new THREE.Mesh(geo, mat);
            mesh.position.set(x + (Math.random()-0.5), 1.4, z + (Math.random()-0.5));
            mesh.scale.setScalar(0.8 + Math.random()*0.4);
            parentMesh.add(mesh);
            if(config.fruitColor) this.addFruits(mesh, config.fruitColor);
         }
      }
    },
    
    addFruits(plantMesh, color) {
      const fruitGeo = new THREE.BoxGeometry(0.25, 0.25, 0.25);
      const fruitMat = new THREE.MeshBasicMaterial({ color: color });
      for(let k=0; k<2; k++) {
        const fruit = new THREE.Mesh(fruitGeo, fruitMat);
        fruit.position.set((Math.random()-0.5)*0.7, (Math.random()-0.5)*0.7, (Math.random()-0.5)*0.7);
        plantMesh.add(fruit);
      }
    },

    initRainSystem() {
      const count = 15000;
      this.rainGeo = new THREE.BufferGeometry();
      const positions = [];
      for(let i=0;i<count;i++) positions.push(Math.random()*400-200, Math.random()*200, Math.random()*400-200);
      this.rainGeo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
      const mat = new THREE.PointsMaterial({ 
          color: 0xaaaaaa, size: 0.5, map: this.textureMap.rain, 
          transparent: true, opacity: 0.6, depthWrite: false, blending: THREE.AdditiveBlending
      }); 
      this.rainSystem = new THREE.Points(this.rainGeo, mat);
      this.rainSystem.visible = false;
      this.scene.add(this.rainSystem);
    },

    initSnowSystem() {
      const count = 10000;
      this.snowGeo = new THREE.BufferGeometry();
      const positions = [];
      for(let i=0;i<count;i++) positions.push(Math.random()*400-200, Math.random()*200, Math.random()*400-200);
      this.snowGeo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
      const mat = new THREE.PointsMaterial({ 
          color: 0xffffff, size: 0.8, map: this.textureMap.snow, 
          transparent: true, opacity: 0.8, depthWrite: false, blending: THREE.AdditiveBlending
      });
      this.snowSystem = new THREE.Points(this.snowGeo, mat);
      this.snowSystem.visible = false;
      this.scene.add(this.snowSystem);
    },
    
    setWeather(mode, showMsg = true) {
      this.weatherMode = mode;
      if (showMsg && !Object.values(this.loadingState).some(s=>s)) this.$message.info(`切换天气: ${this.weatherModeText}`);
      
      if (this.rainSystem) this.rainSystem.visible = false;
      if (this.snowSystem) this.snowSystem.visible = false;
      if(this.lightningMesh) { this.scene.remove(this.lightningMesh); this.lightningMesh = null; }
      
      this.scene.fog.density = 0;

      if(mode === 'sunny') {
         if (this.scene.environment) {
             this.scene.background = this.scene.environment;
         } else {
             new RGBELoader().setPath('/textures/').load('sky.hdr', (tex) => {
                 tex.mapping = THREE.EquirectangularReflectionMapping;
                 this.scene.background = tex;
                 this.scene.environment = tex;
             });
         }
         this.scene.fog = new THREE.Fog(0x87CEEB, 200, 800);
      }
      else if (mode === 'rain') { 
          this.rainSystem.visible = true; 
          this.scene.background = new THREE.Color(0x334455); 
          // 雨天不反光，看起来更暗淡
          // this.scene.environment = null; 
          this.scene.fog = new THREE.Fog(0x334455, 50, 300);
      } else if (mode === 'snow') { 
          this.snowSystem.visible = true; 
          this.scene.background = new THREE.Color(0xccddff); 
          this.scene.fog = new THREE.Fog(0xccddff, 20, 200);
      } else if (mode === 'storm') { 
          this.rainSystem.visible = true; 
          this.scene.background = new THREE.Color(0x050505); 
          this.scene.fog = new THREE.Fog(0x050505, 10, 200);
          const mat = new THREE.SpriteMaterial({ 
              map: this.textureMap.lightning, transparent: true, depthTest: false, blending: THREE.AdditiveBlending
          });
          this.lightningMesh = new THREE.Sprite(mat);
          this.lightningMesh.scale.set(60, 120, 1);
          this.lightningMesh.visible = false;
          this.scene.add(this.lightningMesh);
      }
    },

    animateRain() {
      if (!this.rainSystem || !this.rainSystem.visible) return;
      const positions = this.rainGeo.attributes.position.array;
      const speed = this.weatherMode === 'storm' ? 3.5 : 1.2;
      for(let i=1; i<positions.length; i+=3) {
        positions[i] -= speed;
        if (positions[i] < 0) positions[i] = 200;
      }
      this.rainGeo.attributes.position.needsUpdate = true;
    },

    animateSnow() {
      if (!this.snowSystem || !this.snowSystem.visible) return;
      const positions = this.snowGeo.attributes.position.array;
      for(let i=0; i<positions.length; i+=3) {
        positions[i+1] -= 0.3;
        positions[i] += Math.sin(positions[i+1] * 0.1) * 0.1;
        if (positions[i+1] < 0) { positions[i+1] = 200; positions[i] = Math.random()*400-200; }
      }
      this.snowGeo.attributes.position.needsUpdate = true;
    },
    
    animateStorm() {
      if (this.weatherMode !== 'storm') return;
      if (Math.random() > 0.99) { 
        this.lightningMesh.position.set((Math.random()-0.5)*300, 80, (Math.random()-0.5)*300 - 50);
        this.lightningMesh.visible = true;
        this.scene.background.setHex(0x555577);
        this.scene.fog.color.setHex(0x555577);
        setTimeout(() => {
            if(this.lightningMesh) this.lightningMesh.visible = false;
            this.scene.background.setHex(0x050505);
            this.scene.fog.color.setHex(0x050505);
        }, 100 + Math.random()*100);
      }
    },

    handleFarmClick(farm) {
       this.selectedFarm = farm;
       const targetMesh = this.farmBlocks.find(m => m.userData.farm.id === farm.id);
       if (targetMesh) { 
           this.flyTo(targetMesh.position); 
           this.highlightBlock(targetMesh); 
       }
    },
    onPointerDown(event) {
        this.mouseDownPos = { x: event.clientX, y: event.clientY };
        this.mouseDownTime = Date.now();
    },
    onPointerUp(event) {
        // 计算移动距离和按下时间
        const dx = event.clientX - this.mouseDownPos.x;
        const dy = event.clientY - this.mouseDownPos.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        const duration = Date.now() - this.mouseDownTime;
        
        // 移动距离 > 10像素 或 按下时间 > 300ms 则视为拖拽
        if (distance > 10 || duration > 300) {
            return;
        }
        
        const rect = this.$refs.threeContainer.getBoundingClientRect();
        this.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        this.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
        this.raycaster.setFromCamera(this.mouse, this.camera);
        
        const intersects = this.raycaster.intersectObjects(this.farmBlocks, true);
        if (intersects.length > 0) {
            let object = intersects[0].object;
            while (object && !object.userData.farm && object.parent) object = object.parent;
            
            if (object && object.userData.farm) {
                const farm = object.userData.farm;
                this.selectedFarm = farm; 
                this.highlightBlock(object); 
                this.flyTo(object.position); 
                this.$message.success(`已选中：${farm.farm}（${farm.crop}）`);
            }
        }
    },
    highlightBlock(mesh) { 
        this.farmBlocks.forEach(b => {
             if(b.userData.edgeMat) {
                 b.userData.edgeMat.opacity = 0.6;
                 b.userData.edgeMat.color.setHex(0xFFFFFF);
                 b.userData.edgeMat.linewidth = 1;
             }
        });
        mesh.userData.edgeMat.opacity = 1.0;
        mesh.userData.edgeMat.color.setHex(0xFFEB3B); 
        mesh.userData.edgeMat.linewidth = 3;
    },
    flyTo(position) {
        gsap.to(this.camera.position, { x: position.x + 20, y: position.y + 25, z: position.z + 20, duration: 1.5, ease: "power2.inOut" });
        gsap.to(this.controls.target, { x: position.x, y: position.y, z: position.z, duration: 1.5, ease: "power2.inOut" });
    },
    resetCamera() {
        gsap.to(this.camera.position, { x: 50, y: 60, z: 80, duration: 1.5 });
        gsap.to(this.controls.target, { x: 0, y: 0, z: 0, duration: 1.5 });
    },
    onWindowResize() {
        if(this.camera && this.renderer && this.$refs.threeContainer) {
            const w = this.$refs.threeContainer.clientWidth;
            const h = this.$refs.threeContainer.clientHeight;
            this.camera.aspect = w / h;
            this.camera.updateProjectionMatrix();
            this.renderer.setSize(w, h);
        }
    },
    animate() {
        this.requestId = requestAnimationFrame(() => this.animate());
        this.controls.update();
        this.animateRain();
        this.animateSnow();
        this.animateStorm();
        const time = Date.now() * 0.001;
        this.labels.forEach((l, i) => { l.position.y = 6 + Math.sin(time + i) * 0.3; });
        this.renderer.render(this.scene, this.camera);
    },
    calculateHealthScore(farm) {
        if (!farm) return 0;
        let score = 100;
        // 温度过高扣分（>30°C）
        if (Number(farm.temperature) > 30) {
          const tempDiff = Number(farm.temperature) - 30;
          score -= Math.min(30, tempDiff * 3); // 每超过1度扣3分，最多扣30分
        }
        // 湿度过低扣分（<20%）
        if (Number(farm.soilhumidity) < 20) {
          const humidityDiff = 20 - Number(farm.soilhumidity);
          score -= Math.min(25, humidityDiff * 2); // 每低1%扣2分，最多扣25分
        }
        return Math.max(0, score); // 确保分数不低于0
    },
    getAiAdvice(farm) { return ""; }
  }
};
</script>

<style scoped>
.farm-map-nature { 
  width: 100%; 
  height: 100vh; 
  background: #e3f2fd; 
  color: #333; 
  position: relative; 
  overflow: hidden !important; 
  font-family: 'Microsoft YaHei', 'Segoe UI', sans-serif; 
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.farm-map-nature::-webkit-scrollbar { display: none; }

.nature-card {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  padding: 16px;
  border: 1px solid #fff;
  backdrop-filter: blur(5px);
}

.top-bar { 
  position: absolute; top: 0; left: 0; width: 100%; height: 64px; z-index: 10; 
  display: flex; justify-content: space-between; align-items: center; padding: 0 24px; 
  background: rgba(255,255,255,0.95); 
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}
.title { display: flex; align-items: center; gap: 12px; }
.logo-icon { font-size: 28px; }
.title-text { display: flex; flex-direction: column; }
.title-text .main { font-size: 18px; font-weight: bold; color: #2E7D32; } 
.title-text .sub { font-size: 12px; color: #888; letter-spacing: 1px; }
.right-area { display: flex; gap: 16px; align-items: center; }
.weather-badge, .time-badge {
    background: #f1f8e9; color: #558b2f;
    padding: 6px 12px; border-radius: 20px; font-size: 14px; font-weight: 500;
    display: flex; align-items: center; gap: 6px;
}
.weather-img { width: 20px; height: 20px; object-fit: contain; }
.time-img { width: 16px; height: 16px; object-fit: contain; margin-right: 4px; }

.main-container { display: flex; height: calc(100vh - 64px); padding-top: 74px; padding-bottom: 8px; padding-left: 10px; padding-right: 10px; gap: 10px; box-sizing: border-box; overflow: hidden; }
.left-panel { width: 280px; display: flex; flex-direction: column; gap: 8px; z-index: 5; pointer-events: none; max-height: 100%; }
.left-panel > * { pointer-events: auto; }

.stats-row { display: flex; gap: 8px; }
.stat-box { flex: 1; display: flex; align-items: center; gap: 8px; padding: 8px; }
.stat-img { width: 32px; height: 32px; object-fit: contain; }
.stat-icon { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 16px; }
.green-bg { background: #E8F5E9; color: #2E7D32; }
.orange-bg { background: #FFF3E0; color: #EF6C00; }
.stat-num { font-size: 16px; font-weight: bold; color: #333; }
.stat-label { font-size: 11px; color: #666; }

.list-card { flex: 1; overflow: hidden; display: flex; flex-direction: column; padding: 0; min-height: 0; max-height: calc(100vh - 220px); }
.card-header { padding: 8px 12px; border-bottom: 1px solid #eee; font-weight: bold; color: #333; background: #fafafa; font-size: 13px; display: flex; align-items: center; gap: 6px; }
.header-img { width: 18px; height: 18px; object-fit: contain; }
.farm-list { flex: 1; overflow-y: auto; padding: 4px; scrollbar-width: none; -ms-overflow-style: none; }
.farm-list::-webkit-scrollbar { display: none; }
.farm-item { 
    display: flex; align-items: center; padding: 6px 8px; border-radius: 6px; margin-bottom: 4px; cursor: pointer; transition: 0.2s; 
    background: #fff; border: 1px solid #eee;
}
.farm-item:hover, .farm-item.active { background: #F1F8E9; border-color: #8BC34A; }
.crop-icon-img { width: 24px; height: 24px; object-fit: contain; margin-right: 6px; }
.item-center { flex: 1; }
.f-name { font-weight: bold; font-size: 13px; color: #333; }
.f-info { font-size: 11px; color: #666; margin-top: 1px; display: flex; align-items: center; }
.info-icon { width: 14px; height: 14px; object-fit: contain; margin-right: 2px; }
.status-tag { font-size: 10px; padding: 2px 6px; border-radius: 4px; }
.bg-green { background: #E8F5E9; color: #2E7D32; }
.bg-red { background: #FFEBEE; color: #C62828; }
.text-danger { color: #C62828; font-weight: bold; }

.center-panel { flex: 1; position: relative; border-radius: 12px; overflow: hidden; box-shadow: inset 0 0 20px rgba(0,0,0,0.05); background: #87CEEB; max-height: calc(100vh - 90px); }
.map-3d { width: 100%; height: 100%; }

.scene-controls {
    position: absolute; bottom: 24px; left: 50%; transform: translateX(-50%);
    background: #fff; padding: 6px 12px; border-radius: 30px;
    display: flex; align-items: center; gap: 12px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    font-size: 14px;
}
.capsule-btn { cursor: pointer; color: #555; padding: 4px 8px; border-radius: 15px; transition: 0.2s; }
.capsule-btn:hover { background: #f5f5f5; color: #000; }
.divider { width: 1px; height: 16px; background: #ddd; }
.icon-group { display: flex; gap: 8px; font-size: 18px; }
.icon-group span { cursor: pointer; opacity: 0.6; transition: 0.2s; }
.icon-group span:hover, .icon-group span.active { opacity: 1; transform: scale(1.2); }
.weather-icon { width: 24px; height: 24px; object-fit: contain; vertical-align: middle; }

.right-panel { width: 280px; display: flex; flex-direction: column; gap: 8px; z-index: 5; pointer-events: none; max-height: calc(100vh - 90px); overflow: hidden; }
.right-panel > * { pointer-events: auto; }
.detail-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.farm-badge h3 { margin: 0 0 4px 0; color: #333; font-size: 18px; }
.type-pill { background: #E0F2F1; color: #00695C; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.health-circle { position: relative; display: flex; flex-direction: column; align-items: center; }
.circle-text { 
  position: absolute; 
  top: 60%; 
  left: 50%; 
  transform: translate(-50%, 0); 
  font-size: 12px; 
  text-align: center; 
  color: #888; 
  z-index: 1; 
  white-space: nowrap;
}

.env-grid { display: flex; flex-direction: column; gap: 12px; }
.env-item { display: flex; align-items: center; gap: 10px; font-size: 12px; }
.env-item .label { width: 50px; color: #666; }
.bar-box { flex: 1; display: flex; flex-direction: column; }
.bar-box .val { font-weight: bold; color: #333; margin-bottom: 2px; }

.ai-note-fixed { 
  background: #FFF9C4; 
  color: #5D4037; 
  border: none; 
  padding: 12px; 
}
.control-card-white {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  padding: 10px;
}
.note-header { font-weight: bold; border-bottom: 1px dashed #FBC02D; padding-bottom: 8px; margin-bottom: 8px; }
.note-content { font-size: 13px; line-height: 1.5; }
.warning-text { color: #F56C6C; font-weight: bold; animation: pulse 2s infinite; display: flex; align-items: center; gap: 4px; }
.warning-icon { width: 16px; height: 16px; object-fit: contain; }
.normal-text { color: #67C23A; }
@keyframes pulse { 0%{opacity:1} 50%{opacity:0.6} 100%{opacity:1} }

.switch-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  transition: all 0.3s ease;
}
.switch-item:hover { background: #f1f5f9; }
.switch-item.active {
  background: #f0fdf4;
  border-color: #10b981;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.15);
}
.switch-left { display: flex; align-items: center; gap: 10px; }
.switch-icon {
  width: 36px; height: 36px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
}
.water-icon { background: linear-gradient(135deg, #d1fae5, #a7f3d0); }
.fertilizer-icon { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.pest-icon { background: linear-gradient(135deg, #dbeafe, #bfdbfe); }
.switch-img { width: 20px; height: 20px; object-fit: contain; }
.switch-info { display: flex; flex-direction: column; }
.switch-name { font-size: 13px; font-weight: 600; color: #5D4037; }
.switch-status { font-size: 11px; color: #9ca3af; }
.switch-item.active .switch-status { color: #10b981; }

.data-source { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #e0e0e0; text-align: center; }
.source-tag { font-size: 11px; color: #666; background: #f5f5f5; padding: 3px 10px; border-radius: 12px; }

::-webkit-scrollbar { width: 0; display: none; }
::-webkit-scrollbar-thumb { background: transparent; }
::-webkit-scrollbar-track { background: transparent; }

</style>

<style scoped>
:deep(.action-btn.el-button) { transition: none !important; }
:deep(.action-btn.el-button:hover),
:deep(.action-btn.el-button:focus),
:deep(.action-btn.el-button:active) { transform: none !important; transition: none !important; }
:deep(.action-btn.el-button span) {
  display: flex !important; align-items: center !important;
  justify-content: center !important; width: 100% !important; height: 100% !important;
}
:deep(.action-btn .action-img) {
  position: static !important; margin: 0 !important;
  transform: none !important; transition: none !important;
}
</style>
