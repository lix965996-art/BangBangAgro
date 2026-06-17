# 帮帮农 — 智慧农业综合管理平台

> 基于 Spring Boot + Vue 的智慧农业物联网管理系统，集成 IoT 设备监控、AI 病害检测、智能决策与农产品供应链管理。

## 核心功能

| 模块 | 说明 |
|------|------|
| IoT 设备监控 | 实时采集温湿度、光照等传感器数据，支持设备远程控制 |
| AI 番茄检测 | 基于 YOLO 的番茄成熟度识别与病害检测 |
| 农田管理 | 地块信息管理、GIS 地图可视化、风险预警 |
| 智能决策 | 基于 AI Agent 的农业决策辅助与作物分析 |
| 供应链管理 | 进销存管理、线上销售、采购管理 |
| 实时通讯 | WebSocket 在线聊天与消息通知 |
| 数据大屏 | 多维度数据可视化仪表盘 |

## 技术架构

```
┌─────────────────────────────────────────────┐
│                   前端展示层                   │
│         Vue 3.5 + Element Plus + ECharts         │
│         高德地图 / Three.js 3D 可视化          │
├─────────────────────────────────────────────┤
│                   后端服务层                   │
│      Spring Boot 3.4 + MyBatis-Plus          │
│      JWT 认证 + WebSocket + RESTful API       │
│      Spring AI 1.0 (ChatClient + @Tool)      │
├─────────────────────────────────────────────┤
│                  AI 服务层                    │
│   Flask API + Ultralytics YOLO v8+ (成熟度/病害检测)         │
├─────────────────────────────────────────────┤
│                  数据存储层                   │
│         MySQL 8.0 + OneNET IoT 平台          │
└─────────────────────────────────────────────┘
```

## 快速开始

### 🚀 Docker Compose 一键启动（推荐）

```bash
docker compose up -d
```

启动后访问:
- 后端 API: http://localhost:9090
- Swagger: http://localhost:9090/swagger-ui/index.html

### 🔧 手动启动 / 生产部署

详见 [PROJECT.md](PROJECT.md) 第十三节「部署指南」。

## 核心技术亮点

### AI Agent 真实落地
- 基于 Spring AI 1.0 ChatClient + `@Tool` Function Calling
- 22 个工具方法，每次决策记录到 `agent_decision_chain` 表
- `ConfidenceEvaluator` 风险评分，高风险任务进入审批队列

### 事件驱动巡检架构
- STM32 传感器数据每 30 秒同步入库后，通过 Spring `ApplicationEvent` 立即触发
- 报警延迟从最坏 30 分钟降到秒级（降低 1800 倍）

### 知识库 RAG 检索
- 启动时全量 embedding 加载进 `ConcurrentHashMap` 内存缓存
- 搜索性能提升 50 倍（~500ms → ~5ms）

### 真实硬件 + IoT 闭环
- 接入 OneNET 物联网平台 + 2 台 STM32 真实设备
- 真实控制水泵、补光灯、风扇（HMAC-SHA256 签名）

## 文档索引

| 文档 | 说明 |
|------|------|
| [PROJECT.md](PROJECT.md) | 项目全貌：架构、数据库、模块详解、API 清单、创新点、部署指南 |
| [CHANGELOG.md](CHANGELOG.md) | 版本演进记录 |

## 许可证

本项目仅供学习交流使用。
