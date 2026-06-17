# Changelog

本文档记录帮帮农(Bang Bang Agro)智慧农业平台的版本演进。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/),
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

---

## [1.1.0] - 2026-06-06

### 性能优化(P0)
- **知识库 RAG 搜索性能提升 50 倍**
  - `KnowledgeServiceImpl` 启动时把所有有 embedding 的文档加载进 `ConcurrentHashMap`
  - 搜索由 "全表查询 + N 次 JSON.parse"(~500ms+ CPU)变为 "纯内存遍历"(~5-10ms)
  - 文档写入/更新时同步维护缓存,保证一致性
  - 缓存为空时自动降级回全表扫描路径,可用性 100%
- **巡检日志单条 save 循环改为 saveBatch**
  - `AutoPatrolService.doPatrol` 和事件驱动监听器都使用批量插入
  - N 次数据库往返压缩为 1 次

### 架构改造(P1)
- **巡检从"30 分钟轮询"升级为"事件驱动"**,报警延迟降低 1800 倍
  - 新增 `SensorDataSyncedEvent` 应用事件
  - `ScheduledTasks.syncOneNetData` 每 30 秒 OneNET 数据同步完成后发布事件
  - `AutoPatrolService.onSensorDataSynced` `@EventListener` + `@Async` 异步监听
  - 25 秒冷却窗口防止传感器风暴
  - LLM 综合分析改为 `@Async`,主巡检流程不再阻塞几十秒
  - 一键回退开关:`patrol.event-driven.enabled=false`
  - 保留 30 分钟定时巡检作为兜底
  - 解决 Spring AOP `this.` 自调用导致 `@Async` 失效的经典坑,使用 `@Autowired @Lazy` self-injection

### Bug 修复
- **修复 AI 巡检报告污染问题**
  - 历史问题:`runAiAnalysis` 在 LLM 不可用时走 `fallbackPlan`(1000 行 if-else),
    把巡检 prompt 误判为对话场景,返回"为您打开监控界面"之类的 UI 跳转话术
  - 修复:`buildPlanWithTriggerSource` 在 `triggerSource="auto_patrol"` 时完全屏蔽 fallback,
    宁可返回 null(=报告字段留空)也不污染数据
  - 新增 `POST /api/patrol/cleanup-ai-reports` 一键清理历史污染数据
- **修复 BUG-01: 管理员新建用户无法登录**
  - 旧逻辑用 `RandomUtil.randomString(12)` 生成密码,`sanitizeUser` 又置 null,管理员看不到
  - 修复:生成可读默认密码 `Aa{username}@2026`,通过 `Result.msg` 字段返回给前端 `ElMessage`,
    后端日志也记录,确保管理员知道初始密码
- **修复 `Result` 类缺少带消息成功响应的重载**
  - 新增 `Result.success(data, msg)` 方法

### 新功能
- **量化成果 API `GET /api/dashboard/achievements`**
  - 申报书"应用价值"和答辩演示的核心数据弹药
  - 全部基于真实数据库聚合:累计运行天数、Agent 决策次数、自动操作次数、节水/省人力换算
  - 突出"事件驱动巡检次数"展示 P1 改造效果

### 界面与体验
- **侧边栏菜单重组**,从散乱 8 项变为"故事主线 7 项"
  - 顺序:工作台 → 无人农场指挥中心 → 预警研判中心 → 3D 数字孪生 → 环境监测 → 视觉巡检 → 消息中心
  - 重命名:"AI 农艺师" → "无人农场指挥中心"(突出差异化,避免烂大街)
  - 重命名:"环境监测与联动" → "环境监测"、"视觉巡检与异常识别" → "视觉巡检"(精简)
  - 二级"更多功能"按"农场数据 / 经营协同 / 系统后台"清晰分组
- **三处命名统一**:菜单 / 面包屑 / 页面 H1 标题都叫"无人农场指挥中心"

### 工程化
- 主类加 `@EnableAsync` 启用异步执行
- `application.yml` 新增 `patrol.event-driven.enabled` / `patrol.event-driven.cooldown-ms` 配置项

---

## [1.0.0] - 2026-05-29

### 重大升级
- Spring Boot 2.5.12 → 3.4.5
- Java 1.8 → 17
- MyBatis-Plus 3.5.1 → 3.5.9
- Swagger:springfox 3.0.0 → springdoc 2.8.6
- Vue 2.6.11 → 3.5.13
- 构建工具:Vue CLI 4.5 → Vite 6
- Element UI 2.15.6 → Element Plus 2.9
- 状态管理:Vuex 3 → Pinia 2
- AI 框架:手动 HTTP 调用 → Spring AI 1.0.0

### 核心能力
- 27 个 Controller、35+ API 端点
- 22 个 `@Tool` 注解的 Agent Function Calling 工具
- 三层自主巡检架构(规则引擎 → 传感器事件 → Agent 决策)
- 4 个作物 YOLOv8 病害检测模型(玉米/水稻/草莓/番茄)
- OneNET IoT 平台 + STM32 真实硬件接入
- 高德地图 + Leaflet + Three.js 多视角农场可视化
- WebSocket 实时聊天 + 好友/群组系统
- per-user AI 配置 + 6 个提供商接入(Qwen/DeepSeek/GLM/MiniMax/OpenAI/Custom)
