/**
 * API配置文件 - 统一管理所有API地址
 * 开发环境自动使用localhost，生产环境使用环境变量
 */

// 基础API地址
const normalizeLocalUrl = (url) => {
  if (typeof window === 'undefined' || !url) return url;
  try {
    const parsed = new URL(url);
    const pageHost = window.location.hostname;
    const isLocalApi = parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1';
    const isLocalPage = pageHost === 'localhost' || pageHost === '127.0.0.1';
    if (isLocalApi && isLocalPage) {
      parsed.hostname = pageHost;
      return parsed.toString().replace(/\/$/, '');
    }
  } catch (e) {
    return url;
  }
  return url;
};

export const BASE_URL = normalizeLocalUrl(import.meta.env.VUE_APP_API_BASE_URL || 'http://localhost:9090');

// Python服务地址（果蔬检测）
export const PYTHON_API_URL = import.meta.env.VUE_APP_PYTHON_API_URL || 'http://localhost:5000';

// 完整的API端点
export const API_ENDPOINTS = {
  // 文件相关
  fileUpload: `${BASE_URL}/file/upload`,
  
  // 用户相关
  userImport: `${BASE_URL}/user/import`,
  userExport: `${BASE_URL}/user/export`,
  
  // 销售相关
  salesExport: `${BASE_URL}/sales/export`,
  
  // 采购相关
  purchaseExport: `${BASE_URL}/purchase/export`,
  
  // 库存相关
  inventoryExport: `${BASE_URL}/inventory/export`,
  
  // 在线销售相关
  onlineSaleExport: `${BASE_URL}/onlineSale/export`,
  
  // Python检测服务
  pythonDetect: {
    both: `${PYTHON_API_URL}/api/detect/both`,
    ripeness: `${PYTHON_API_URL}/api/detect/ripeness`,
    disease: `${PYTHON_API_URL}/api/detect/disease`,
    health: `${PYTHON_API_URL}/api/health`
  }
};

// 导出便捷方法
export default {
  BASE_URL,
  PYTHON_API_URL,
  ...API_ENDPOINTS
};
