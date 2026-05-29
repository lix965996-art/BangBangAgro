/**
 * 环境配置文件
 */

// 获取当前环境
const getEnv = () => {
  return import.meta.env.MODE || 'development';
};

// 基础配置
const baseConfig = {
  development: {
    apiBaseUrl: 'http://localhost:9090',
    videoUrl: 'http://192.168.137.192/mjpeg/1',
    websocketUrl: 'ws://localhost:8080/ws',
    debug: true,
    timeout: 10000
  },
  testing: {
    apiBaseUrl: 'http://test.example.com',
    videoUrl: 'http://test-server.example.com/mjpeg/1',
    websocketUrl: 'ws://test.example.com/ws',
    debug: true,
    timeout: 15000
  },
  production: {
    apiBaseUrl: 'https://api.example.com',
    videoUrl: 'https://production-server.example.com/mjpeg/1',
    websocketUrl: 'wss://api.example.com/ws',
    debug: false,
    timeout: 30000
  }
};

// 获取当前环境的配置
const currentEnv = getEnv();
const config = {
  ...baseConfig[currentEnv],
  env: currentEnv,
  isDevelopment: currentEnv === 'development',
  isTesting: currentEnv === 'testing',
  isProduction: currentEnv === 'production'
};

export default config;

// 导出环境相关的工具函数
export const getEnvConfig = () => config;
export const isDevelopment = () => config.isDevelopment;
export const isProduction = () => config.isProduction;