module.exports = {
  lintOnSave: false,
  
  // 生产环境公共路径，云端部署时根据实际情况修改
  publicPath: process.env.VUE_APP_ELECTRON === 'true' ? './' : '/',
  
  // 输出目录
  outputDir: 'dist',
  
  // 静态资源目录
  assetsDir: 'static',
  
  // 生产环境是否生成 sourceMap
  productionSourceMap: false,
  
  devServer: {
    host: '0.0.0.0',
    port: 8080,
    open: true,
    disableHostCheck: true, 
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    // 开发环境代理配置
    proxy: {
      '/api': {
        target: process.env.VUE_APP_API_BASE_URL || 'http://localhost:9090',
        changeOrigin: true,
        pathRewrite: {
          '^/api': ''
        }
      }
    }
  },
  
  // webpack配置
  configureWebpack: {
    // 生产环境打包优化
    optimization: {
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          libs: {
            name: 'chunk-libs',
            test: /[\\/]node_modules[\\/]/,
            priority: 10,
            chunks: 'initial'
          },
          elementUI: {
            name: 'chunk-elementUI',
            priority: 20,
            test: /[\\/]node_modules[\\/]_?element-ui(.*)/
          },
          echarts: {
            name: 'chunk-echarts',
            priority: 20,
            test: /[\\/]node_modules[\\/]_?echarts(.*)/
          }
        }
      }
    }
  }
}
