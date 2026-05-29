# 帮帮农 — 智慧农业综合管理平台 项目文档

> 本文档供 AI 助手快速了解项目全貌，避免每次读取全部源码浪费 token。
> 最后更新：2026-05-29

---

## 一、项目概览

帮帮农是一个面向现代农业的综合管理平台，集成 IoT 设备监控、AI 病害检测、智能决策 Agent、农产品供应链管理、实时通讯等功能。

### 技术架构

```
┌─────────────────────────────────────────────────┐
│                   前端展示层                       │
│         Vue 3.5 + Element Plus + ECharts         │
│         Vite 6 构建 + Pinia 状态管理               │
│         高德地图 / Three.js 3D / Leaflet          │
├─────────────────────────────────────────────────┤
│                   后端服务层                       │
│      Spring Boot 3.4 + MyBatis-Plus 3.5          │
│      Java 17 + JWT 4.4 + WebSocket               │
│      springdoc-openapi 2.8 (Swagger UI)          │
│      Spring AI 1.0 (ChatClient + @Tool)          │
├─────────────────────────────────────────────────┤
│                  AI 服务层                        │
│   Flask API + YOLOv8 (ultralytics)               │
│   Qwen / DeepSeek 大模型 Agent                    │
├─────────────────────────────────────────────────┤
│                  数据存储层                       │
│         MySQL 8.0 + OneNET IoT 平台              │
└─────────────────────────────────────────────────┘
```

### 端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| Vue 前端 | 8080 | Vite dev server |
| Spring Boot 后端 | 9090 | REST API + WebSocket |
| Python AI 检测 | 5000 | Flask + YOLOv8 |
| MySQL | 3306 | 数据库 |

### 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Python | 3.8+ |
| Maven | 3.6+ |

---

## 二、项目结构

```
bang-bang-agro-master/1.0/
├── springboot/                    # 后端服务 (Spring Boot 3.4.5)
│   ├── pom.xml                    # Maven 配置
│   ├── src/main/java/com/farmland/intel/
│   │   ├── SpringbootApplication.java  # 启动类
│   │   ├── controller/           # 27 个控制器
│   │   ├── service/              # 服务层（接口 + 实现 + AI 相关）
│   │   ├── entity/               # 28 个实体类
│   │   ├── mapper/               # 29 个 Mapper
│   │   ├── config/               # 配置类
│   │   ├── agent/                # Agent 数据模型
│   │   ├── common/               # 常量、响应封装
│   │   ├── exception/            # 异常处理
│   │   ├── utils/                # 工具类
│   │   └── component/            # WebSocket 组件
│   ├── src/main/resources/
│   │   ├── application.yml       # 主配置
│   │   ├── application-local.yml # 本地配置（gitignore）
│   │   ├── mapper/               # 8 个 Mapper XML
│   │   └── logback-spring.xml    # 日志配置
│   └── sql/                      # 数据库脚本
├── vue/                           # 前端应用 (Vue 3.5)
│   ├── package.json
│   ├── vite.config.js            # Vite 构建配置
│   ├── index.html                # 入口 HTML
│   ├── src/
│   │   ├── main.js               # 应用入口
│   │   ├── App.vue               # 根组件（含浮动 AI 助手）
│   │   ├── views/                # 26 个页面组件
│   │   ├── components/           # 9 个公共组件
│   │   ├── router/               # 路由配置
│   │   ├── store/                # Pinia 状态管理
│   │   ├── utils/                # 工具函数
│   │   ├── config/               # 配置文件
│   │   └── assets/               # 静态资源（51 张图片 + CSS）
│   ├── electron/                 # Electron 桌面端
│   └── public/                   # 公共静态资源
├── TomatoDetection/              # Python AI 检测服务
│   ├── integrated_api_server.py  # Flask API 主服务
│   ├── models/                   # YOLOv8 检测模型
│   ├── disease_weights/          # 病害检测权重（玉米/水稻/草莓/番茄）
│   └── UIProgram/                # PyQt5 桌面检测工具
└── README.md
```

---

## 三、后端详解 (Spring Boot)

### 3.1 依赖栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-parent | 3.4.5 | 框架 |
| mybatis-plus-spring-boot3-starter | 3.5.9 | ORM |
| springdoc-openapi-starter-webmvc-ui | 2.8.6 | API 文档 |
| spring-ai-starter-model-openai | 1.0.0 | Spring AI（ChatClient） |
| spring-ai-model-chat-memory-repository-jdbc | 1.0.0 | Spring AI 对话记忆 |
| spring-ai-alibaba-starter-dashscope | 1.0.0.2 | DashScope Embedding |
| java-jwt | 4.4.0 | JWT 认证 |
| hutool-all | 5.8.35 | 工具库 |
| poi-ooxml | 5.3.0 | Excel 导入导出 |

### 3.2 Controller 层（27 个控制器，35+ API 端点）

#### 系统管理
| 控制器 | 路径前缀 | 功能 |
|--------|---------|------|
| `UserController` | `/user` | 登录、注册、密码重置、用户 CRUD、Excel 导入导出、头像上传 |
| `RoleController` | `/role` | 角色 CRUD（仅管理员）、角色-菜单权限分配 |
| `MenuController` | `/menu` | 菜单 CRUD、字典集成、角色过滤 |
| `FileController` | `/file` | 文件上传（MD5 去重）、分页列表、下载、删除 |

#### 农田与 IoT
| 控制器 | 路径前缀 | 功能 |
|--------|---------|------|
| `StatisticController` | `/statistic` | 农田统计 CRUD、Excel 导入导出、大屏数据聚合 |
| `FarmlandAlertController` | `/farmland-alert` | 预警 CRUD、IoT 回调端点、预警统计 |
| `AetherDeviceController` | `/aether/device` | IoT 设备状态、LED/风扇/水泵控制、历史传感器数据 |
| `AetherWeatherController` | `/aether/weather` | 高德天气 API、实时+预报天气、地图配置 |
| `AmapProxyController` | `/amap` | 高德地图 API 代理（解决 CORS）、地理编码、输入提示、天气 |
| `HealthIndexController` | `/health-index` | 农田健康指数计算（0-100）、批量评估 |

#### AI 与 Agent
| 控制器 | 路径前缀 | 功能 |
|--------|---------|------|
| `AgentController` | `/api/agent` | Agent 计划生成、动作执行、记忆 CRUD、决策链、任务队列、传感器事件 |
| `ChatController` | `/api/chat` | AI 对话（Qwen/DeepSeek）、农业专家问答 |
| `AiConfigController` | `/ai-config` | Per-user AI 配置 CRUD、提供商预设、连接测试 |
| `CropAnalysisController` | `/crop-analysis` | 作物成熟度/病害检测（转发到 Python 服务） |
| `FruitDetectController` | `/fruit-detect` | 果蔬检测（旧路由，转发到 Python 服务） |
| `KnowledgeController` | `/api/knowledge` | 农业知识库语义搜索、文档 CRUD、Embedding 生成 |
| `AgriDailyReportController` | `/agri-report` | AI 每日农业报告、天气分析、农田建议 |
| `AutoPatrolController` | `/api/patrol` | 自主巡检状态、开关、手动触发、日志 |
| `BusinessAnalysisController` | `/business-analysis` | 经营利润分析、成本分析、作物级利润 |

#### 供应链
| 控制器 | 路径前缀 | 功能 |
|--------|---------|------|
| `InventoryController` | `/inventory` | 库存 CRUD、Excel 导入导出 |
| `PurchaseController` | `/purchase` | 采购 CRUD、Excel 导入导出 |
| `SalesController` | `/sales` | 销售 CRUD、Excel 导入导出 |
| `OnlineSaleController` | `/online-sale` | 线上销售 CRUD、库存联动、Excel 导出 |
| `NoticeController` | `/notice` | 系统公告 CRUD、Excel 导入导出 |

#### 通讯
| 控制器 | 路径前缀 | 功能 |
|--------|---------|------|
| `ChatMessageController` | `/chat-message` | 私聊/群聊消息历史、联系人、未读数 |
| `ChatGroupController` | `/chat-group` | 群组创建、列表、成员管理 |
| `FriendshipController` | `/friendship` | UID 搜索、好友添加/删除、好友请求 |

### 3.3 Service 层

#### 核心 AI 服务
| 类 | 职责 |
|----|------|
| `AgentService` | Agent 编排：Spring AI ChatClient + @Tool Function Calling，22 个工具，多轮对话，兜底规则引擎 |
| `AgentTools` | 22 个 `@Tool` 注解方法：查询农田/设备/销售/采购/库存/用户/系统数据，控制灌溉/补光灯，创建订单，发送通知，搜索知识库 |
| `ChatModelFactory` | 编程式构建 ChatClient，支持 per-user 配置（6 个提供商），缓存 + ChatMemory Advisor |
| `ChatMemoryConfig` | Spring AI JdbcChatMemoryRepository + MessageWindowChatMemory（保留 20 条） |
| `EmbeddingService` | Spring AI EmbeddingModel，DashScope text-embedding-v3，1024 维向量 |
| `AutoPatrolService` | 三层自主巡检：规则引擎 → 传感器事件记录 → Agent 自主决策 |
| `ConfidenceEvaluator` | Agent 风险评分（0-100），决定自动执行或人工审批 |
| `ModelCallLogger` | AI 模型调用日志：调用次数、token 用量、成本估算 |

#### 业务服务
| 类 | 职责 |
|----|------|
| `UserServiceImpl` | JWT 登录、BCrypt 注册、密码更新 |
| `OneNetServiceImpl` | OneNET IoT 平台 REST API，HMAC-SHA256 签名，STM32 设备控制 |
| `KnowledgeServiceImpl` | 向量相似度搜索（余弦距离），关键词兜底 |
| `HealthIndexServiceImpl` | 加权健康指数计算、预警生成 |
| `QwenServiceImpl` | DashScope 文本生成 API（旧版，用于 /ask 端点） |
| `FriendshipServiceImpl` | 双向好友关系、好友请求、联系人列表（含最后消息预览） |
| `ChatMessageServiceImpl` | 私聊/群聊消息、联系人聚合、未读计数 |

### 3.4 Entity 层（28 个实体，对应数据库表）

#### 系统管理
| 实体 | 表名 | 说明 |
|------|------|------|
| `User` | `sys_user` | 用户：username, password, nickname, phone, email, avatar, role, uid, status |
| `Role` | `sys_role` | 角色：name, description, flag |
| `RoleMenu` | `sys_role_menu` | 角色-菜单映射（复合主键） |
| `Menu` | `sys_menu` | 菜单：icon, path, sort, parent（层级结构） |
| `Dict` | `sys_dict` | 字典：key-value + type |
| `Files` | `sys_file` | 文件：name, type, size, url, md5, 软删除 |

#### 农田与 IoT
| 实体 | 表名 | 说明 |
|------|------|------|
| `Statistic` | `statistic` | 农田统计：farm, crop, area, yield, 温湿度/光照/CO2/pH |
| `FarmlandAlert` | `farmland_alert` | 预警：type, level, 当前值/阈值, status, suggestion |
| `SensorReading` | `sensor_reading` | 传感器数据：temperature, humidity, soilHumidity, light, pH, CO2 |
| `SensorEvent` | `sensor_event` | 传感器事件：阈值突破、异常、趋势变化 |
| `HealthIndexConfig` | `health_index_config` | 健康指数阈值配置 |

#### 供应链
| 实体 | 表名 | 说明 |
|------|------|------|
| `Inventory` | `inventory` | 库存：produce, warehouse, region, number, safeStock, keeper |
| `InventoryOutbound` | `inventory_outbound` | 出库记录 |
| `Purchase` | `purchase` | 采购：product, number, price, provider, purchaser |
| `Sales` | `sales` | 销售：product, number, price, buyer, shipper |
| `OnlineSale` | `online_sale` | 线上销售：produce, warehouse, quantity, price, status |
| `CropYieldConfig` | `crop_yield_config` | 作物产量参数：cropName, yieldPerMu, pricePerKg, costPerMu |
| `Notice` | `notice` | 系统公告：name, content |

#### AI Agent
| 实体 | 表名 | 说明 |
|------|------|------|
| `AiConfig` | `sys_ai_config` | Per-user AI 配置：provider, baseUrl, apiKey, modelName, temperature |
| `AgentUserMemory` | `agent_user_memory` | 用户记忆：preferences（偏好）, conversationSummary（对话摘要） |
| `AgentDecisionChain` | `agent_decision_chain` | 决策链：chainId, stepIndex, stepType, modelName |
| `AgentTaskQueue` | `agent_task_queue` | 任务队列：taskType, priority, riskLevel, confidenceScore, status |
| `AutoPatrolLog` | `auto_patrol_log` | 巡检日志：triggerType, farmName, decision, actions |
| `KnowledgeDocument` | `knowledge_document` | 知识库：title, category, content, embedding（JSON 向量）, source |

#### 通讯
| 实体 | 表名 | 说明 |
|------|------|------|
| `ChatMessage` | `chat_message` | 消息：fromUser, toUser, group, content, type, read |
| `ChatGroup` | `chat_group` | 群组：name, avatar, owner |
| `ChatGroupMember` | `chat_group_member` | 群成员 |
| `Friendship` | `friendship` | 好友关系 |
| `FriendRequest` | `friend_request` | 好友请求：fromUser, toUser, status |

### 3.5 Spring AI 集成详情

#### 架构
- `ChatModelFactory`：编程式构建 `ChatClient`，支持 per-user 配置（`sys_ai_config` 表）
- 6 个提供商通过 OpenAI 兼容接口统一接入：Qwen, DeepSeek, GLM, MiniMax, OpenAI, Custom
- `@Tool` 注解替代手动 JSON 工具定义（22 个工具）
- `MessageChatMemoryAdvisor` 自动管理对话记忆
- `JdbcChatMemoryRepository` 持久化到 `SPRING_AI_CHAT_MEMORY` 表

#### Agent 工具列表（22 个）
**查询工具（15 个）：**
1. `getAllFarms` — 获取所有农田数据
2. `getFarmDetail` — 获取指定农田详情
3. `getFarmsNeedIrrigation` — 查询需要灌溉的农田
4. `getDeviceStatus` — 获取 IoT 设备状态
5. `getEnvironmentData` — 获取环境监测数据
6. `getSalesData` — 获取销售数据
7. `getPurchaseData` — 获取采购数据
8. `getInventoryData` — 获取库存数据
9. `getProfitAnalysis` — 利润分析
10. `getUserStatistics` — 用户统计
11. `getSystemOverview` — 系统概览
12. `getOnlineSaleData` — 在线销售数据
13. `getBusinessHealthScore` — 经营健康度评分
14. `getTrendAnalysis` — 趋势分析
15. `getComprehensiveReport` — 综合分析报告

**执行工具（7 个）：**
16. `controlIrrigation` — 控制灌溉系统
17. `controlLed` — 控制补光灯
18. `createPurchaseOrder` — 创建采购订单
19. `createSalesOrder` — 创建销售订单
20. `updateInventory` — 更新库存
21. `sendNotification` — 发送系统通知
22. `searchKnowledgeBase` — 查询农业知识库

### 3.6 配置文件

#### application.yml 关键配置
```yaml
server:
  port: 9090
  servlet:
    context-path: /

spring:
  datasource:        # MySQL 连接（通过环境变量配置）
  autoconfigure:
    exclude:         # 排除 Spring AI 自动配置（使用 per-user 编程式构建）
      - org.springframework.ai.openai.autoconfigure.OpenAiAutoConfiguration

mybatis-plus:        # MyBatis-Plus 配置
  mapper-locations: classpath:mapper/*.xml

# 外部服务配置
qwen:                # 通义千问 / DashScope
deepseek:            # DeepSeek
amap:                # 高德地图
onenet:              # OneNET IoT 平台
jwt:                 # JWT 密钥和过期时间
python:              # Python 检测服务地址
auto-irrigation:     # 自动灌溉阈值
patrol:              # 自主巡检配置
agent:               # Agent 自主运行配置
```

### 3.7 定时任务（ScheduledTasks）
- 每 30 秒：同步 OneNET IoT 传感器数据到数据库
- 每 5 分钟：检查自动灌溉条件
- 每天：清理过期传感器数据

---

## 四、前端详解 (Vue 3)

### 4.1 依赖栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| vue | 3.5 | 框架 |
| vue-router | 4.5 | 路由 |
| pinia | 2.3 | 状态管理 |
| element-plus | 2.9 | UI 组件库 |
| echarts | 5.6 | 图表 |
| three | 0.181 | 3D 可视化 |
| leaflet | 1.9 | 地图（备选） |
| @amap/amap-jsapi-loader | 1.0 | 高德地图 |
| axios | 1.7 | HTTP 请求 |
| mitt | 3.0 | 事件总线 |
| gsap | 3.13 | 动画 |
| vite | 6.0 | 构建工具 |
| electron | 30.5 | 桌面端 |

### 4.2 页面组件（26 个页面）

#### 认证与系统
| 文件 | 页面 | 功能 |
|------|------|------|
| `Login.vue` | 登录页 | 用户名/密码表单 + 验证码 + FragmentBackground 动画 |
| `Register.vue` | 注册页 | 新用户注册表单 |
| `404.vue` | 404 页 | 全屏 404 图片 |
| `Manage.vue` | 主布局 | 侧边栏 + 头部 + keep-alive router-view |

#### 仪表盘
| 文件 | 页面 | 功能 |
|------|------|------|
| `Home.vue` | 首页 | 问候语、AI 日报、天气、收入/任务卡片 |
| `DashbordNew.vue` | 监测分析看板 | 农田选择器、KPI 卡片、AI 决策面板、环境图表 |
| `Statistic.vue` | 地块数据总览 | 网格视图、在线/离线状态、搜索、批量灌溉 |
| `UnmannedFarmDashboard.vue` | 无人农场总控 | Agent 决策链、待处理任务、传感器事件 |

#### 农田与地图
| 文件 | 页面 | 功能 |
|------|------|------|
| `Farmland.vue` | 地块资产图谱 | 农田 CRUD 表格、作物图标、边界标记、导出 |
| `FarmMapGaode.vue` | 高德 GIS 驾驶舱 | 全屏高德地图 + HUD 侧边栏、按地区分组、搜索定位 |
| `FarmMap3D.vue` | 3D 农场生态监测 | Three.js 3D 地图 + 农田列表 + 天气徽章 |
| `AetherMonitor.vue` | 微气候调控舱 | 室内外温湿度、土壤指标、迷你折线图、设备状态 |

#### IoT 子系统
| 文件 | 页面 | 功能 |
|------|------|------|
| `IoTDashboard.vue` | IoT 总控驾驶舱 | 在线设备、待处理预警、环境快照、设备联动 |
| `IoTMonitor.vue` | 设备感知联动中心 | 温湿度阈值、7 天趋势图、自动联动开关 |
| `IoTVision.vue` | 视觉巡检辅助 | MJPEG 摄像头流/图片上传、病害检测模型推理 |
| `IoTAlertCenter.vue` | 预警闭环中心 | 待处理预警队列、GIS 链接、操作日志 |

#### AI 与检测
| 文件 | 页面 | 功能 |
|------|------|------|
| `FruitDetect.vue` | 果蔬检测 | 检测统计卡片、视觉模型沙箱 |
| `AutoPatrol.vue` | 自主巡检中心 | 巡检开关、手动触发、统计、历史 |
| `BusinessAnalysis.vue` | 预警研判中心 | 待处理预警、传感器状态、操作日志 |
| `Chat.vue` | 消息中心 | 好友列表、群聊、好友请求、WebSocket 实时聊天 |
| `Notice.vue` | 农情预警指挥中心 | Qwen NLP 关键词云、农事日志时间线 |

#### 供应链
| 文件 | 页面 | 功能 |
|------|------|------|
| `SupplyCenter.vue` | 供给协同中心 | 库存健康率、低库存预警、物资分类 |
| `MarketCenter.vue` | 产销协同中心 | 销售总额、客户覆盖、联销品项、订单录入 |
| `Inventory.vue` | 仓储雷达 | 仓库健康监控、区域热力图、补货预警 |
| `Purchase.vue` | 供应协作网 | 供应商排名、供应集中度、采购台账 |
| `Sales.vue` | 订单流向 | 买家渠道、客户均衡率、热销作物 |
| `OnlineSale.vue` | 现货联销 | 上架/下架/售罄状态、联销覆盖率 |

#### 用户管理
| 文件 | 页面 | 功能 |
|------|------|------|
| `User.vue` | 用户管理 | 用户列表、统计卡片、搜索、增删改 |
| `Role.vue` | 角色管理 | 角色 CRUD、菜单权限分配 |
| `Person.vue` | 个人资料 | 头像上传、UID 复制、信息编辑 |
| `Password.vue` | 修改密码 | 旧/新/确认密码表单 |

### 4.3 公共组件（9 个）

| 文件 | 功能 |
|------|------|
| `Aside.vue` | 侧边栏导航：Logo、主菜单、未读聊天徽章、可折叠子菜单 |
| `Header.vue` | 顶部栏：折叠按钮、面包屑、消息铃铛、用户下拉菜单 |
| `GlobalLoading.vue` | 全屏加载遮罩 + 进度条 |
| `Identify.vue` | Canvas 验证码生成器 |
| `ParticleBackground.vue` | 粒子动画背景（150 个浮动粒子） |
| `FragmentBackground.vue` | 马赛克碎片背景效果 |
| `FarmLocationSelector.vue` | 高德地图选址对话框：地址搜索、多边形绘制、坐标输出 |
| `TodayTaskList.vue` | 今日任务清单：按级别显示预警、建议、时间 |
| `DecisionChainViewer.vue` | Agent 决策链时间线查看器 |

### 4.4 路由配置

- 模式：支持 hash 和 history（通过 `VUE_APP_ROUTER_MODE` 环境变量切换）
- 静态路由：Login、Register、404
- 静态子路由（Manage 下）：26 个页面，全部懒加载
- 动态路由：从 `localStorage.menus` 读取菜单配置，动态注入
- `import.meta.glob` 用于安全的动态视图解析

### 4.5 状态管理（Pinia）

```javascript
useAppStore = {
  state: { currentPathName: '' },
  actions: {
    setPath(),           // 从 localStorage 更新当前路径
    logout()             // 清除存储、重置路由、跳转登录
  }
}
```

### 4.6 工具函数

| 文件 | 功能 |
|------|------|
| `request.js` | Axios 实例：baseURL、60s 超时、token 注入、401 重定向 |
| `eventBus.js` | Mitt 事件总线：灌溉/补光灯/无人机事件常量 |
| `errorHandler.js` | 统一 API 错误处理：HTTP 状态码 → ElMessage 提示 |
| `loading.js` | 编程式全局加载管理器：引用计数、延迟移除 |
| `memoryLeakChecker.js` | 内存泄漏检测：跟踪 interval/timeout/事件监听器 |
| `amapLoader.js` | 高德地图 SDK 加载器：单例注入、安全配置、插件加载 |
| `legacyElementIcons.js` | Element UI 旧版图标兼容层 |
| `assetUrl.js` | Vite import.meta.glob 资源解析 |

---

## 五、Python AI 检测服务

### 5.1 架构
- 框架：Flask
- 模型：YOLOv8 (ultralytics)
- 权重：4 个作物模型（玉米、水稻、草莓、番茄）
- 端口：5000

### 5.2 API 端点
| 端点 | 方法 | 功能 |
|------|------|------|
| `/detect/ripeness` | POST | 成熟度检测 |
| `/detect/disease` | POST | 病害检测 |
| `/detect/both` | POST | 同时检测成熟度+病害 |
| `/health` | GET | 健康检查 |

### 5.3 模型文件
- `models/best.pt` — 通用检测模型
- `disease_weights/corn_best.pt` — 玉米病害
- `disease_weights/rice_best.pt` — 水稻病害
- `disease_weights/strawberry_best.pt` — 草莓病害
- `disease_weights/tomato_best.pt` — 番茄病害

---

## 六、数据库表清单

| 表名 | 说明 | 关联实体 |
|------|------|---------|
| `sys_user` | 用户表 | User |
| `sys_role` | 角色表 | Role |
| `sys_role_menu` | 角色-菜单映射 | RoleMenu |
| `sys_menu` | 菜单表 | Menu |
| `sys_dict` | 字典表 | Dict |
| `sys_file` | 文件表 | Files |
| `sys_ai_config` | AI 配置表 | AiConfig |
| `statistic` | 农田统计表 | Statistic |
| `farmland_alert` | 预警表 | FarmlandAlert |
| `sensor_reading` | 传感器数据表 | SensorReading |
| `sensor_event` | 传感器事件表 | SensorEvent |
| `health_index_config` | 健康指数配置表 | HealthIndexConfig |
| `inventory` | 库存表 | Inventory |
| `inventory_outbound` | 出库记录表 | InventoryOutbound |
| `purchase` | 采购表 | Purchase |
| `sales` | 销售表 | Sales |
| `online_sale` | 线上销售表 | OnlineSale |
| `crop_yield_config` | 作物产量配置表 | CropYieldConfig |
| `notice` | 公告表 | Notice |
| `agent_user_memory` | Agent 用户记忆表 | AgentUserMemory |
| `agent_decision_chain` | Agent 决策链表 | AgentDecisionChain |
| `agent_task_queue` | Agent 任务队列表 | AgentTaskQueue |
| `auto_patrol_log` | 自主巡检日志表 | AutoPatrolLog |
| `knowledge_document` | 知识库文档表 | KnowledgeDocument |
| `chat_message` | 聊天消息表 | ChatMessage |
| `chat_group` | 聊天群组表 | ChatGroup |
| `chat_group_member` | 群成员表 | ChatGroupMember |
| `friendship` | 好友关系表 | Friendship |
| `friend_request` | 好友请求表 | FriendRequest |
| `SPRING_AI_CHAT_MEMORY` | Spring AI 对话记忆表 | （Spring AI 内置） |

---

## 七、启动指南

### 1. 数据库初始化
```bash
mysql -u root -p < smart-agriculture.sql
# 执行 Spring AI ChatMemory 表
mysql -u root -p < springboot/sql/spring_ai_chat_memory.sql
```

### 2. 配置
在 `springboot/src/main/resources/application-local.yml` 中配置：
```yaml
spring:
  datasource:
    password: 你的MySQL密码

qwen:
  api-key: sk-你的通义千问Key

deepseek:
  fallback-api-key: sk-你的DeepSeekKey
```

### 3. 启动后端
```bash
cd springboot
mvn spring-boot:run
```

### 4. 启动前端
```bash
cd vue
npm install
npm run serve
```

### 5. 启动 AI 检测服务（可选）
```bash
cd TomatoDetection
pip install ultralytics flask
python integrated_api_server.py
```

### 6. 访问
- 前端：http://localhost:8080
- 后端 API：http://localhost:9090
- Swagger 文档：http://localhost:9090/swagger-ui/index.html
- AI 检测：http://localhost:5000

---

## 八、版本升级记录

### 2026-05-29 升级
| 组件 | 旧版本 | 新版本 |
|------|--------|--------|
| Spring Boot | 2.5.12 | 3.4.5 |
| Java | 1.8 | 17 |
| MyBatis-Plus | 3.5.1 | 3.5.9 |
| Swagger | springfox 3.0.0 | springdoc 2.8.6 |
| Vue | 2.6.11 | 3.5.13 |
| 构建工具 | Vue CLI 4.5 | Vite 6 |
| Element UI | 2.15.6 | Element Plus 2.9 |
| 状态管理 | Vuex 3 | Pinia 2 |
| AI 框架 | 手动 HTTP 调用 | Spring AI 1.0.0 |

---

## 九、注意事项

1. **Per-user AI 配置**：每个用户可在「个人中心 → AI 模型配置」中独立配置提供商/模型/密钥，存储在 `sys_ai_config` 表
2. **Agent 兜底机制**：如果 LLM API 调用失败，Agent 会回退到基于关键词匹配的规则引擎（~1000 行）
3. **Embedding 存储**：向量以 JSON 数组字符串存储在 MySQL `knowledge_document.embedding` 列，非向量数据库
4. **Element UI 图标兼容**：通过 `legacyElementIcons.js` 和 `legacy-element-icons.css` 双重兼容层支持旧版 `el-icon-*` 图标
5. **Electron 桌面端**：前端同时支持 Web 和 Electron 桌面端打包
6. **`bin/` 目录**：包含旧版部署文件，不影响主项目
