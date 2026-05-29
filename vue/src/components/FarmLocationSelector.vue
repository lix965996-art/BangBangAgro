<template>
  <el-dialog
    title="地块位置标记"
    v-model="dialogVisible"
    @opened="initMap"
    width="70%"
    top="5vh"
    :close-on-click-modal="false"
    append-to-body
  >
    <div class="location-selector">
      <div class="toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索地址或地点"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" icon="el-icon-search" @click="handleSearch">搜索</el-button>
        <el-button type="success" icon="el-icon-edit-outline" :disabled="isDrawing" @click="startDraw">
          {{ isDrawing ? '绘制中...' : '绘制区域' }}
        </el-button>
        <el-button
          v-if="isDrawing"
          type="info"
          plain
          icon="el-icon-refresh-left"
          @click="undoLastPoint"
        >
          撤销上一步
        </el-button>
        <el-button
          v-if="isDrawing"
          type="primary"
          plain
          icon="el-icon-check"
          @click="finishDrawing"
        >
          完成绘制
        </el-button>
        <el-button type="warning" icon="el-icon-delete" @click="clearDraw">清除</el-button>
      </div>

      <div id="selector-container" class="map-container"></div>

      <div class="status-panel">
        <div class="status-line">
          <span class="label">地址</span>
          <span class="value">{{ selectedLocation.address || '点击地图选择位置，或搜索地址' }}</span>
        </div>
        <div class="status-line">
          <span class="label">所属区县</span>
          <span class="value">{{ calculatedDistrict || '-' }}</span>
        </div>
        <div class="status-line">
          <span class="label">面积</span>
          <span class="value">{{ calculatedArea ? `${calculatedArea} 亩` : '-' }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSelection">确认</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script>
import mapConfig from '@/config/map.config.js'
import { loadAmapSdk, resetAmapLoader } from '@/utils/amapLoader'

const LEAFLET_JS_CDN = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
const LEAFLET_CSS_CDN = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'

export default {
  name: 'FarmLocationSelector',
  props: {
    visible: { type: Boolean, default: false },
    initialData: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      map: null,
      geocoder: null,
      placeSearch: null,
      mouseTool: null,
      marker: null,
      overlay: null,
      searchKeyword: '',
      isDrawing: false,
      isLoadingMap: false,
      useFallbackWithoutSecurityCode: false,
      fallbackMode: false,
      providerMode: 'auto',
      leafletMap: null,
      leafletMarker: null,
      leafletPolygon: null,
      leafletCurrentPathLayer: null,
      activeTileLayer: null,
      activeTileSourceIndex: -1,
      currentPath: [],
      calculatedArea: 0,
      calculatedDistrict: '',
      selectedLocation: {
        lng: null,
        lat: null,
        address: ''
      }
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit('update:visible', value)
      }
    }
  },
  watch: {
    visible(val) {
      console.log('[FarmLocationSelector] visible 变化为:', val)
    }
  },
  beforeUnmount() {
    this.destroyMap()
  },
  methods: {
    resolveProviderMode() {
      const envMode = (import.meta.env.VUE_APP_MAP_PROVIDER || '').toLowerCase()
      const forceLeaflet = import.meta.env.VUE_APP_FORCE_LEAFLET === 'true'
      if (forceLeaflet) {
        return 'leaflet'
      }
      if (['amap', 'leaflet', 'auto'].includes(envMode)) {
        return envMode
      }
      return mapConfig.provider && mapConfig.provider.mode ? mapConfig.provider.mode : 'auto'
    },
    async initMap() {
      console.log('[FarmLocationSelector] initMap 被调用, window.AMap:', !!window.AMap)
      this.providerMode = this.resolveProviderMode()
      if (this.providerMode === 'leaflet') {
        console.log('[FarmLocationSelector] 当前配置为 Leaflet 兼容模式')
        await this.initLeafletMap()
        return
      }
      try {
        this.fallbackMode = false
        await this.ensureMapSdk()
        console.log('[FarmLocationSelector] SDK 加载完成，开始创建地图')
        this.createMap()
      } catch (error) {
        console.error('[FarmLocationSelector] 地图初始化失败:', error)
        if (this.providerMode === 'amap') {
          this.$message.error('高德地图初始化失败，请检查 key 与域名白名单')
          return
        }
        this.$message.warning('高德地图不可用，已切换兼容地图模式')
        await this.initLeafletMap()
      }
    },
    mountLeafletTileLayer(index = 0) {
      const tileSources = (mapConfig.leaflet && Array.isArray(mapConfig.leaflet.tileSources))
        ? mapConfig.leaflet.tileSources
        : []
      if (!this.leafletMap || !window.L || !tileSources.length || index >= tileSources.length) {
        this.$message.error('地图瓦片加载失败，请检查网络或更换可用瓦片源')
        return
      }
      const source = tileSources[index]
      if (this.activeTileLayer) {
        this.leafletMap.removeLayer(this.activeTileLayer)
        this.activeTileLayer = null
      }
      this.activeTileSourceIndex = index
      const layer = window.L.tileLayer(source.url, source.options || {})
      let switched = false
      layer.on('tileerror', () => {
        if (switched) return
        switched = true
        const nextIndex = index + 1
        if (nextIndex < tileSources.length) {
          console.warn(`[FarmLocationSelector] 瓦片源失败，切换到: ${tileSources[nextIndex].name}`)
          this.mountLeafletTileLayer(nextIndex)
        } else {
          this.$message.error('地图瓦片不可达，请检查网络/DNS 或配置内网瓦片服务')
        }
      })
      layer.addTo(this.leafletMap)
      this.activeTileLayer = layer
    },
    async ensureLeafletSdk() {
      if (window.L && window.L.map) {
        return window.L
      }

      const existingCss = document.getElementById('leaflet-css')
      if (!existingCss) {
        const link = document.createElement('link')
        link.id = 'leaflet-css'
        link.rel = 'stylesheet'
        link.href = LEAFLET_CSS_CDN
        document.head.appendChild(link)
      }

      await new Promise((resolve, reject) => {
        const existingScript = document.getElementById('leaflet-js')
        if (existingScript) {
          if (window.L && window.L.map) {
            resolve()
          } else {
            existingScript.addEventListener('load', resolve, { once: true })
            existingScript.addEventListener('error', reject, { once: true })
          }
          return
        }
        const script = document.createElement('script')
        script.id = 'leaflet-js'
        script.src = LEAFLET_JS_CDN
        script.async = true
        script.addEventListener('load', resolve, { once: true })
        script.addEventListener('error', reject, { once: true })
        document.head.appendChild(script)
      })

      if (!window.L || !window.L.map) {
        throw new Error('LEAFLET_SDK_UNAVAILABLE')
      }
      return window.L
    },
    async initLeafletMap() {
      this.fallbackMode = true
      await this.ensureLeafletSdk()
      this.$nextTick(() => {
        const container = document.getElementById('selector-container')
        if (!container || !window.L) {
          return
        }
        this.destroyMap()
        this.fallbackMode = true
        const center = mapConfig.amap.defaultCenter || [110.479, 29.117]
        this.leafletMap = window.L.map('selector-container', {
          zoomControl: true,
          doubleClickZoom: false
        }).setView([center[1], center[0]], mapConfig.amap.defaultZoom || 14)

        this.mountLeafletTileLayer(0)

        this.leafletMap.on('click', (event) => {
          if (this.isDrawing) {
            const { lat, lng } = event.latlng
            this.currentPath.push({ lng, lat })
            this.drawLeafletDraft()
            this.calculatedArea = Number(this.calculatePolygonAreaInMu(this.currentPath).toFixed(1))
            return
          }
          this.updateLocation(event.latlng.lng, event.latlng.lat)
        })

        this.leafletMap.on('dblclick', (event) => {
          if (!this.isDrawing) {
            return
          }
          event.originalEvent && event.originalEvent.preventDefault()
          this.finishLeafletDrawing()
        })

        this.hydrateFromInitialData()
      })
    },
    drawLeafletDraft() {
      if (!this.leafletMap || !window.L) {
        return
      }
      const latLngs = this.currentPath.map(point => [point.lat, point.lng])
      if (this.leafletCurrentPathLayer) {
        this.leafletMap.removeLayer(this.leafletCurrentPathLayer)
      }
      if (this.leafletPolygon) {
        this.leafletMap.removeLayer(this.leafletPolygon)
        this.leafletPolygon = null
      }
      if (latLngs.length >= 3) {
        this.leafletCurrentPathLayer = window.L.polygon(latLngs, {
          color: '#10B981',
          weight: 3,
          fillColor: '#10B981',
          fillOpacity: 0.2
        }).addTo(this.leafletMap)
      } else {
        this.leafletCurrentPathLayer = window.L.polyline(latLngs, {
          color: '#10B981',
          weight: 3
        }).addTo(this.leafletMap)
      }
    },
    finishLeafletDrawing() {
      if (!this.currentPath.length) {
        return
      }
      if (this.currentPath.length < 3) {
        this.$message.warning('至少绘制 3 个点才能形成地块区域')
        return
      }
      this.isDrawing = false
      if (this.leafletCurrentPathLayer) {
        this.leafletMap.removeLayer(this.leafletCurrentPathLayer)
        this.leafletCurrentPathLayer = null
      }
      const latLngs = this.currentPath.map(point => [point.lat, point.lng])
      this.leafletPolygon = window.L.polygon(latLngs, {
        color: '#10B981',
        weight: 3,
        fillColor: '#10B981',
        fillOpacity: 0.2
      }).addTo(this.leafletMap)
      this.leafletMap.fitBounds(this.leafletPolygon.getBounds())

      this.calculatedArea = Number((this.calculatePolygonAreaInMu(this.currentPath)).toFixed(1))
      const center = this.calculatePolygonCenter(this.currentPath)
      this.updateLocation(center.lng, center.lat)
    },
    calculatePolygonCenter(path) {
      if (!Array.isArray(path) || path.length === 0) {
        return { lng: 0, lat: 0 }
      }
      const sum = path.reduce((acc, point) => {
        acc.lng += Number(point.lng) || 0
        acc.lat += Number(point.lat) || 0
        return acc
      }, { lng: 0, lat: 0 })
      return {
        lng: sum.lng / path.length,
        lat: sum.lat / path.length
      }
    },
    calculatePolygonAreaInMu(path) {
      if (!Array.isArray(path) || path.length < 3) {
        return 0
      }
      const meanLat = path.reduce((sum, p) => sum + Number(p.lat || 0), 0) / path.length
      const meterPerDegLng = 111320 * Math.cos(meanLat * Math.PI / 180)
      const meterPerDegLat = 110540
      const points = path.map(p => ({
        x: Number(p.lng || 0) * meterPerDegLng,
        y: Number(p.lat || 0) * meterPerDegLat
      }))
      let area = 0
      for (let i = 0; i < points.length; i += 1) {
        const j = (i + 1) % points.length
        area += points[i].x * points[j].y - points[j].x * points[i].y
      }
      const areaSquareMeters = Math.abs(area) / 2
      return areaSquareMeters / 666.67
    },
    async ensureMapSdk() {
      if (this.isLoadingMap) {
        console.log('[FarmLocationSelector] SDK 正在加载中，跳过重复请求')
        return
      }

      this.isLoadingMap = true
      console.log('[FarmLocationSelector] 开始加载 AMap SDK...')
      try {
        await loadAmapSdk({
          forceNoSecurity: this.useFallbackWithoutSecurityCode,
          plugins: ['AMap.ToolBar', 'AMap.PlaceSearch', 'AMap.Geocoder', 'AMap.MouseTool', 'AMap.GeometryUtil']
        })
        console.log('[FarmLocationSelector] AMap SDK 加载成功')
      } catch (error) {
        console.error('[FarmLocationSelector] AMap SDK 加载失败:', error)
        this.$message.error('高德地图加载失败，请检查网络或 Key 配置')
        throw error
      } finally {
        this.isLoadingMap = false
      }
    },
    createMap() {
      console.log('[FarmLocationSelector] createMap 被调用')
      this.$nextTick(() => {
        const container = document.getElementById('selector-container')
        console.log('[FarmLocationSelector] 容器元素:', !!container, 'AMap:', !!window.AMap)
        if (!container || !window.AMap) {
          console.warn('[FarmLocationSelector] 无法创建地图 - 容器或AMap不存在')
          return
        }

        this.destroyMap()

        this.map = new window.AMap.Map('selector-container', {
          zoom: mapConfig.amap.defaultZoom,
          center: mapConfig.amap.defaultCenter,
          viewMode: '2D',
          resizeEnable: true
        })

        this.map.addControl(new window.AMap.ToolBar())

        console.log('[FarmLocationSelector] 插件可用性 - Geocoder:', !!window.AMap.Geocoder, 'PlaceSearch:', !!window.AMap.PlaceSearch, 'MouseTool:', !!window.AMap.MouseTool)

        if (window.AMap.Geocoder) {
          this.geocoder = new window.AMap.Geocoder({ radius: 1000, extensions: 'all' })
        } else {
          console.warn('[FarmLocationSelector] Geocoder 插件不可用')
        }

        if (window.AMap.PlaceSearch) {
          this.placeSearch = new window.AMap.PlaceSearch({
            map: this.map,
            autoFitView: true,
            city: '430800'
          })
        } else {
          console.warn('[FarmLocationSelector] PlaceSearch 插件不可用')
        }

        if (window.AMap.MouseTool) {
          this.mouseTool = new window.AMap.MouseTool(this.map)
          this.mouseTool.on('draw', (event) => {
            this.isDrawing = false
            if (this.overlay) {
              this.map.remove(this.overlay)
            }
            this.overlay = event.obj

            const path = event.obj.getPath()
            this.currentPath = path.map(point => ({ lng: point.lng, lat: point.lat }))

            if (window.AMap.GeometryUtil) {
              const areaInSquareMeters = window.AMap.GeometryUtil.ringArea(path)
              this.calculatedArea = Number((areaInSquareMeters / 666.67).toFixed(1))
            }

            const center = event.obj.getBounds().getCenter()
            this.updateLocation(center.lng, center.lat)
          })
        }

        this.map.on('click', (event) => {
          if (!this.isDrawing) {
            this.updateLocation(event.lnglat.getLng(), event.lnglat.getLat())
          }
        })

        this.hydrateFromInitialData()
      })
    },
    hydrateFromInitialData() {
      if (!this.initialData) {
        return
      }

      if (this.initialData.centerLng && this.initialData.centerLat) {
        this.selectedLocation = {
          lng: this.initialData.centerLng,
          lat: this.initialData.centerLat,
          address: this.initialData.address || ''
        }
        this.calculatedDistrict = this.initialData.district || ''
        this.calculatedArea = Number(this.initialData.area || 0)
        this.setMarker(this.initialData.centerLng, this.initialData.centerLat)
        if (this.fallbackMode && this.leafletMap) {
          this.leafletMap.setView([this.initialData.centerLat, this.initialData.centerLng], 15)
        } else if (this.map) {
          this.map.setCenter([this.initialData.centerLng, this.initialData.centerLat])
        }
      }

      if (this.initialData.coordinates) {
        try {
          const coordinates = typeof this.initialData.coordinates === 'string'
            ? JSON.parse(this.initialData.coordinates)
            : this.initialData.coordinates

          if (Array.isArray(coordinates) && coordinates.length > 2) {
            this.currentPath = coordinates
            if (this.fallbackMode && this.leafletMap && window.L) {
              const latLngs = coordinates.map(item => [item.lat, item.lng])
              this.leafletPolygon = window.L.polygon(latLngs, {
                color: '#10B981',
                weight: 3,
                fillColor: '#10B981',
                fillOpacity: 0.2
              }).addTo(this.leafletMap)
              this.leafletMap.fitBounds(this.leafletPolygon.getBounds())
            } else if (this.map) {
              this.overlay = new window.AMap.Polygon({
                path: coordinates.map(item => [item.lng, item.lat]),
                strokeColor: '#10B981',
                strokeWeight: 3,
                fillColor: '#10B981',
                fillOpacity: 0.2
              })
              this.map.add(this.overlay)
              this.map.setFitView([this.overlay])
            }
          }
        } catch (error) {
          console.error('Failed to parse initial polygon:', error)
        }
      }
    },
    handleSearch() {
      console.log('[FarmLocationSelector] handleSearch 被调用, keyword:', this.searchKeyword)
      console.log('[FarmLocationSelector] placeSearch 对象:', !!this.placeSearch)
      if (!this.searchKeyword) {
        console.warn('[FarmLocationSelector] 搜索条件不满足 - keyword:', !!this.searchKeyword, 'placeSearch:', !!this.placeSearch)
        return
      }

      // PlaceSearch 不可用时，直接降级到后端代理检索
      if (!this.placeSearch) {
        this.searchByBackendProxy()
        return
      }

      this.placeSearch.search(this.searchKeyword, async (status, result) => {
        console.log('[FarmLocationSelector] 搜索结果 - status:', status, 'result:', result)
        const platMismatch = this.isPlatMismatchError(status, result)
        if (platMismatch) {
          this.$message.warning('检测到高德 JS Key 平台不匹配，已切换后端代理检索')
          await this.switchToLeafletMode()
          await this.searchByBackendProxy()
          return
        }

        const invalidScode = this.isInvalidScodeError(status, result)
        if (invalidScode && !this.useFallbackWithoutSecurityCode) {
          this.$message.warning('检测到高德安全码校验失败，正在自动切换兼容模式重试...')
          await this.retrySearchWithoutSecurityCode()
          return
        }

        if (status !== 'complete' || !result.poiList || !result.poiList.pois.length) {
          this.$message.warning('未找到匹配的地址')
          return
        }

        const poi = result.poiList.pois[0]
        if (!poi.location) {
          this.$message.warning('所选地址没有坐标数据')
          return
        }

        console.log('[FarmLocationSelector] 搜索到POI:', poi.name, poi.location)
        this.updateLocation(poi.location.lng, poi.location.lat, poi.name)
        this.map.setZoom(16)
      })
    },
    isPlatMismatchError(status, result) {
      if (status !== 'error') {
        return false
      }
      const text = [
        typeof result === 'string' ? result : '',
        result && result.info ? String(result.info) : '',
        result && result.message ? String(result.message) : '',
        result && result.error ? String(result.error) : ''
      ].join(' ')
      return text.includes('USERKEY_PLAT_NOMATCH')
    },
    isInvalidScodeError(status, result) {
      if (status !== 'error') {
        return false
      }
      const text = [
        typeof result === 'string' ? result : '',
        result && result.info ? String(result.info) : '',
        result && result.message ? String(result.message) : '',
        result && result.error ? String(result.error) : ''
      ].join(' ')
      return text.includes('INVALID_USER_SCODE')
    },
    async retrySearchWithoutSecurityCode() {
      try {
        this.useFallbackWithoutSecurityCode = true
        resetAmapLoader()
        this.destroyMap()
        await this.initMap()
        if (this.placeSearch && this.searchKeyword) {
          this.placeSearch.search(this.searchKeyword, (status, result) => {
            console.log('[FarmLocationSelector] 兼容模式搜索结果 - status:', status, 'result:', result)
            if (status !== 'complete' || !result.poiList || !result.poiList.pois.length) {
              this.$message.warning('检索失败，请检查高德 JS Key 与安全码配置')
              return
            }
            const poi = result.poiList.pois[0]
            if (!poi.location) {
              this.$message.warning('所选地址没有坐标数据')
              return
            }
            this.updateLocation(poi.location.lng, poi.location.lat, poi.name)
            if (this.map) {
              this.map.setZoom(16)
            }
          })
        }
      } catch (error) {
        console.error('[FarmLocationSelector] 兼容模式重试失败:', error)
        this.$message.error('地图检索初始化失败，请检查高德配置')
      }
    },
    async switchToLeafletMode() {
      if (this.fallbackMode && this.leafletMap) {
        return
      }
      this.destroyMap()
      await this.initLeafletMap()
    },
    async searchByBackendProxy() {
      try {
        const res = await this.request.get('/amap/inputtips', {
          params: {
            keywords: this.searchKeyword,
            city: mapConfig.amap.defaultCity || '430800'
          }
        })
        // 该接口返回的是普通对象 {status, info, tips}，不是 Result 包装
        const tips = res && Array.isArray(res.tips) ? res.tips : []
        const firstTip = tips.find(item => item && item.location)
        if (firstTip) {
          const [lngStr, latStr] = String(firstTip.location).split(',')
          const lng = Number(lngStr)
          const lat = Number(latStr)
          if (Number.isFinite(lng) && Number.isFinite(lat)) {
            this.updateLocation(lng, lat, firstTip.name || this.searchKeyword)
            if (this.fallbackMode && this.leafletMap) {
              this.leafletMap.setView([lat, lng], 16)
            } else if (this.map) {
              this.map.setZoom(16)
            }
            return
          }
        }

        // 后端可用但无结果，尝试 OSM 兜底
        await this.searchByNominatim()
      } catch (error) {
        console.warn('[FarmLocationSelector] 后端代理检索失败，切换 OSM:', error)
        await this.searchByNominatim()
      }
    },
    async searchByNominatim() {
      try {
        const query = encodeURIComponent(this.searchKeyword)
        const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=${query}`
        const response = await fetch(url, {
          headers: {
            Accept: 'application/json',
            'User-Agent': 'BangBangAgro/1.0 (agriculture-management-app)'
          }
        })
        if (!response.ok) {
          throw new Error(`Nominatim search failed: ${response.status}`)
        }
        const rows = await response.json()
        if (!Array.isArray(rows) || !rows.length) {
          this.$message.warning('未找到匹配的地址')
          return
        }
        const first = rows[0]
        const lng = Number(first.lon)
        const lat = Number(first.lat)
        if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
          this.$message.warning('检索到的坐标无效，请换个关键词')
          return
        }
        this.updateLocation(lng, lat, first.display_name || this.searchKeyword)
        if (this.fallbackMode && this.leafletMap) {
          this.leafletMap.setView([lat, lng], 16)
        } else if (this.map) {
          this.map.setZoom(16)
        }
      } catch (error) {
        console.error('[FarmLocationSelector] OSM 检索失败:', error)
        this.$message.error('地址搜索失败，请检查网络')
      }
    },
    updateLocation(lng, lat, addressHint = '') {
      this.selectedLocation.lng = lng
      this.selectedLocation.lat = lat
      this.setMarker(lng, lat)
      if (this.fallbackMode && this.leafletMap) {
        this.leafletMap.setView([lat, lng], this.leafletMap.getZoom())
      } else if (this.map) {
        this.map.setCenter([lng, lat])
      }

      if (this.fallbackMode) {
        this.reverseGeocodeByBackend(lng, lat, addressHint)
        return
      }

      if (!this.geocoder) {
        this.selectedLocation.address = addressHint || `${lng.toFixed(6)}, ${lat.toFixed(6)}`
        return
      }

      this.geocoder.getAddress([lng, lat], (status, result) => {
        if (status === 'complete' && result.regeocode) {
          const addressComponent = result.regeocode.addressComponent || {}
          this.selectedLocation.address = result.regeocode.formattedAddress || addressHint
          this.calculatedDistrict = `${addressComponent.city || ''}${addressComponent.district || ''}` || this.calculatedDistrict
        } else {
          this.selectedLocation.address = addressHint || `${lng.toFixed(6)}, ${lat.toFixed(6)}`
        }
      })
    },
    async reverseGeocodeByBackend(lng, lat, addressHint = '') {
      try {
        const location = `${lng},${lat}`
        const res = await this.request.get('/amap/regeocode', {
          params: { location, extensions: 'all' }
        })
        if (res && res.status === '1' && res.regeocode) {
          const addressComponent = res.regeocode.addressComponent || {}
          this.selectedLocation.address = res.regeocode.formatted_address || addressHint || `${lng.toFixed(6)}, ${lat.toFixed(6)}`
          this.calculatedDistrict = `${addressComponent.city || ''}${addressComponent.district || ''}` || this.calculatedDistrict
          return
        }
      } catch (error) {
        console.warn('[FarmLocationSelector] 后端逆地理失败，切换 OSM', error)
      }

      try {
        const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lng)}`
        const response = await fetch(url, {
          headers: {
            Accept: 'application/json',
            'User-Agent': 'BangBangAgro/1.0 (agriculture-management-app)'
          }
        })
        if (response.ok) {
          const data = await response.json()
          if (data) {
            this.selectedLocation.address = data.display_name || addressHint || `${lng.toFixed(6)}, ${lat.toFixed(6)}`
            const addr = data.address || {}
            const city = addr.city || addr.town || addr.county || ''
            const district = addr.city_district || addr.suburb || addr.county || ''
            this.calculatedDistrict = `${city}${district}` || this.calculatedDistrict
            return
          }
        }
      } catch (error) {
        console.warn('[FarmLocationSelector] OSM 逆地理失败，使用坐标兜底', error)
      }
      this.selectedLocation.address = addressHint || `${lng.toFixed(6)}, ${lat.toFixed(6)}`
    },
    setMarker(lng, lat) {
      if (this.fallbackMode) {
        if (!this.leafletMap || !window.L) {
          return
        }
        if (this.leafletMarker) {
          this.leafletMap.removeLayer(this.leafletMarker)
        }
        this.leafletMarker = window.L.marker([lat, lng]).addTo(this.leafletMap)
        return
      }

      if (!this.map) {
        return
      }

      if (this.marker) {
        this.map.remove(this.marker)
      }

      this.marker = new window.AMap.Marker({
        position: [lng, lat]
      })
      this.map.add(this.marker)
    },
    startDraw() {
      if (this.fallbackMode) {
        if (!this.leafletMap) {
          this.$message.warning('地图未准备好')
          return
        }
        this.isDrawing = true
        this.currentPath = []
        this.calculatedArea = 0
        if (this.leafletPolygon) {
          this.leafletMap.removeLayer(this.leafletPolygon)
          this.leafletPolygon = null
        }
        if (this.leafletCurrentPathLayer) {
          this.leafletMap.removeLayer(this.leafletCurrentPathLayer)
          this.leafletCurrentPathLayer = null
        }
        this.$message.info('请在地图上连续点击绘制顶点，双击完成绘制')
        return
      }

      if (!this.mouseTool) {
        this.$message.warning('绘图工具不可用')
        return
      }

      this.isDrawing = true
      this.setDrawingCursor(true)
      if (this.overlay) {
        this.map.remove(this.overlay)
        this.overlay = null
      }
      this.currentPath = []
      this.calculatedArea = 0
      this.mouseTool.polygon({
        strokeColor: '#10B981',
        strokeWeight: 3,
        fillColor: '#10B981',
        fillOpacity: 0.2
      })
    },
    undoLastPoint() {
      if (!this.isDrawing || !this.currentPath.length) {
        return
      }
      this.currentPath.pop()
      if (this.fallbackMode) {
        this.drawLeafletDraft()
        this.calculatedArea = Number(this.calculatePolygonAreaInMu(this.currentPath).toFixed(1))
      }
    },
    finishDrawing() {
      if (!this.isDrawing) {
        return
      }
      if (this.fallbackMode) {
        this.finishLeafletDrawing()
        return
      }
      // AMap 的 MouseTool 通过 close 停止绘制，最终会触发 draw 回调
      if (this.mouseTool) {
        this.mouseTool.close(false)
      }
      this.isDrawing = false
      this.setDrawingCursor(false)
      if (!this.currentPath.length) {
        this.$message.info('请先在地图上点击至少 3 个点形成区域')
      }
    },
    setDrawingCursor(enabled) {
      const container = document.getElementById('selector-container')
      if (!container) return
      container.style.cursor = enabled ? 'crosshair' : 'grab'
    },
    clearDraw() {
      if (this.fallbackMode) {
        if (this.leafletPolygon && this.leafletMap) {
          this.leafletMap.removeLayer(this.leafletPolygon)
          this.leafletPolygon = null
        }
        if (this.leafletCurrentPathLayer && this.leafletMap) {
          this.leafletMap.removeLayer(this.leafletCurrentPathLayer)
          this.leafletCurrentPathLayer = null
        }
        this.currentPath = []
        this.calculatedArea = 0
        this.isDrawing = false
        this.setDrawingCursor(false)
        return
      }

      if (this.overlay) {
        this.map.remove(this.overlay)
        this.overlay = null
      }
      if (this.mouseTool) {
        this.mouseTool.close(true)
      }
      this.currentPath = []
      this.calculatedArea = 0
      this.isDrawing = false
      this.setDrawingCursor(false)
    },
    confirmSelection() {
      if (!this.selectedLocation.lng || !this.selectedLocation.lat) {
        this.$message.warning('请先选择位置')
        return
      }
      if (!this.currentPath.length) {
        this.$message.warning('请先绘制地块区域后再确认')
        return
      }

      this.$emit('confirm', {
        centerLng: this.selectedLocation.lng,
        centerLat: this.selectedLocation.lat,
        address: this.selectedLocation.address,
        district: this.calculatedDistrict,
        area: this.calculatedArea || this.initialData.area || 0,
        coordinates: this.currentPath.length ? JSON.stringify(this.currentPath) : this.initialData.coordinates || ''
      })
      this.$emit('update:visible', false)
    },
    destroyMap() {
      if (this.map) {
        this.map.destroy()
        this.map = null
      }
      if (this.leafletMap) {
        this.leafletMap.remove()
        this.leafletMap = null
      }
      this.marker = null
      this.overlay = null
      this.mouseTool = null
      this.leafletMarker = null
      this.leafletPolygon = null
      this.leafletCurrentPathLayer = null
      this.activeTileLayer = null
      this.activeTileSourceIndex = -1
      this.setDrawingCursor(false)
    }
  }
}
</script>

<style scoped>
.location-selector {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: grid;
  grid-template-columns: 1fr auto auto auto auto auto;
  gap: 12px;
}

.map-container {
  width: 100%;
  height: 460px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  background: #f5f7fa;
}

.status-panel {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
}

.status-line {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 12px;
  font-size: 14px;
}

.label {
  color: #606266;
  font-weight: 600;
}

.value {
  color: #303133;
  word-break: break-all;
}

@media (max-width: 900px) {
  .toolbar {
    grid-template-columns: 1fr 1fr 1fr;
  }

  .map-container {
    height: 360px;
  }
}
</style>
