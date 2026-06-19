# 🌾 帮帮农 — 智慧农业综合管理平台

> 一站式农业数字化解决方案，融合 IoT 设备监控、AI 智能决策、农产品供应链管理与实时协作。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17+-orange.svg)
![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen.svg)

## ✨ 核心特性

### 🤖 AI 智能决策
- **AI Agent 自主决策**：基于 Spring AI 1.0 的 22 个工具方法，自动执行采购、库存调整等操作
- **多模态视觉检测**：支持豆包/通义千问等大模型进行果蔬病虫害与成熟度识别
- **YOLO 番茄检测**：本地部署的番茄成熟度与病害检测模型
- **审批工作流**：全自动/半审批/全审批三种模式，高风险操作需人工确认

### 📡 IoT 设备监控
- **实时传感器数据**：温湿度、土壤湿度、光照强度等环境参数采集
- **远程设备控制**：通过 Web 界面控制水泵、补光灯、风扇等设备
- **OneNET 物联网平台**：支持 STM32 真实设备接入，HMAC-SHA256 安全签名
- **事件驱动告警**：传感器数据异常时秒级触发告警（比轮询快 1800 倍）

### 🗺️ 农田管理
- **GIS 地图可视化**：基于高德地图的地块展示与管理
- **3D 数字孪生**：Three.js 构建的农场三维可视化
- **地块资产管理**：地块信息、作物种植、产量统计
- **风险预警**：基于环境数据的病虫害、干旱等风险预测

### 📦 供应链管理
- **进销存系统**：采购管理、销售管理、库存管理
- **线上销售**：农产品在线销售平台
- **数据报表**：多维度经营数据分析与可视化

### 💬 实时协作
- **WebSocket 聊天**：基于 Spring AI 的智能问答助手
- **消息通知**：系统公告、告警通知实时推送
- **好友系统**：用户间添加好友、在线状态显示

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端展示层                            │
│   Vue 3.5 + TypeScript + Vite + Element Plus + ECharts      │
│   高德地图 JS API + Three.js 3D + WebSocket                 │
├─────────────────────────────────────────────────────────────┤
│                        后端服务层                            │
│   Spring Boot 3.4 + MyBatis-Plus + Spring Security          │
│   JWT 认证 + WebSocket + RESTful API + Spring AI 1.0        │
│   ChatClient + @Tool Function Calling                       │
├─────────────────────────────────────────────────────────────┤
│                       AI 服务层                             │
│   豆包/通义千问 API（多模态视觉）+ Flask + YOLO v8+（本地）    │
├─────────────────────────────────────────────────────────────┤
│                       数据存储层                            │
│   MySQL 8.0 + Redis 缓存 + OneNET IoT 平台                  │
├─────────────────────────────────────────────────────────────┤
│                       硬件设备层                            │
│   STM32 + DHT11 温湿度 + 土壤湿度传感器 + 水泵/风扇/补光灯    │
└─────────────────────────────────────────────────────────────┘
```

## 📁 项目结构

```
bang-bang-agro/
├── springboot/                    # 后端 Spring Boot 项目
│   ├── src/main/java/com/farmland/intel/
│   │   ├── config/               # 配置类（WebSocket、定时任务等）
│   │   ├── controller/           # REST API 控制器
│   │   │   ├── AgentController         # AI Agent 接口
│   │   │   ├── AiConfigController      # AI 配置管理
│   │   │   ├── ChatController          # 聊天接口
│   │   │   ├── CropAnalysisController  # 作物分析
│   │   │   ├── FruitDetectController   # 果蔬检测
│   │   │   ├── InventoryController     # 库存管理
│   │   │   ├── PurchaseController      # 采购管理
│   │   │   ├── SalesController         # 销售管理
│   │   │   └── ...                     # 其他控制器
│   │   ├── entity/               # 实体类
│   │   ├── mapper/               # MyBatis Mapper
│   │   ├── service/              # 业务逻辑层
│   │   │   ├── AgentService           # AI Agent 核心
│   │   │   ├── AgentTools             # Agent 工具方法（22个）
│   │   │   ├── ChatModelFactory       # 多模型工厂
│   │   │   └── VisionAnalysisService  # 视觉分析服务
│   │   └── websocket/            # WebSocket 服务
│   ├── src/main/resources/
│   │   ├── application.yml       # 应用配置
│   │   └── mapper/               # MyBatis XML 映射
│   └── sql/                      # 数据库脚本
│       ├── 00_schema/            # 建表语句
│       ├── 01_migrations/        # 迁移脚本
│       ├── 02_seed/              # 初始数据
│       └── 03_maintenance/       # 维护脚本
│
├── vue/                          # 前端 Vue 项目
│   ├── src/
│   │   ├── views/                # 页面组件
│   │   │   ├── Person.vue              # 个人中心（AI 配置）
│   │   │   ├── FruitDetect.vue         # 果蔬检测
│   │   │   ├── ChatWindow.vue          # 聊天窗口
│   │   │   ├── UnmannedFarm.vue        # 无人农场
│   │   │   └── ...                     # 其他页面
│   │   ├── components/           # 公共组件
│   │   ├── router/               # 路由配置
│   │   ├── store/                # 状态管理
│   │   └── utils/                # 工具函数
│   └── package.json
│
├── TomatoDetection/              # YOLO 番茄检测模型
│   ├── train/                    # 模型训练
│   ├── val/                      # 模型验证
│   └── runs/                     # 训练结果
│
├── ZET6/                         # STM32 嵌入式代码
│   ├── 01_基础版/                 # 基础传感器版本
│   ├── 02_加lcd显示版/            # LCD 显示版本
│   ├── 04_加风扇版_不带语音/       # 风扇版本
│   └── 05_扩展版_土壤传感器_风扇/   # 完整版本
│
├── 最终EXE/                      # 打包的可执行文件
├── docker-compose.yml            # Docker 编排
├── PROJECT.md                    # 项目详细文档
└── README.md                     # 本文件
```

## 🚀 快速开始

### 方式一：Docker Compose（推荐）

```bash
# 克隆项目
git clone https://gitee.com/wu-zhenyu666/bang-bang-agro.git
cd bang-bang-agro

# 启动所有服务
docker compose up -d

# 访问应用
# 前端：http://localhost:8080
# 后端 API：http://localhost:9090
# Swagger：http://localhost:9090/swagger-ui/index.html
```

### 方式二：手动启动

#### 1. 环境要求
- JDK 17+
- Node.js 18+
- MySQL 8.0
- Maven 3.8+

#### 2. 启动后端

```bash
cd springboot

# 创建数据库
mysql -u root -p < sql/00_schema/init.sql

# 导入初始数据
mysql -u root -p bangbangagro < sql/02_seed/init_all.sql

# 启动服务
mvn spring-boot:run
```

#### 3. 启动前端

```bash
cd vue

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

#### 4. 配置 AI 服务（可选）

在 `application.yml` 中配置 AI 服务：

```yaml
spring:
  ai:
    dashscope:
      api-key: your-api-key    # 通义千问 API Key
    openai:
      api-key: your-api-key    # DeepSeek API Key
      base-url: https://api.deepseek.com/v1
```

或在前端 `个人中心` 页面配置 AI 模型。

## 📡 IoT 设备接入

### 支持的设备
- **STM32F103C8T6**：基础版，连接 DHT11 温湿度传感器
- **STM32F103ZET6**：扩展版，支持土壤湿度传感器、风扇、补光灯

### 接入步骤

1. **烧录固件**：使用 `ZET6/05_扩展版_土壤传感器_风扇/` 中的 `.hex` 文件
2. **配置 OneNET**：在 OneNET 平台创建设备，获取产品ID和设备ID
3. **修改代码**：更新 `ZET6/` 中的 WiFi 配置（SSID、密码、设备ID）
4. **启动后端**：后端会自动从 OneNET 拉取传感器数据

详细接线图请参考：[硬件设备接入方案.md](硬件设备接入方案.md)

## 🔧 AI 功能配置

### 多模态视觉检测

支持两种视觉引擎切换：

| 引擎 | 模型 | 适用场景 |
|------|------|----------|
| 豆包视觉 | doubao-1.5-vision-pro-32k | 病虫害识别、成熟度判断 |
| 通义千问 | Qwen/Qwen3.5-27B | 通用图像分析 |
| 硅基流动 | Qwen/Qwen3.6-35B-A3B | 高性价比方案 |

配置方式：`个人中心 → 视觉模型配置`

### AI 审批策略

| 模式 | 说明 |
|------|------|
| 全自动 | AI 直接执行所有写操作（采购、库存调整等） |
| 半审批（推荐） | 通知/库存自动，采购/销售待确认 |
| 全审批 | 所有写操作都需人工确认 |

## 🗄️ 数据库

### 主要数据表

| 表名 | 说明 |
|------|------|
| user | 用户信息 |
| farmland | 地块信息 |
| sensor_reading | 传感器数据 |
| device | IoT 设备 |
| purchase / sale | 采购/销售记录 |
| inventory | 库存信息 |
| ai_config | AI 配置 |
| agent_task_queue | AI 任务队列 |
| chat_message | 聊天记录 |
| knowledge_document | 知识库文档 |

### 数据库初始化

```bash
# 执行完整初始化脚本
mysql -u root -p < springboot/sql/init_all.sql
```

## 📚 API 文档

启动后端后访问：http://localhost:9090/swagger-ui/index.html

### 核心 API

| 模块 | 端点 | 说明 |
|------|------|------|
| AI Agent | `POST /api/agent/chat` | AI 智能对话 |
| AI 配置 | `GET/POST /ai-config` | AI 模型配置 |
| 果蔬检测 | `POST /crop-analysis/analyze` | 上传图片分析 |
| 作物分析 | `POST /crop-analysis/llm` | 多模态视觉分析 |
| 设备控制 | `POST /aether/device/{id}/control` | 远程控制设备 |
| 采购管理 | `GET/POST /purchase` | 采购单 CRUD |
| 库存管理 | `GET/POST /inventory` | 库存查询/调整 |
| 消息通知 | `WebSocket /ws` | 实时消息推送 |

## 🛠️ 开发指南

### 代码规范
- 后端遵循阿里巴巴 Java 开发手册
- 前端使用 ESLint + Prettier 格式化
- 提交信息格式：`type(scope): description`

### 分支策略
- `main`：生产分支，保持稳定
- `develop`：开发分支，集成最新功能
- `feature/*`：功能分支
- `fix/*`：修复分支

## 📝 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)

## ⚠️ 注意事项

1. **API Key 安全**：请勿将 API Key 提交到 Git 仓库
2. **数据库密码**：生产环境请修改默认密码
3. **端口配置**：默认前端 8080、后端 9090，可在配置文件中修改
4. **OneNET 费用**：IoT 平台可能产生流量费用，请注意使用量

## 📄 许可证

本项目仅供学习交流使用，禁止用于商业用途。

---

**开发者**: wu-zhenyu666

**联系方式**: 请通过 Gitee Issues 反馈问题

**最后更新**: 2026-06-19
