/**
 * 高德地图配置文件
 * 统一管理地图相关配置，避免硬编码
 */

const mapConfig = {
  // 高德地图API配置
  amap: {
    // JavaScript API Key（请通过 .env 的 VUE_APP_AMAP_JS_KEY 配置）
    jsKey: '',

    // 高德地图安全码（请通过 .env 的 VUE_APP_AMAP_SECURITY_CODE 配置）
    securityCode: '',
    
    // API版本
    version: '2.0',
    
    // 需要加载的插件
    plugins: [
      'AMap.AutoComplete',
      'AMap.PlaceSearch',
      'AMap.Geocoder',
      'AMap.ToolBar',
      'AMap.MouseTool',
      'AMap.GeometryUtil'
    ],
    
    // 默认城市（张家界）
    defaultCity: '430800',
    
    // 默认中心点坐标（张家界市中心）
    defaultCenter: [110.479, 29.117],
    
    // 默认缩放级别
    defaultZoom: 14
  },

  // 地图引擎策略：auto / amap / leaflet
  provider: {
    mode: 'auto'
  },

  // Leaflet 兼容地图瓦片源（按顺序回退）
  leaflet: {
    tileSources: [
      {
        name: 'Gaode Road',
        url: 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
        options: { maxZoom: 18, subdomains: '1234', attribution: '&copy; AutoNavi' }
      },
      {
        name: 'OpenStreetMap',
        url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        options: { maxZoom: 19, subdomains: 'abc', attribution: '&copy; OpenStreetMap' }
      },
      {
        name: 'Carto Light',
        url: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
        options: { maxZoom: 19, subdomains: 'abcd', attribution: '&copy; CARTO &copy; OpenStreetMap' }
      }
    ]
  },
  
  // 区县映射配置
  districtMapping: {
    // 张家界市区县
    zjj: {
      '永定区': '张家界市永定区',
      '武陵源区': '张家界市武陵源区',
      '慈利县': '张家界市慈利县',
      '桑植县': '张家界市桑植县'
    },
    // 长沙市区县
    cs: {
      '岳麓区': '长沙市岳麓区',
      '芙蓉区': '长沙市芙蓉区',
      '天心区': '长沙市天心区',
      '开福区': '长沙市开福区',
      '雨花区': '长沙市雨花区',
      '望城区': '长沙市望城区'
    }
  },
  
  // POI特殊映射
  poiMapping: {
    '张家界学院': '张家界市永定区',
    '吉首大学张家界': '张家界市永定区',
    '湖南中医药大学': '长沙市岳麓区',
    '中南大学': '长沙市岳麓区',
    '湖南大学': '长沙市岳麓区',
    '湖南师范大学': '长沙市岳麓区'
  }
}

export default mapConfig
