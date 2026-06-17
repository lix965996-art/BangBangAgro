<template>
  <div class="gis-cockpit">
    <!-- 全屏地图 -->
    <div id="gaode-container" class="fullscreen-map"></div>
    
    <!-- HUD: 左侧农田列表 -->
    <div class="hud-left-panel">
      <div class="hud-header">
        <div class="hud-title">
          <i class="el-icon-location"></i>
          <span>地块监控</span>
        </div>
        <div class="hud-status">
          <span class="status-dot"></span>
          <span class="status-text">ONLINE</span>
        </div>
      </div>
      
      <div class="hud-search">
        <input 
          v-model="searchKeyword" 
          placeholder="搜索地块..." 
          class="hud-input"
        />
      </div>

      <div class="region-list">
        <!-- 按区域分组显示 -->
        <div v-for="(group, region) in groupedFarms" :key="region" class="region-section">
          <div class="region-header" @click="toggleRegion(region)">
            <i :class="expandedRegions[region] ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" class="expand-icon"></i>
            <i class="el-icon-location-outline region-icon"></i>
            <span class="region-name">{{ region }}</span>
            <span class="region-count">({{ group.length }})</span>
          </div>
          
          <div v-show="expandedRegions[region]" class="farm-list">
            <div 
              v-for="(farm, index) in group" 
              :key="farm.id || index" 
              class="farm-item"
              :class="{ 'active': currentFarm && currentFarm.id === farm.id }"
              @click="focusFarm(farm)"
            >
              <div class="farm-icon">
                <span class="crop-initial">{{ farm.crop ? farm.crop[0] : '农' }}</span>
              </div>
              <div class="farm-info">
                <div class="farm-name">{{ farm.farm }}</div>
                <div class="farm-details">
                  <span class="area">{{ farm.area || 0 }}亩</span>
                  <span class="crop">{{ farm.crop || '未知' }}</span>
                  <span :class="'status-' + getFarmStatus(farm).type" class="status">{{ getFarmStatus(farm).label }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-if="Object.keys(groupedFarms).length === 0" description="未找到地块" :image-size="60"></el-empty>
      </div>
    </div>

    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <span class="welcome-icon">🌱</span>
      <span class="welcome-text">欢迎回到 10号示范田，今日气象适宜。</span>
    </div>

    <!-- 顶部数据栏 -->
    <div class="data-metrics">
      <div class="metric-card">
        <div class="metric-label">监测地块</div>
        <div class="metric-value">{{ allFarms.length }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">异常提示</div>
        <div class="metric-value alert">{{ warningCount }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">土壤湿度</div>
        <div class="metric-value">{{ avgHumidity }}%</div>
      </div>
    </div>

    <!-- 图层控制器 - 圆润胶囊风格 -->
    <div class="layer-controller">
      <div class="layer-pill" :class="{active: layers.satellite}" @click="toggleLayer('satellite')">
        <i class="el-icon-view"></i>
        <span>卫星光谱</span>
      </div>
      <div class="layer-pill" :class="{active: layers.underground}" @click="toggleLayer('underground')">
        <i class="el-icon-connection"></i>
        <span>管网水流</span>
      </div>
      <div class="layer-pill" :class="{active: layers.drone}" @click="toggleLayer('drone')">
        <i class="el-icon-airplane"></i>
        <span>巡检轨迹</span>
      </div>
    </div>

    <div v-if="mapError" class="map-error-overlay">
      <div class="error-content">
        <i class="el-icon-warning-outline"></i>
        <p>地图资源加载失败</p>
        <p class="error-detail">{{ mapErrorMessage }}</p>
        <el-button type="primary" size="medium" @click="retryLoadMap" icon="el-icon-refresh">重新加载</el-button>
      </div>
    </div>

    <!-- HUD: 右侧智能详情卡片 -->
    <transition name="fade">
      <div class="hud-detail-card" v-if="currentFarm">
        <div class="detail-header">
          <div class="header-left">
            <div class="dh-title">{{ currentFarm.farm }}</div>
            <div class="dh-address">
              <i class="el-icon-location" style="margin-right: 4px;"></i>
              {{ currentFarm.address || '位置信息未录入' }}
            </div>
          </div>
          <el-button circle icon="el-icon-close" size="mini" @click="currentFarm = null"></el-button>
        </div>
        
        <div class="detail-body">
          <div class="kpi-row">
            <div class="kpi-box">
              <div class="k-val">{{ currentFarm.area || 0 }}<small>亩</small></div>
              <div class="k-label">种植面积</div>
            </div>
            <div class="kpi-box">
              <div class="k-val">{{ currentFarm.crop || '-' }}</div>
              <div class="k-label">作物类型</div>
            </div>
            <div class="kpi-box">
              <div class="k-val" :style="{color: currentFarmStatus.color}">{{ currentFarmStatus.label }}</div>
              <div class="k-label">当前状态</div>
            </div>
          </div>

          <el-divider></el-divider>

          <div class="env-list">
            <div class="env-item">
              <div class="env-icon"><i class="el-icon-sunny"></i></div>
              <div class="env-info">
                <div class="env-name">光照强度</div>
                <el-progress :percentage="80" :stroke-width="6" color="#F7BA2A"></el-progress>
              </div>
              <div class="env-val">High</div>
            </div>
            <div class="env-item">
              <div class="env-icon"><i class="el-icon-heavy-rain"></i></div>
              <div class="env-info">
                <div class="env-name">土壤水分</div>
                <el-progress :percentage="35" :stroke-width="6" status="exception"></el-progress>
              </div>
              <div class="env-val warning">Low</div>
            </div>
          </div>

          <div class="action-group">
            <el-button type="primary" icon="el-icon-video-camera" style="width: 100%" @click="$message.success('正在连接无人机画面...')">
              调用实时监控
            </el-button>
          </div>
        </div>
      </div>
    </transition>

    <div class="map-tools">
      <el-tooltip content="复位地图" placement="left">
        <div class="tool-btn" @click="resetView"><i class="el-icon-refresh"></i></div>
      </el-tooltip>
      <el-tooltip content="定位到我" placement="left">
        <div class="tool-btn" @click="autoLocate"><i class="el-icon-aim"></i></div>
      </el-tooltip>
    </div>
  </div>
</template>

<script>
import { loadAmapSdk, resetAmapLoader } from '@/utils/amapLoader'

export default {
  name: 'FarmMapGaode',
  data() {
    return {
      map: null,
      amapKey: import.meta.env.VUE_APP_AMAP_JS_KEY || '',
      securityCode: import.meta.env.VUE_APP_AMAP_SECURITY_CODE || '',
      allFarms: [],
      searchKeyword: '',
      currentFarm: null,
      mapPitch: 60,
      mapError: false,
      mapErrorMessage: '',
      userMarker: null,
      expandedRegions: {
        Jishou: true,
        Wulingyuan: true,
        Yongding: true
      },
      layers: {
        satellite: false,
        underground: false,
        drone: false
      },
      undergroundPipes: [
        { path: [[110.478, 29.116], [110.479, 29.117], [110.480, 29.118]], color: '#0ea5e9' },
        { path: [[110.477, 29.115], [110.479, 29.116], [110.481, 29.117]], color: '#0ea5e9' }
      ],
      dronePath: [
        [110.478, 29.120],
        [110.480, 29.119],
        [110.482, 29.118],
        [110.481, 29.116]
      ],
      pipeLines: [],
      droneMarker: null,
      dronePathLine: null
    }
  },
  computed: {
    displayFarms() {
      if (!this.searchKeyword) return this.allFarms;
      return this.allFarms.filter(f => f.farm && f.farm.includes(this.searchKeyword));
    },
    groupedFarms() {
      const farms = this.searchKeyword ? this.displayFarms : this.allFarms;
      const groups = {};
      
      // 使用正则表达式从地址中提取区县名称进行分组
      farms.forEach(farm => {
        const region = this.getRegionByFarm(farm);
        
        if (!groups[region]) {
          groups[region] = [];
        }
        groups[region].push(farm);
      });
      
      // 按照优先级排序：主要区县在前，"未知区县"在最后（参考Django版本）
      const priorityOrder = ['永定区', '武陵源区', '慈利县', '桑植县'];
      const sortedGroups = {};
      
      // 先添加优先区县
      priorityOrder.forEach(region => {
        if (groups[region]) {
          sortedGroups[region] = groups[region];
        }
      });
      
      // 再添加其他动态识别的区县
      Object.keys(groups).forEach(region => {
        if (!priorityOrder.includes(region) && region !== '未知区县') {
          sortedGroups[region] = groups[region];
        }
      });
      
      // 最后添加"未知区县"（参考Django版本）
      if (groups['未知区县']) {
        sortedGroups['未知区县'] = groups['未知区县'];
      }
      
      return sortedGroups;
    },
    warningCount() {
      return this.allFarms.filter(f => this.getFarmStatus(f).type !== 'success').length;
    },
    currentFarmStatus() {
      return this.currentFarm ? this.getFarmStatus(this.currentFarm) : {};
    },
    // 平均土壤湿度
    avgHumidity() {
      if (this.allFarms.length === 0) return 0;
      const total = this.allFarms.reduce((sum, f) => sum + (Number(f.soilhumidity) || 0), 0);
      return Math.round(total / this.allFarms.length);
    }
  },
  mounted() {
    this.loadAmapScript();
    this.loadFarms();
  },
  activated() {
    // 支持 keep-alive：组件被激活时重新加载数据
    
    // 使用 nextTick 确保 DOM 已经渲染完成
    this.$nextTick(() => {
      const container = document.getElementById('gaode-container');
      
      if (!container) {
        console.error('地图容器不存在，等待下次激活');
        return;
      }
      
      // 检查地图实例状态
      if (this.map) {
        try {
          // 验证地图实例是否有效
          this.map.getZoom();
          this.loadFarms();
        } catch (e) {
          // 地图实例无效，需要重新初始化
          console.warn('地图实例无效，重新初始化:', e);
          this.map = null;
          this.loadAmapScript();
          this.loadFarms();
        }
      } else {
        // 地图不存在，重新初始化
        this.loadAmapScript();
        this.loadFarms();
      }
    });
  },
  beforeUnmount() {
    this.cleanupMap();
  },
  deactivated() {
    // 支持 keep-alive：组件失活时不销毁地图，保持状态
  },
  methods: {
    cleanupMap() {
      try {
        // 清理地图覆盖物
        if (this.map) {
          this.map.clearMap();
          this.map.destroy();
          this.map = null;
        }
        
        // 清理相关标记
        this.userMarker = null;
        this.pipeLines = [];
        this.droneMarker = null;
        this.dronePathLine = null;
      } catch (e) {
        console.error('清理地图时出错:', e);
      }
    },
    
    retryLoadMap() {
      this.mapError = false;
      this.mapErrorMessage = '';
      // 清理旧实例
      this.cleanupMap();
      resetAmapLoader();
      this.loadAmapScript();
    },
    
    getFarmStatus(farm) {
      const temperature = Number(farm.temperature || 0);
      const humidity = Number(farm.soilhumidity || 0);
      const stateText = String(farm.state || farm.status || '').toLowerCase();

      if (stateText.includes('warning') || stateText.includes('error') || stateText.includes('abnormal') || temperature >= 35) {
        return { type: 'danger', color: '#F56C6C', label: 'ALERT' };
      }
      if ((humidity > 0 && humidity < 20) || (temperature > 0 && temperature >= 30) || stateText.includes('pending')) {
        return { type: 'warning', color: '#E6A23C', label: 'WATCH' };
      }
      return { type: 'success', color: '#67C23A', label: 'OK' };
    },

    getRegionByFarm(farm) {
      // 优先使用数据库中的district字段
      if (farm.district && farm.district.trim()) {
        const district = farm.district.trim();
        
        // 如果district是完整格式（如"长沙市岳麓区"），提取区县部分用于分组
        const match = district.match(/市([\u4e00-\u9fa5]{2,3}(?:区|县))/);
        if (match) {
          return match[1]; // 返回"岳麓区"
        }
        
        // 如果district只是区县名（如"永定区"），直接返回
        if (district.match(/^[\u4e00-\u9fa5]{2,3}(?:区|县)$/)) {
          return district;
        }
        
        // 其他格式直接返回
        return district;
      }
      
      // 如果district字段为空，从地址中提取（兼容旧数据）
      const address = farm.address || '';
      
      if (!address) {
        return '未知区县';  // 统一使用"未知区县"
      }
      
      // 精确匹配：X市 + (2-3个汉字) + 区/县
      const match = address.match(/市([\u4e00-\u9fa5]{2,3}(?:区|县))/);
      
      if (match) {
        return match[1];
      }
      
      // 无法识别，归类到"未知区县"（参考Django版本）
      return '未知区县';
    },

    toggleRegion(region) {
      this.expandedRegions[region] = !this.expandedRegions[region];
    },

    async loadFarms() {
      try {
        const res = await this.request.get('/statistic/page', { params: { pageNum: 1, pageSize: 1000 } });
        
        if (res.code === '200') {
          this.allFarms = res.data.records || [];
          
          // 数据验证
          const invalidFarms = this.allFarms.filter(farm => {
            if (!farm.farm) return true;  // 没有名称
            if (farm.coordinates) {
              try {
                const coords = JSON.parse(farm.coordinates);
                if (!Array.isArray(coords) || coords.length < 3) return true;
              } catch (e) {
                return true;  // 坐标解析失败
              }
            }
            return false;
          });
          
          if (invalidFarms.length > 0) {
            console.warn(`发现 ${invalidFarms.length} 个数据异常的农田:`, 
              invalidFarms.map(f => f.farm || f.id));
          }
          
          // 渲染地图元素
          if (this.map) {
            this.renderMapElements();
          } else {
            console.warn('地图实例尚未创建，等待地图加载完成后渲染');
          }
        } else {
          console.error('农田数据加载失败:', res.msg);
          this.$message.error('农田数据加载失败: ' + (res.msg || '未知错误'));
        }
      } catch (e) {
        console.error('加载农田数据时发生错误:', e);
        this.$message.error('加载农田数据失败，请刷新页面重试');
      }
    },

    async loadAmapScript() {
      this.mapError = false;
      this.mapErrorMessage = '';

      try {
        await loadAmapSdk({
          key: this.amapKey,
          securityCode: this.securityCode,
          plugins: ['AMap.Scale', 'AMap.ToolBar', 'AMap.Geolocation']
        });
        this.initMap();
      } catch (error) {
        console.error('高德地图脚本加载失败:', error);
        this.mapError = true;
        this.mapErrorMessage = this.resolveMapErrorMessage(error);
        this.$message.error(this.mapErrorMessage);
      }
    },

    resolveMapErrorMessage(error) {
      if (!this.amapKey) {
        return '未配置 VUE_APP_AMAP_JS_KEY，地图无法初始化';
      }
      if (!this.securityCode) {
        return '未配置 VUE_APP_AMAP_SECURITY_CODE，地图无法初始化';
      }
      if (typeof navigator !== 'undefined' && navigator.onLine === false) {
        return '当前浏览器处于离线状态，请先恢复网络连接';
      }
      if (error && error.message === 'AMAP_NETWORK_ERROR') {
        return '高德地图资源请求被网络、代理或防火墙拦截，请检查本机网络环境';
      }
      if (error && error.message === 'AMAP_SDK_UNAVAILABLE') {
        return '高德地图脚本已返回，但 SDK 未初始化，请检查 Key、SecurityCode 和域名白名单';
      }
      return '高德地图加载失败，请检查网络环境以及 Key/SecurityCode 配置';
    },

    initMap() {
      if (!window.AMap) {
        console.error('AMap 对象不存在');
        this.mapError = true;
        return;
      }
      
      // 检查容器是否存在并且有尺寸
      const checkContainer = () => {
        const container = document.getElementById('gaode-container');
        if (!container) {
          console.error('地图容器 #gaode-container 不存在');
          return false;
        }
        
        const rect = container.getBoundingClientRect();
        if (rect.width === 0 || rect.height === 0) {
          console.warn('地图容器尺寸为0，width:', rect.width, 'height:', rect.height);
          return false;
        }
        
        return true;
      };
      
      // 如果容器还没准备好，延迟重试
      if (!checkContainer()) {
        setTimeout(() => {
          if (!checkContainer()) {
            console.error('容器超时未就绪，初始化失败');
            this.mapError = true;
            return;
          }
          this.initMap();
        }, 100);
        return;
      }
      
      // 如果地图已存在，先销毁
      if (this.map) {
        this.cleanupMap();
      }


      this.$nextTick(() => {
        try {
          this.map = new window.AMap.Map('gaode-container', {
            viewMode: '3D', 
            pitch: this.mapPitch,
            rotation: 0,
            zoom: 16,
            center: [110.479, 29.117],
            mapStyle: 'amap://styles/normal',
            resizeEnable: true
          });


          // 叠加卫星图层
          const satellite = new window.AMap.TileLayer.Satellite();
          const roadNet = new window.AMap.TileLayer.RoadNet();
          this.map.add([satellite, roadNet]);

          // 添加比例尺
          this.map.addControl(new window.AMap.Scale({ 
            position: 'LB',
            maxWidth: 200
          }));
          
          // 添加工具栏
          this.map.addControl(new window.AMap.ToolBar({
            position: { top: '110px', right: '10px' }
          }));

          this.map.on('complete', () => {
            this.renderMapElements();
          });
          
          this.mapError = false;
          this.$message.success('地图加载成功');
        } catch (e) {
          console.error('地图初始化错误:', e);
          this.mapError = true;
          this.$message.error('地图初始化失败: ' + e.message);
        }
      });
    },

    handlePitchChange(val) {
      if(this.map) this.map.setPitch(val);
    },

    // 计算多边形的几何中心点
    calculatePolygonCenter(coordinates) {
      if (!coordinates || coordinates.length < 3) {
        return [110.479, 29.117]; // 默认中心点
      }

      let sumLng = 0;
      let sumLat = 0;
      let validPoints = 0;

      // 计算所有坐标点的平均值
      coordinates.forEach(coord => {
        const lng = parseFloat(coord.lng);
        const lat = parseFloat(coord.lat);
        
        if (!isNaN(lng) && !isNaN(lat)) {
          sumLng += lng;
          sumLat += lat;
          validPoints++;
        }
      });

      if (validPoints === 0) {
        return [110.479, 29.117]; // 默认中心点
      }

      // 返回几何中心点
      return [sumLng / validPoints, sumLat / validPoints];
    },

    renderMapElements() {
      if (!this.map) {
        console.warn('地图实例不存在，无法渲染元素');
        return;
      }
      
      try {
        // 清除地图上的所有覆盖物
        this.map.clearMap();
        
        // 保留用户位置标记
        if (this.userMarker) {
          this.map.add(this.userMarker);
        }
        
        let validFarmCount = 0;
        let invalidFarmCount = 0;
        
        this.allFarms.forEach(farm => {
          try {
            const status = this.getFarmStatus(farm);
            
            // 1. 绘制多边形
            if (farm.coordinates) {
              try {
                const coordsData = JSON.parse(farm.coordinates);
                
                // 验证坐标数据
                if (!Array.isArray(coordsData) || coordsData.length < 3) {
                  console.warn(`农田 [${farm.farm}] 坐标点数量不足，至少需要3个点`);
                  throw new Error('坐标点数量不足');
                }
                
                // 转换坐标并验证
                const path = coordsData.map(c => {
                  const lng = parseFloat(c.lng);
                  const lat = parseFloat(c.lat);
                  
                  // 验证经纬度范围
                  if (isNaN(lng) || isNaN(lat)) {
                    throw new Error('坐标格式无效');
                  }
                  if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
                    throw new Error('坐标超出有效范围');
                  }
                  
                  return [lng, lat];
                });
                
                const polygon = new window.AMap.Polygon({
                  path: path,
                  strokeColor: status.color,
                  strokeWeight: 3,
                  strokeOpacity: 1,
                  fillColor: status.color,
                  fillOpacity: 0.4, 
                  zIndex: 10,
                  bubble: true
                });
                polygon.on('click', () => this.focusFarm(farm));
                this.map.add(polygon);
                
              } catch(coordError) {
                console.error(`农田 [${farm.farm}] 坐标解析失败:`, coordError.message);
                invalidFarmCount++;
              }
            }

            // 2. 绘制标签
            let center = [110.479, 29.117];  // 默认中心点
            
            // 如果有多边形坐标，计算多边形的几何中心点
            if (farm.coordinates) {
              try {
                const coordsData = JSON.parse(farm.coordinates);
                if (Array.isArray(coordsData) && coordsData.length >= 3) {
                  // 计算多边形的几何中心（重心）
                  center = this.calculatePolygonCenter(coordsData);
                }
              } catch (e) {
                // 如果坐标解析失败，尝试使用数据库中的中心点
                if (farm.centerLng && farm.centerLat) {
                  const lng = parseFloat(farm.centerLng);
                  const lat = parseFloat(farm.centerLat);
                  
                  if (!isNaN(lng) && !isNaN(lat) && 
                      lng >= -180 && lng <= 180 && 
                      lat >= -90 && lat <= 90) {
                    center = [lng, lat];
                  }
                }
              }
            } else if (farm.centerLng && farm.centerLat) {
              // 没有多边形坐标时，使用数据库中的中心点
              const lng = parseFloat(farm.centerLng);
              const lat = parseFloat(farm.centerLat);
              
              if (!isNaN(lng) && !isNaN(lat) && 
                  lng >= -180 && lng <= 180 && 
                  lat >= -90 && lat <= 90) {
                center = [lng, lat];
              }
            }
            
            const text = new window.AMap.Text({
              text: `<div class="map-tag" style="border-color:${status.color}; color:${status.color}">${farm.farm || '未命名'}</div>`,
              anchor: 'center',
              position: center,
              offset: new window.AMap.Pixel(0, 0),  // 移除偏移，让标签完全居中
              zIndex: 20
            });
            text.on('click', () => this.focusFarm(farm));
            this.map.add(text);
            
            validFarmCount++;
            
          } catch (farmError) {
            console.error(`渲染农田 [${farm.farm || farm.id}] 时出错:`, farmError);
            invalidFarmCount++;
          }
        });
        
        
        if (invalidFarmCount > 0) {
          this.$message.warning(`有 ${invalidFarmCount} 个农田数据异常，请检查坐标信息`);
        }
        
      } catch (error) {
        console.error('渲染地图元素时发生严重错误:', error);
        this.$message.error('地图渲染失败: ' + error.message);
        this.mapError = true;
      }
    },

    focusFarm(farm) {
      this.currentFarm = farm;
      let target = [110.479, 29.117];
      if(farm.centerLng) target = [farm.centerLng, farm.centerLat];
      
      this.map.setZoomAndCenter(18, target);
    },

    resetView() {
      this.map.setZoomAndCenter(14, [110.479, 29.117]);
      this.mapPitch = 60;
      this.map.setPitch(60);
    },
    
    // 图层控制
    toggleLayer(layerName) {
      this.layers[layerName] = !this.layers[layerName];
      
      if (layerName === 'satellite') {
        this.toggleSatelliteLayer();
      } else if (layerName === 'underground') {
        this.toggleUndergroundLayer();
      } else if (layerName === 'drone') {
        this.toggleDroneLayer();
      }
    },
    
    // 卫星光谱图层（NDVI伪彩色）
    toggleSatelliteLayer() {
      if (this.layers.satellite) {
        // 为所有多边形添加NDVI伪彩色效果
        this.renderMapElements();
        this.$message.success('卫星光谱图层已开启');
      } else {
        this.renderMapElements();
        this.$message.info('卫星光谱图层已关闭');
      }
    },
    
    // 地下管网图层 - 流动水网特效
    toggleUndergroundLayer() {
      if (this.layers.underground) {
        // 绘制管网线条
        this.undergroundPipes.forEach((pipe, index) => {
          const line = new window.AMap.Polyline({
            path: pipe.path,
            strokeColor: '#38bdf8',
            strokeWeight: 5,
            strokeStyle: 'dashed',
            strokeOpacity: 0,
            strokeDasharray: [10, 10],
            zIndex: 5,
            extData: { pipeIndex: index }
          });
          
          // 添加水光晕效果（通过描边模拟）
          const glowLine = new window.AMap.Polyline({
            path: pipe.path,
            strokeColor: '#38bdf8',
            strokeWeight: 8,
            strokeOpacity: 0,
            zIndex: 4
          });
          
          this.pipeLines.push(line, glowLine);
          this.map.add([line, glowLine]);
          
          // 渐入动画
          setTimeout(() => {
            line.setOptions({ strokeOpacity: 0.85 });
            glowLine.setOptions({ strokeOpacity: 0.15 });
          }, index * 200);
          
          // 添加阀门节点（管道端点）
          pipe.path.forEach((point, idx) => {
            if (idx === 0 || idx === pipe.path.length - 1) {
              const valve = new window.AMap.Marker({
                position: point,
                content: `<div class="valve-node"><div class="valve-pulse"></div></div>`,
                offset: new window.AMap.Pixel(-6, -6),
                zIndex: 10
              });
              this.pipeLines.push(valve);
              this.map.add(valve);
            }
          });
        });
        
        this.$message.success('💧 智慧灌溉管网已激活');
      } else {
        // 渐出动画后移除
        this.pipeLines.forEach((item, index) => {
          setTimeout(() => {
            this.map.remove(item);
          }, index * 50);
        });
        this.pipeLines = [];
        this.$message.info('管网图层已关闭');
      }
    },
    
    // 无人机空域图层 - 激光雷达扫描特效
    toggleDroneLayer() {
      if (this.layers.drone) {
        // 绘制无人机路径（虽然色）
        this.dronePathLine = new window.AMap.Polyline({
          path: this.dronePath,
          strokeColor: '#f59e0b',
          strokeWeight: 3,
          strokeStyle: 'dashed',
          strokeOpacity: 0,
          strokeDasharray: [8, 8],
          zIndex: 100
        });
        this.map.add(this.dronePathLine);
        
        // 渐入路径
        setTimeout(() => {
          this.dronePathLine.setOptions({ strokeOpacity: 0.6 });
        }, 300);
        
        // 添加无人机标记（带雷达扫描和投影）
        this.droneMarker = new window.AMap.Marker({
          position: this.dronePath[0],
          content: `
            <div class="drone-container">
              <div class="drone-shadow"></div>
              <div class="radar-scan"></div>
              <div class="drone-body">
                <svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <filter id="drone-shadow" x="-50%" y="-50%" width="200%" height="200%">
                      <feGaussianBlur in="SourceAlpha" stdDeviation="2"/>
                      <feOffset dx="0" dy="2" result="offsetblur"/>
                      <feComponentTransfer>
                        <feFuncA type="linear" slope="0.3"/>
                      </feComponentTransfer>
                      <feMerge>
                        <feMergeNode/>
                        <feMergeNode in="SourceGraphic"/>
                      </feMerge>
                    </filter>
                  </defs>
                  <circle cx="20" cy="20" r="14" fill="#10b981" fill-opacity="0.2" filter="url(#drone-shadow)"/>
                  <circle cx="20" cy="20" r="10" fill="#10b981" filter="url(#drone-shadow)"/>
                  <circle cx="20" cy="20" r="6" fill="#fff"/>
                  <path d="M 20 14 L 20 8 M 20 26 L 20 32 M 14 20 L 8 20 M 26 20 L 32 20" stroke="#10b981" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </div>
            </div>
          `,
          offset: new window.AMap.Pixel(-20, -20),
          zIndex: 101
        });
        this.map.add(this.droneMarker);
        this.$message.success('🛫 AI巡检无人机已启动');
      } else {
        // 移除无人机
        if (this.dronePathLine) {
          this.map.remove(this.dronePathLine);
          this.dronePathLine = null;
        }
        if (this.droneMarker) {
          this.map.remove(this.droneMarker);
          this.droneMarker = null;
        }
        this.$message.info('巡检图层已关闭');
      }
    },
    autoLocate() {
      if (!this.map) {
        this.$message.warning('地图未初始化');
        return;
      }

      // 显示加载提示
      const loading = this.$loading({
        lock: true,
        text: '正在获取您的位置...',
        spinner: 'el-icon-loading',
        background: 'rgba(0, 0, 0, 0.7)'
      });

      this.map.plugin('AMap.Geolocation', () => {
        const geolocation = new window.AMap.Geolocation({
          enableHighAccuracy: true,  // 启用高精度定位
          timeout: 10000,            // 超时时间10秒
          zoomToAccuracy: true,      // 自动调整地图视野到定位点
          showButton: false,         // 不显示定位按钮
          showMarker: true,          // 显示定位标记
          showCircle: true,          // 显示定位精度圈
          panToLocation: true,       // 定位成功后将定位点移动到地图中心
          extensions: 'all'          // 返回详细信息
        });

        geolocation.getCurrentPosition((status, result) => {
          loading.close();
          
          if (status === 'complete') {
            // 定位成功
            const position = result.position;
            const accuracy = result.accuracy;

            // 移动地图到当前位置
            this.map.setZoomAndCenter(16, [position.lng, position.lat]);
            
            // 添加自定义标记
            if (this.userMarker) {
              this.map.remove(this.userMarker);
            }
            
            this.userMarker = new window.AMap.Marker({
              position: [position.lng, position.lat],
              icon: new window.AMap.Icon({
                size: new window.AMap.Size(40, 40),
                image: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHZpZXdCb3g9IjAgMCA0MCA0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48Y2lyY2xlIGN4PSIyMCIgY3k9IjIwIiByPSIxOCIgZmlsbD0iIzQwOTBGRiIgZmlsbC1vcGFjaXR5PSIwLjMiLz48Y2lyY2xlIGN4PSIyMCIgY3k9IjIwIiByPSIxMiIgZmlsbD0iIzQwOTBGRiIgZmlsbC1vcGFjaXR5PSIwLjYiLz48Y2lyY2xlIGN4PSIyMCIgY3k9IjIwIiByPSI2IiBmaWxsPSIjRkZGRkZGIi8+PGNpcmNsZSBjeD0iMjAiIGN5PSIyMCIgcj0iMyIgZmlsbD0iIzQwOTBGRiIvPjwvc3ZnPg==',
                imageSize: new window.AMap.Size(40, 40)
              }),
              title: '我的位置',
              offset: new window.AMap.Pixel(-20, -20),
              animation: 'AMAP_ANIMATION_DROP'
            });
            
            this.map.add(this.userMarker);
            
            // 显示成功消息
            this.$message.success({
              message: `定位成功！精度: ${Math.round(accuracy)}米`,
              duration: 3000
            });
            
            // 如果有地址信息，显示在控制台
            if (result.formattedAddress) {
            }
          } else {
            // 定位失败
            console.error('定位失败:', result);
            
            let errorMsg = '定位失败';
            if (result.message) {
              errorMsg += ': ' + result.message;
            }
            
            this.$message.error({
              message: errorMsg,
              duration: 3000
            });
            
            // 提供备选方案
            this.$confirm('定位失败，是否使用浏览器定位？', '提示', {
              confirmButtonText: '使用浏览器定位',
              cancelButtonText: '取消',
              type: 'warning'
            }).then(() => {
              this.useBrowserGeolocation();
            }).catch(err => { console.error('Browser geolocation prompt dismissed:', err) });
          }
        });
      });
    },

    // 使用浏览器原生定位API
    useBrowserGeolocation() {
      if (!navigator.geolocation) {
        this.$message.error('您的浏览器不支持定位功能');
        return;
      }

      const loading = this.$loading({
        lock: true,
        text: '正在使用浏览器定位...',
        spinner: 'el-icon-loading',
        background: 'rgba(0, 0, 0, 0.7)'
      });

      navigator.geolocation.getCurrentPosition(
        (position) => {
          loading.close();
          const lng = position.coords.longitude;
          const lat = position.coords.latitude;
          const accuracy = position.coords.accuracy;


          // 移动地图到当前位置
          this.map.setZoomAndCenter(16, [lng, lat]);

          // 添加标记
          if (this.userMarker) {
            this.map.remove(this.userMarker);
          }

          this.userMarker = new window.AMap.Marker({
            position: [lng, lat],
            icon: new window.AMap.Icon({
              size: new window.AMap.Size(40, 40),
              image: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHZpZXdCb3g9IjAgMCA0MCA0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48Y2lyY2xlIGN4PSIyMCIgY3k9IjIwIiByPSIxOCIgZmlsbD0iIzQwOTBGRiIgZmlsbC1vcGFjaXR5PSIwLjMiLz48Y2lyY2xlIGN4PSIyMCIgY3k9IjIwIiByPSIxMiIgZmlsbD0iIzQwOTBGRiIgZmlsbC1vcGFjaXR5PSIwLjYiLz48Y2lyY2xlIGN4PSIyMCIgY3k9IjIwIiByPSI2IiBmaWxsPSIjRkZGRkZGIi8+PGNpcmNsZSBjeD0iMjAiIGN5PSIyMCIgcj0iMyIgZmlsbD0iIzQwOTBGRiIvPjwvc3ZnPg==',
              imageSize: new window.AMap.Size(40, 40)
            }),
            title: '我的位置',
            offset: new window.AMap.Pixel(-20, -20),
            animation: 'AMAP_ANIMATION_DROP'
          });

          this.map.add(this.userMarker);

          this.$message.success({
            message: `定位成功！精度: ${Math.round(accuracy)}米`,
            duration: 3000
          });
        },
        (error) => {
          loading.close();
          console.error('浏览器定位失败:', error);
          
          let errorMsg = '浏览器定位失败';
          switch (error.code) {
            case error.PERMISSION_DENIED:
              errorMsg = '您拒绝了定位权限请求';
              break;
            case error.POSITION_UNAVAILABLE:
              errorMsg = '位置信息不可用';
              break;
            case error.TIMEOUT:
              errorMsg = '定位请求超时';
              break;
          }
          
          this.$message.error(errorMsg);
        },
        {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 0
        }
      );
    }
  }
}
</script>

<style scoped>
/* 🌱 生态农业示范基地 - 清新明亮风格 */
.gis-cockpit {
  position: relative;
  width: 100%;
  height: calc(100vh - 60px);
  background: #f9fafb;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 全屏地图 */
.fullscreen-map {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

/* 左侧面板 - 白色卡片 */
.hud-left-panel {
  position: absolute;
  top: 20px;
  left: 20px;
  width: 320px;
  max-height: calc(100vh - 140px);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  z-index: 100;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.hud-header {
  padding: 20px;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hud-title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #059669;
  font-size: 16px;
  font-weight: 700;
}

.hud-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  color: #10b981;
  font-size: 11px;
  font-weight: 600;
}

.hud-search {
  padding: 15px 20px;
  border-bottom: 1px solid #f3f4f6;
}

.hud-input {
  width: 100%;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 10px 15px;
  color: #374151;
  font-size: 13px;
  outline: none;
  transition: all 0.3s;
}

.hud-input:focus {
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

.hud-input::placeholder {
  color: #9ca3af;
}

/* 欢迎横幅 */
.welcome-banner {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 10px;
}

.welcome-icon {
  font-size: 20px;
}

.welcome-text {
  color: #059669;
  font-size: 14px;
  font-weight: 600;
}

/* 顶部数据栏 */
.data-metrics {
  position: absolute;
  top: 70px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 15px;
  z-index: 100;
}

.metric-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px 24px;
  min-width: 130px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.metric-label {
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 8px;
}

.metric-value {
  color: #059669;
  font-size: 28px;
  font-weight: 700;
}

.metric-value.alert {
  color: #f59e0b;
  animation: gentlePulse 3s infinite;
}

@keyframes gentlePulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* 图层控制器 - 圆润胶囊按钮 */
.layer-controller {
  position: absolute;
  bottom: 30px;
  right: 30px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  z-index: 100;
}

.layer-pill {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border: 2px solid #e5e7eb;
  border-radius: 24px;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  user-select: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.layer-pill:hover {
  border-color: #10b981;
  background: #f0fdf4;
  transform: translateX(-5px);
}

.layer-pill.active {
  border-color: #10b981;
  color: #fff;
  background: #10b981;
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.3);
}

.layer-pill i {
  font-size: 18px;
}

/* 右侧详情卡片 - 白色卡片 */
.hud-detail-card {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 380px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  z-index: 100;
  overflow: hidden;
}

.detail-header {
  padding: 20px;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.dh-title {
  color: #059669;
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 6px;
}

.dh-address {
  color: #6b7280;
  font-size: 12px;
}

.detail-body {
  padding: 20px;
  color: #374151;
}

/* ========== 工业级视觉特效 ========== */

/* 阀门节点 - 呼吸脉冲 */
.valve-node {
  position: relative;
  width: 12px;
  height: 12px;
  background: #38bdf8;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 8px rgba(56, 189, 248, 0.6),
              0 0 16px rgba(56, 189, 248, 0.3);
  animation: valvePulse 2s ease-in-out infinite;
}

.valve-pulse {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 100%;
  height: 100%;
  background: rgba(56, 189, 248, 0.4);
  border-radius: 50%;
  animation: pulseRing 2s ease-out infinite;
}

@keyframes valvePulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 0 8px rgba(56, 189, 248, 0.6),
                0 0 16px rgba(56, 189, 248, 0.3);
  }
  50% {
    transform: scale(1.2);
    box-shadow: 0 0 12px rgba(56, 189, 248, 0.8),
                0 0 24px rgba(56, 189, 248, 0.4);
  }
}

@keyframes pulseRing {
  0% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.6;
  }
  100% {
    transform: translate(-50%, -50%) scale(3);
    opacity: 0;
  }
}

/* 无人机容器 */
.drone-container {
  position: relative;
  width: 40px;
  height: 40px;
}

/* 地面投影 - 樭圆阴影 */
.drone-shadow {
  position: absolute;
  bottom: -30px;
  left: 50%;
  transform: translateX(-50%);
  width: 30px;
  height: 12px;
  background: radial-gradient(ellipse, rgba(0, 0, 0, 0.25) 0%, transparent 70%);
  border-radius: 50%;
  animation: shadowPulse 3s ease-in-out infinite;
}

@keyframes shadowPulse {
  0%, 100% {
    width: 30px;
    opacity: 0.25;
  }
  50% {
    width: 36px;
    opacity: 0.35;
  }
}

/* 雷达扫描光波 - 扫形扫描 */
.radar-scan {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 80px;
  height: 80px;
  transform: translate(-50%, -50%);
  background: conic-gradient(
    from 0deg,
    transparent 0deg,
    transparent 270deg,
    rgba(16, 185, 129, 0.15) 270deg,
    rgba(16, 185, 129, 0.3) 315deg,
    rgba(16, 185, 129, 0.5) 360deg
  );
  border-radius: 50%;
  animation: radarScan 3s linear infinite;
  pointer-events: none;
}

@keyframes radarScan {
  0% {
    transform: translate(-50%, -50%) rotate(0deg);
  }
  100% {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

/* 无人机本体 - 悄悬浮 */
.drone-body {
  position: relative;
  z-index: 2;
  animation: droneHover 3s ease-in-out infinite;
}

@keyframes droneHover {
  0%, 100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-4px);
  }
}

/* 管网流动动画（全局样式，需要通过JS动态添加） */
@keyframes flowDash {
  0% {
    stroke-dashoffset: 0;
  }
  100% {
    stroke-dashoffset: 40;
  }
}

.brand { display: flex; align-items: center; gap: 10px; }
.brand .title { font-size: 18px; font-weight: 600; color: #333; letter-spacing: 1px; }

.data-pills { display: flex; gap: 16px; }
.pill {
  background: #f5f7fa; padding: 6px 16px; border-radius: 20px;
  display: flex; align-items: center; gap: 8px;
  border: 1px solid #eee;
}
.pill .label { font-size: 12px; color: #666; }
.pill .val { font-size: 16px; font-weight: bold; color: #333; }
.pill.warning { background: #fef0f0; border-color: #fde2e2; }
.pill.warning .val { color: #F56C6C; }

.view-control { display: flex; align-items: center; width: 200px; gap: 10px; }
.control-label { font-size: 12px; color: #999; white-space: nowrap; }
.pitch-slider { flex: 1; }

/* 2. 左侧边栏面板 */
.left-sidebar-panel {
  width: 260px;
  min-width: 260px;
  height: 100%;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 20px;
  background: white;
  border-bottom: 1px solid #e9ecef;
}

.sidebar-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.search-wrapper {
  padding: 15px 20px;
  background: white;
  border-bottom: 1px solid #e9ecef;
}

.search-input {
  width: 100%;
}

:deep(.search-input .el-input__inner ){
  border-radius: 6px;
  background: #f8f9fa;
  border: none;
  box-shadow: none;
}

:deep(.search-input .el-input__wrapper ){
  border: 1px solid #e9ecef;
  border-radius: 6px;
  background: #f8f9fa;
  box-shadow: none;
}

.region-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 0;
  scrollbar-width: none;
}

.region-list::-webkit-scrollbar { 
  display: none; 
}

/* 区域分组样式 */
.region-section {
  margin-bottom: 15px;
}

.region-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: white;
  cursor: pointer;
  transition: all 0.2s;
  border-bottom: 1px solid #f0f0f0;
}

.region-header:hover {
  background: #f8f9fa;
}

.expand-icon {
  color: #999;
  margin-right: 8px;
  font-size: 12px;
  transition: transform 0.2s;
}

.region-icon {
  color: #666;
  margin-right: 8px;
  font-size: 14px;
}

.region-name {
  flex: 1;
  font-weight: 600;
  color: #333;
  font-size: 14px;
}

.region-count {
  color: #999;
  font-size: 12px;
}

.farm-list {
  background: #f8f9fa;
}

.farm-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: white;
  margin: 1px 0;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.farm-item:hover {
  background: #f0f9eb;
  border-left-color: #4CAF50;
}

.farm-item.active {
  background: #e8f5e9;
  border-left-color: #4CAF50;
}

.farm-icon {
  width: 32px;
  height: 32px;
  background: #e8f5e9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.crop-initial {
  color: #4CAF50;
  font-weight: bold;
  font-size: 14px;
}

.farm-info {
  flex: 1;
}

.farm-name {
  font-weight: 600;
  color: #333;
  font-size: 14px;
  margin-bottom: 4px;
}

.farm-details {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.area {
  color: #666;
}

.crop {
  color: #999;
}

.status {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.status-success {
  background: #f0f9eb;
  color: #67C23A;
}

.status-warning {
  background: #fdf6ec;
  color: #E6A23C;
}

.status-danger {
  background: #fef0f0;
  color: #F56C6C;
}

.card-img-placeholder {
  width: 48px; height: 48px; background: #e8f5e9; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #4CAF50; font-weight: bold; font-size: 18px; margin-right: 12px;
}
.card-info { flex: 1; }
.card-info .name { font-weight: 600; color: #333; font-size: 15px; margin-bottom: 4px; }
.card-info .meta { font-size: 12px; color: #999; display: flex; align-items: center; gap: 5px; }
.card-info .divider { color: #ddd; }
.arrow-icon { color: #ccc; font-size: 12px; }

/* 3. 右侧详情卡片 */
.right-detail-card {
  position: absolute; top: 100px; right: 30px;
  width: 320px; background: white; border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.1);
  z-index: 95; overflow: hidden;
}
.detail-header {
  padding: 20px; border-bottom: 1px solid #f5f5f5;
  display: flex; justify-content: space-between; align-items: flex-start;
}
.dh-title { font-size: 18px; font-weight: bold; color: #333; margin-bottom: 8px; }
.dh-address { 
  font-size: 14px; 
  color: #4CAF50; 
  font-weight: 500;
  background: linear-gradient(135deg, #e8f5e9 0%, #f1f8f4 100%);
  padding: 6px 12px;
  border-radius: 6px;
  border-left: 3px solid #4CAF50;
  display: inline-flex;
  align-items: center;
}

.detail-body { padding: 20px; }
.kpi-row { display: flex; justify-content: space-between; text-align: center; }
.k-val { font-size: 18px; font-weight: bold; color: #333; }
.k-label { font-size: 12px; color: #999; margin-top: 4px; }

.env-list { margin-top: 10px; margin-bottom: 20px; }
.env-item { display: flex; align-items: center; margin-bottom: 15px; }
.env-icon {
  width: 36px; height: 36px; background: #f5f7fa; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; color: #666; margin-right: 12px;
}
.env-info { flex: 1; margin-right: 12px; }
.env-name { font-size: 12px; color: #666; margin-bottom: 4px; }
.env-val { font-size: 13px; font-weight: bold; color: #333; }
.env-val.warning { color: #F56C6C; }

/* 地图工具 */
.map-tools {
  position: absolute; bottom: 30px; left: 30px;
  display: flex; flex-direction: column; gap: 12px;
  z-index: 100;
}

/* 错误提示 */
.map-error-overlay {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  background: #f0f2f5;
  display: flex; align-items: center; justify-content: center;
  z-index: 50;
}
.error-content { text-align: center; color: #909399; }
.error-content i { font-size: 48px; margin-bottom: 16px; color: #F56C6C; }
.error-content p { margin-bottom: 20px; font-size: 16px; }
.error-detail {
  max-width: 420px;
  margin: 0 auto 20px;
  font-size: 13px !important;
  line-height: 1.6;
  color: #6b7280;
}

.tool-btn {
  width: 44px; height: 44px; background: white; border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all 0.2s; color: #666; font-size: 18px;
}
.tool-btn:hover { background: #4CAF50; color: white; transform: scale(1.1); }

/* 地图标签 */
:deep(.map-tag ){
  background: white; padding: 4px 10px; border-radius: 4px;
  font-size: 12px; font-weight: bold;
  box-shadow: 0 2px 6px rgba(0,0,0,0.2); border: 2px solid;
  white-space: nowrap;
}
</style>

