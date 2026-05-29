# 帮帮农 — 智慧农业综合管理平台

> 基于 Spring Boot + Vue 的智慧农业物联网管理系统，集成 IoT 设备监控、AI 病害检测、智能决策与农产品供应链管理。

## 项目简介

帮帮农是一个面向现代农业的综合管理平台，旨在通过物联网、人工智能和大数据技术，帮助农户实现精准种植、智能管理和高效运营。

### 核心功能

- **IoT 设备监控** — 实时采集温湿度、光照等传感器数据，支持设备远程控制
- **AI 番茄检测** — 基于 YOLO 的番茄成熟度识别与病害检测
- **农田管理** — 地块信息管理、GIS 地图可视化、风险预警
- **智能决策** — 基于 AI Agent 的农业决策辅助与作物分析
- **供应链管理** — 进销存管理、线上销售、采购管理
- **实时通讯** — WebSocket 在线聊天与消息通知
- **数据大屏** — 多维度数据可视化仪表盘

## 技术架构

```
┌─────────────────────────────────────────────┐
│                   前端展示层                   │
│         Vue 2 + Element UI + ECharts         │
│         高德地图 / Three.js 3D 可视化          │
├─────────────────────────────────────────────┤
│                   后端服务层                   │
│      Spring Boot 3.4 + MyBatis-Plus          │
│      JWT 认证 + WebSocket + RESTful API       │
├─────────────────────────────────────────────┤
│                  AI 服务层                    │
│   Flask API + Ultralytics YOLO v8+ (成熟度/病害检测)         │
├─────────────────────────────────────────────┤
│                  数据存储层                   │
│         MySQL 8.0 + OneNET IoT 平台          │
└─────────────────────────────────────────────┘
```

## 项目结构

```
bang-bang-agro/
├── springboot/              # 后端服务 (Spring Boot)
│   ├── src/main/java/       # Java 源码
│   ├── src/main/resources/  # 配置文件
│   └── sql/                 # 数据库脚本
├── vue/                     # 前端应用 (Vue 3 + Vite)
│   ├── src/views/           # 页面组件
│   ├── src/components/      # 公共组件
│   ├── src/router/          # 路由配置
│   └── src/utils/           # 工具函数
├── TomatoDetection/         # AI 番茄检测服务 (Python)
│   ├── integrated_api_server.py  # Flask API 服务
│   └── models/              # YOLO 模型文件
├── native-android-app/      # Android 原生应用
├── smart-agriculture.sql    # 数据库初始化脚本
└── README.md
```

## 快速开始

### 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 1.8+ |
| Node.js | 14+ |
| MySQL | 8.0+ |
| Python | 3.8+ |
| Maven | 3.6+ |

### 1. 数据库初始化

```bash
mysql -u root -p < smart-agriculture.sql
```

### 2. 启动后端服务

```bash
cd springboot
# 配置数据库连接 (修改 application.yml 或设置环境变量)
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd vue
npm install
npm run serve
```

### 4. 启动 AI 检测服务 (可选)

```bash
cd TomatoDetection
pip install -r api_requirements.txt
python integrated_api_server.py
```

### 5. 访问系统

- 前端地址: `http://localhost:8080`
- 后端 API: `http://localhost:9090`
- AI 检测 API: `http://localhost:5000`
- Swagger 文档: `http://localhost:9090/swagger-ui/`

## 功能模块

| 模块 | 说明 |
|------|------|
| 首页大屏 | 数据可视化仪表盘，展示关键农业指标 |
| 农田管理 | 地块信息、GIS 地图、3D 可视化 |
| IoT 监控 | 传感器数据实时展示、设备远程控制 |
| 番茄检测 | AI 图像识别，支持成熟度和病害检测 |
| 作物分析 | AI 驱动的作物生长分析与建议 |
| 供应链 | 进销存管理、采购、销售、线上商城 |
| 系统管理 | 用户、角色、菜单、权限管理 |
| 智能助手 | AI Agent 对话式农业咨询 |

## 许可证

本项目仅供学习交流使用。
