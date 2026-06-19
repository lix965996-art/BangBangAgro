package com.farmland.intel.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.farmland.intel.agent.AgentAction;
import com.farmland.intel.agent.AgentActionResult;
import com.farmland.intel.agent.AgentPlan;
import com.farmland.intel.agent.DecisionChainContext;
import com.farmland.intel.entity.AgentUserMemory;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.entity.Sales;
import com.farmland.intel.entity.Purchase;
import com.farmland.intel.entity.Inventory;
import com.farmland.intel.entity.Notice;
import com.farmland.intel.entity.User;
import com.farmland.intel.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 编排服务：使用 Function Calling 实现智能农业助手
 * 支持工具调用：查询农田数据、获取设备状态、控制设备等
 */
@Service
@Slf4j
public class AgentService {

    // ── 静态兜底配置（application.yml 中配置，DB 配置优先级更高）──
    @Value("${qwen.api-key:}")
    private String fallbackApiKey;

    @Value("${qwen.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String fallbackApiUrl;

    @Value("${qwen.model:qwen-max}")
    private String fallbackModel;

    @Autowired(required = false)
    private com.farmland.intel.service.IAiConfigService aiConfigService;

    @PostConstruct
    public void init() {
        log.debug("AgentService 初始化，静态 API Key: {}",
                 StringUtils.hasText(fallbackApiKey) ? "已配置 (长度:" + fallbackApiKey.length() + ")" : "未配置（将从 DB 读取）");
    }

    /** 获取当前调用用户的 AI 配置，若 DB 无有效配置则降级到 yml 静态值 */
    private com.farmland.intel.entity.AiConfig resolveAiConfig(Integer userId) {
        if (aiConfigService != null && userId != null) {
            com.farmland.intel.entity.AiConfig cfg = aiConfigService.getByUserId(userId);
            if (cfg != null && StringUtils.hasText(cfg.getApiKey())) {
                return cfg;
            }
        }
        // 降级：使用 yml 静态配置
        com.farmland.intel.entity.AiConfig fallback = new com.farmland.intel.entity.AiConfig();
        fallback.setApiKey(fallbackApiKey);
        fallback.setBaseUrl(fallbackApiUrl);
        fallback.setModelName(fallbackModel);
        fallback.setTemperature(new java.math.BigDecimal("0.42"));
        return fallback;
    }

    /**
     * 获取对话模型配置（轻量快速模型）。
     * 优先使用 DB 中的 chatModelName / chatBaseUrl / chatApiKey；
     * 若这三个字段为空，则自动复用主模型的对应字段，保证向后兼容。
     */
    private com.farmland.intel.entity.AiConfig resolveChatConfig(Integer userId) {
        if (aiConfigService != null && userId != null) {
            com.farmland.intel.entity.AiConfig cfg = aiConfigService.getByUserId(userId);
            if (cfg != null && StringUtils.hasText(cfg.getApiKey())) {
                com.farmland.intel.entity.AiConfig chatCfg = new com.farmland.intel.entity.AiConfig();
                chatCfg.setApiKey(StringUtils.hasText(cfg.getChatApiKey())   ? cfg.getChatApiKey()   : cfg.getApiKey());
                chatCfg.setBaseUrl(StringUtils.hasText(cfg.getChatBaseUrl()) ? cfg.getChatBaseUrl()  : cfg.getBaseUrl());
                chatCfg.setModelName(StringUtils.hasText(cfg.getChatModelName()) ? cfg.getChatModelName() : cfg.getModelName());
                chatCfg.setTemperature(cfg.getTemperature());
                chatCfg.setProvider(cfg.getProvider());
                return chatCfg;
            }
        }
        // 降级：使用 yml 静态配置
        com.farmland.intel.entity.AiConfig fallback = new com.farmland.intel.entity.AiConfig();
        fallback.setApiKey(fallbackApiKey);
        fallback.setBaseUrl(fallbackApiUrl);
        fallback.setModelName(fallbackModel);
        fallback.setTemperature(new java.math.BigDecimal("0.42"));
        return fallback;
    }

    /** Service 在异常或映射问题时可能返回 null，统一避免 NPE（曾导致 /api/agent/plan 500）。 */
    private List<Statistic> listFarmsOrEmpty() {
        try {
            if (statisticService == null) {
                return Collections.emptyList();
            }
            List<Statistic> farms = statisticService.list();
            return farms != null ? farms : Collections.emptyList();
        } catch (Exception e) {
            log.warn("读取农田列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // agentTemperature / agentTopP 已由 ChatModelFactory 管理

    @Autowired(required = false)
    private IOneNetService oneNetService;

    @Autowired
    private IStatisticService statisticService;
    
    @Autowired(required = false)
    private ISalesService salesService;
    
    @Autowired(required = false)
    private IPurchaseService purchaseService;
    
    @Autowired(required = false)
    private IInventoryService inventoryService;
    
    @Autowired(required = false)
    private IUserService userService;
    
    @Autowired(required = false)
    private IRoleService roleService;
    
    @Autowired(required = false)
    private INoticeService noticeService;
    
    @Autowired(required = false)
    private IOnlineSaleService onlineSaleService;
    
    @Autowired
    private ModelCallLogger modelCallLogger;

    @Autowired(required = false)
    private IAgentUserMemoryService agentUserMemoryService;

    @Autowired(required = false)
    private IAgentDecisionChainService decisionChainService;

    @Autowired(required = false)
    private IKnowledgeService knowledgeService;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private AgentTools agentTools;
    
    /** 近期对话截断长度 */
    private static final int MAX_HISTORY_CONTENT_CHARS = 1200;

    /** 保存决策链步骤到数据库 */
    private void saveDecisionChain(DecisionChainContext ctx) {
        if (ctx == null || decisionChainService == null || ctx.getSteps().isEmpty()) {
            DecisionChainContext.clear();
            return;
        }
        try {
            decisionChainService.saveSteps(ctx.getSteps());
            ctx.getSteps().clear();
        } catch (Exception e) {
            log.warn("保存决策链失败: {}", e.getMessage());
        } finally {
            DecisionChainContext.clear();
        }
    }

    /**
     * 兼容旧调用：无用户上下文时不注入记忆。
     */
    public AgentPlan buildPlan(String userQuestion) {
        return buildPlan(null, userQuestion, null);
    }

    /**
     * 两参数：无多轮会话上下文。
     */
    public AgentPlan buildPlan(Integer userId, String userQuestion) {
        return buildPlan(userId, userQuestion, null);
    }

    /**
     * 使用 Function Calling 构建智能计划
     * AI 可以主动调用工具获取实时数据，然后基于数据做出智能判断
     *
     * @param userId 当前登录用户，用于读取/写入 agent_user_memory；可为 null
     * @param chatHistory 前端传来的近期多轮对话，role 为 user/assistant，不含本轮最后一条 user 的重复时可由前端控制
     */
    public AgentPlan buildPlan(Integer userId, String userQuestion, List<Map<String, Object>> chatHistory) {
        AgentPlan plan = new AgentPlan();
        if (!StringUtils.hasText(userQuestion)) {
            plan.setAdvice("请输入问题或指令。");
            return plan;
        }

        com.farmland.intel.entity.AiConfig aiCfg = resolveAiConfig(userId);
        log.debug("API Key 检查: {}", StringUtils.hasText(aiCfg.getApiKey()) ? "已配置" : "未配置");
        if (!StringUtils.hasText(aiCfg.getApiKey())) {
            log.warn("AI API Key 未配置，使用规则兜底计划");
            return fallbackPlan(userId, userQuestion);
        }

        // 显式写偏好与是否走通义无关：先落库，避免模型把「备注一下」答成「听不懂」且未写入偏好
        AgentPlan explicitMemory = tryPersistExplicitMemoryPreference(userId, userQuestion);
        if (explicitMemory != null) {
            return explicitMemory;
        }

        // 创建决策链上下文
        DecisionChainContext chainCtx = DecisionChainContext.create(userId, "user_chat", userQuestion);
        chainCtx.setModelName(aiCfg.getModelName());

        String memorySnippet = null;
        if (userId != null && agentUserMemoryService != null) {
            try {
                memorySnippet = agentUserMemoryService.buildPromptSnippet(userId);
            } catch (Exception e) {
                log.warn("读取用户 Agent 记忆失败，忽略: {}", e.getMessage());
            }
        }

        try {
            log.debug("开始调用 AI Function Calling，provider={}", aiCfg.getProvider());
            // 使用 Function Calling 进行多轮对话
            String result = callQwenWithFunctionCalling(userQuestion, memorySnippet, chatHistory, aiCfg);
            log.debug("AI 返回结果: {}", result != null ? "有内容" : "空结果");

            if (StringUtils.hasText(result)) {
                AgentPlan parsed = parsePlan(result);
                if (parsed != null && parsed.getAdvice() != null) {
                    String advice = parsed.getAdvice();
                    log.debug("解析成功，advice 长度: {}", advice.length());
                    // 仅当 advice 实质为空时才走兜底；不再用「长度<50 / 含我将」等规则误判，
                    // 否则「你是什么模型」等短答会被扔掉，用户会以为没用上 Qwen。
                    if (!StringUtils.hasText(advice.trim())) {
                        log.debug("advice 为空，使用兜底逻辑");
                        chainCtx.recordStep("final_answer", "advice为空，使用兜底逻辑", null, null);
                        saveDecisionChain(chainCtx);
                        return fallbackPlan(userId, userQuestion);
                    }
                    // 对话记忆已由 Spring AI ChatMemory Advisor 自动处理
                    // 记录最终回答到决策链
                    chainCtx.recordStep("final_answer", advice, null, null);
                    saveDecisionChain(chainCtx);
                    // 设置决策链 ID，供前端查看推理过程
                    parsed.setChainId(chainCtx.getChainId());
                    return parsed;
                } else {
                    log.warn("解析失败，parsed 为空");
                }
            } else {
                log.warn("AI 返回空结果");
            }
        } catch (Exception e) {
            log.error("调用 AI 生成计划失败，使用兜底逻辑", e);
        } finally {
            // 确保决策链被保存（兜底）
            saveDecisionChain(chainCtx);
        }

        log.debug("使用兜底逻辑");
        return fallbackPlan(userId, userQuestion);
    }
    
    /**
     * 四参数入口：支持 mode 路由。
     *   mode = "agent"（默认）→ 主推理模型 + Function Calling
     *   mode = "chat"         → 对话模型，无工具，轻量快速
     */
    public AgentPlan buildPlan(Integer userId, String userQuestion,
                               List<Map<String, Object>> chatHistory, String mode) {
        if ("chat".equals(mode)) {
            return buildChatPlan(userId, userQuestion, chatHistory);
        }
        return buildPlan(userId, userQuestion, chatHistory);
    }

    /**
     * 五参数入口：支持指定触发来源（用于自主巡检等场景）。
     */
    public AgentPlan buildPlan(Integer userId, String userQuestion,
                               List<Map<String, Object>> chatHistory, String mode,
                               String triggerSource) {
        if ("chat".equals(mode)) {
            return buildChatPlan(userId, userQuestion, chatHistory);
        }
        return buildPlanWithTriggerSource(userId, userQuestion, chatHistory, triggerSource);
    }

    /**
     * 内部方法：支持指定触发来源的 buildPlan
     */
    private AgentPlan buildPlanWithTriggerSource(Integer userId, String userQuestion,
                                                  List<Map<String, Object>> chatHistory,
                                                  String triggerSource) {
        AgentPlan plan = new AgentPlan();
        if (!StringUtils.hasText(userQuestion)) {
            plan.setAdvice("请输入问题或指令。");
            return plan;
        }

        // 自主巡检场景: fallbackPlan 是 1000 行 if-else 关键词匹配,只为对话场景设计,
        // 拿巡检 prompt 进去会误判出"为您打开监控界面"这种污染巡检报告的对话话术。
        // 因此该场景下 LLM 不可用时宁可返回 null(=巡检报告跳过),也不污染数据。
        boolean skipFallback = "auto_patrol".equals(triggerSource);

        com.farmland.intel.entity.AiConfig aiCfg = resolveAiConfig(userId);
        if (!StringUtils.hasText(aiCfg.getApiKey())) {
            if (skipFallback) {
                log.debug("[auto_patrol] AI Key 未配置,跳过 AI 综合分析(不走 fallback 避免污染巡检报告)");
                return null;
            }
            return fallbackPlan(userId, userQuestion);
        }

        String memorySnippet = null;
        if (userId != null && agentUserMemoryService != null) {
            try {
                memorySnippet = agentUserMemoryService.buildPromptSnippet(userId);
            } catch (Exception e) {
                log.warn("读取用户 Agent 记忆失败，忽略: {}", e.getMessage());
            }
        }

        // 创建决策链上下文（使用指定的触发来源）
        DecisionChainContext chainCtx = DecisionChainContext.create(userId, triggerSource, userQuestion);
        chainCtx.setModelName(aiCfg.getModelName());

        try {
            String result = callQwenWithFunctionCalling(userQuestion, memorySnippet, chatHistory, aiCfg);
            if (StringUtils.hasText(result)) {
                AgentPlan parsed = parsePlan(result);
                if (parsed != null && parsed.getAdvice() != null && StringUtils.hasText(parsed.getAdvice().trim())) {
                    chainCtx.recordStep("final_answer", parsed.getAdvice(), null, null);
                    parsed.setChainId(chainCtx.getChainId());
                    saveDecisionChain(chainCtx);
                    return parsed;
                }
            }
        } catch (Exception e) {
            log.error("调用 AI 生成计划失败", e);
        } finally {
            saveDecisionChain(chainCtx);
        }
        if (skipFallback) {
            log.debug("[auto_patrol] AI 调用失败,跳过(不走 fallback 避免污染巡检报告)");
            return null;
        }
        return fallbackPlan(userId, userQuestion);
    }

    /**
     * 对话模式：使用 Spring AI ChatClient，不挂载工具。
     * 适合普通问答、系统使用说明等不需要实时数据查询的场景。
     */
    private AgentPlan buildChatPlan(Integer userId, String userQuestion,
                                    List<Map<String, Object>> chatHistory) {
        AgentPlan plan = new AgentPlan();
        plan.setActions(new ArrayList<>());

        if (!StringUtils.hasText(userQuestion)) {
            plan.setAdvice("请输入问题。");
            return plan;
        }

        com.farmland.intel.entity.AiConfig aiCfg = resolveChatConfig(userId);
        log.debug("对话模型: model={}, url={}", aiCfg.getModelName(), aiCfg.getBaseUrl());

        if (!StringUtils.hasText(aiCfg.getApiKey())) {
            plan.setAdvice("AI API Key 未配置，请在「个人中心 → AI 模型配置」中填写。");
            return plan;
        }

        try {
            ChatClient client = chatModelFactory.getChatClientForChatMode(aiCfg);

            List<Message> messages = new java.util.ArrayList<>();
            messages.add(new SystemMessage("你是帮帮农智慧农业平台的 AI 助手，简洁、友好地回答用户问题。"));
            if (chatHistory != null) {
                for (Map<String, Object> turn : chatHistory) {
                    String role = String.valueOf(turn.getOrDefault("role", ""));
                    String content = String.valueOf(turn.getOrDefault("content", ""));
                    if (!StringUtils.hasText(content) || content.length() > MAX_HISTORY_CONTENT_CHARS) continue;
                    if ("user".equals(role)) {
                        messages.add(new UserMessage(content));
                    } else if ("assistant".equals(role)) {
                        messages.add(new AssistantMessage(content));
                    }
                }
            }
            messages.add(new UserMessage(userQuestion));

            String conversationId = "chat:" + (aiCfg.getUserId() != null ? aiCfg.getUserId() : "anonymous");
            String content = client.prompt()
                    .messages(messages)
                    .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
                    .call()
                    .content();

            if (StringUtils.hasText(content)) {
                plan.setAdvice(content.trim());
                modelCallLogger.logModelCall(aiCfg.getModelName(), userQuestion,
                        new JSONObject().put("content", content), true, 0);
                // 对话记忆已由 Spring AI ChatMemory Advisor 自动处理
                return plan;
            }
        } catch (Exception e) {
            log.error("对话模型调用异常", e);
        }

        plan.setAdvice("抱歉，AI 助手暂时无法响应，请稍后重试。");
        return plan;
    }

    /**
     * 使用 Spring AI ChatClient + @Tool 进行 Function Calling。
     * 框架自动处理多轮工具调用循环，替代手动 JSON 工具定义和分发。
     */
    private String callQwenWithFunctionCalling(String userQuestion, String memorySnippet,
                                                 List<Map<String, Object>> chatHistory,
                                                 com.farmland.intel.entity.AiConfig aiCfg) {
        DecisionChainContext chainCtx = DecisionChainContext.get();
        if (chainCtx != null) {
            chainCtx.recordStep("thinking", "开始 Function Calling 推理，用户问题: " + userQuestion, null, 0);
        }

        try {
            ChatClient client = chatModelFactory.getChatClient(aiCfg);

            // 构建消息列表
            List<Message> messages = new java.util.ArrayList<>();
            messages.add(new SystemMessage(buildSystemPrompt(memorySnippet)));
            // 插入历史对话
            if (chatHistory != null) {
                for (Map<String, Object> turn : chatHistory) {
                    String role = String.valueOf(turn.getOrDefault("role", ""));
                    String content = String.valueOf(turn.getOrDefault("content", ""));
                    if (!StringUtils.hasText(content) || content.length() > MAX_HISTORY_CONTENT_CHARS) continue;
                    if ("user".equals(role)) {
                        messages.add(new UserMessage(content));
                    } else if ("assistant".equals(role)) {
                        messages.add(new AssistantMessage(content));
                    }
                }
            }
            messages.add(new UserMessage(userQuestion));

            // Spring AI 自动处理 FC 循环：发送工具定义 → 解析 tool_calls → 执行 @Tool → 回传结果 → 重复
            String conversationId = "agent:" + (aiCfg.getUserId() != null ? aiCfg.getUserId() : "anonymous");
            String response = client.prompt()
                    .messages(messages)
                    .tools(agentTools)
                    .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
                    .call()
                    .content();

            log.debug("AI 最终回答: {}", response);

            // 记录日志
            modelCallLogger.logModelCall(aiCfg.getModelName(), userQuestion,
                    new JSONObject().put("content", response), true, 0);

            return cleanJsonContent(response);
        } catch (Exception e) {
            log.error("Spring AI ChatClient 调用失败，回退到兜底逻辑", e);
            modelCallLogger.logApiError(aiCfg.getModelName(), userQuestion, "SPRING_AI_ERROR", e.getMessage());
            return null;
        }
    }


    
    
    
    
    
    
    
    /**
     * 获取利润分析数据（销售收入 - 采购成本 = 毛利润）
     */
    private String getProfitAnalysis() {
        JSONObject result = new JSONObject();
        
        try {
            // 计算销售总收入
            BigDecimal totalIncome = BigDecimal.ZERO;
            int salesCount = 0;
            if (salesService != null) {
                List<Sales> salesList = salesService.list();
                salesCount = salesList.size();
                for (Sales sale : salesList) {
                    if (sale.getPrice() != null && sale.getNumber() != null) {
                        totalIncome = totalIncome.add(sale.getPrice().multiply(new BigDecimal(sale.getNumber())));
                    }
                }
            }
            
            // 计算采购总成本
            BigDecimal totalCost = BigDecimal.ZERO;
            int purchaseCount = 0;
            if (purchaseService != null) {
                List<Purchase> purchaseList = purchaseService.list();
                purchaseCount = purchaseList.size();
                for (Purchase purchase : purchaseList) {
                    if (purchase.getPrice() != null && purchase.getNumber() != null) {
                        totalCost = totalCost.add(purchase.getPrice().multiply(new BigDecimal(purchase.getNumber())));
                    }
                }
            }
            
            // 计算毛利润
            BigDecimal grossProfit = totalIncome.subtract(totalCost);
            
            // 计算利润率
            BigDecimal profitRate = BigDecimal.ZERO;
            if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                profitRate = grossProfit.multiply(new BigDecimal(100)).divide(totalIncome, 2, java.math.RoundingMode.HALF_UP);
            }
            
            result.put("total_income", totalIncome);
            result.put("total_cost", totalCost);
            result.put("gross_profit", grossProfit);
            result.put("profit_rate", profitRate.toString() + "%");
            result.put("sales_count", salesCount);
            result.put("purchase_count", purchaseCount);
            result.put("is_profitable", grossProfit.compareTo(BigDecimal.ZERO) > 0);
            
        } catch (Exception e) {
            result.put("error", "获取利润分析失败: " + e.getMessage());
        }
        
        return result.toString();
    }
    
    
    /**
     * 构建系统提示词 - 增强经营分析框架 v2.0
     */
    private String buildSystemPrompt(String memorySnippet) {
        String base = "你是一个智能农业 AI Agent，具有数据分析和自主执行能力。\n\n" +
               "【核心原则 - 必须遵守！】\n" +
               "0. 紧扣用户本轮问题作答，避免答非所问；若用户只是在问数值或情况，优先用工具取数后在 advice 中说明。\n" +
               "1. 禁止使用 navigate 跳转页面！用户问数据问题时，必须调用工具获取数据并在 advice 中回答\n" +
               "2. 🎯 理解用户真实意图：\n" +
               "   - \"农田环境怎么样\" = 调用 get_all_farms 查询数据\n" +
               "   - \"帮我采购XX\" = 调用 create_purchase_order 创建订单\n" +
               "   - \"库存情况\" = 调用 get_inventory_data 查询库存\n" +
               "   - 只有用户明确说\"打开XX页面\"、\"跳转到XX\"时才能使用 navigate\n" +
               "3. 💪 主动执行操作：发现问题时自动解决，不要只给建议\n\n" +
               "【工作流程】\n" +
               "步骤1：分析用户意图 → 是查询数据？执行操作？还是跳转页面？\n" +
               "步骤2：调用工具获取数据或执行操作\n" +
               "步骤3：在 advice 中详细说明结果和建议\n\n" +
               "【查询工具】\n" +
               "• get_all_farms - 查询所有农田环境数据（温湿度、光照等）\n" +
               "• get_environment_data - 查询环境监测汇总\n" +
               "• get_sales_data - 查询销售收入\n" +
               "• get_purchase_data - 查询采购成本\n" +
               "• get_inventory_data - 查询库存物资\n" +
               "• get_profit_analysis - 利润分析\n" +
               "• get_business_health_score - 经营健康度评分\n" +
               "• get_comprehensive_report - 综合分析报告\n\n" +
               "【执行工具】\n" +
               "• control_irrigation(action='on'/'off', farm_name) - 控制灌溉\n" +
               "• control_led(action='on'/'off', farm_name) - 控制补光灯\n" +
               "• create_purchase_order(product, number, provider, price) - 创建采购订单\n" +
               "• create_sales_order(product, number, buyer, price) - 创建销售订单\n" +
               "• update_inventory(product, change, reason) - 更新库存\n" +
               "• send_notification(title, content, level) - 发送通知\n\n" +
               "【意图识别示例】\n" +
               "用户说：\"农田环境怎么样\"\n" +
               "正确：调用 get_all_farms，返回环境数据和分析\n" +
               "错误：返回 navigate 跳转到环境监测页面\n\n" +
               "用户说：\"帮我采购50公斤有机肥料，供应商是绿源农资，单价15元\"\n" +
               "正确：调用 create_purchase_order(product='有机肥料', number=50, provider='绿源农资', price=15)\n" +
               "错误：只说\"建议您去采购页面创建订单\"\n\n" +
               "用户说：\"打开环境监测页面\"\n" +
               "正确：返回 navigate 跳转\n" +
               "错误：调用 get_environment_data\n\n" +
               "用户说：\"你能做什么\" / \"你可以做什么\"\n" +
               "正确：在 advice 中直接列出助手能力（查田、浇水、经营、跳转等），不要返回无法理解、不要只给空 actions\n" +
               "错误：说没听懂或让用户换一个问题\n\n" +
               "【自动执行规则】\n" +
               "1. 环境异常 → 自动控制设备\n" +
               "   - 土壤湿度 < 30% → control_irrigation(action='on')\n" +
               "   - 光照 < 1000lux → control_led(action='on')\n" +
               "2. 库存不足 → 自动创建采购订单\n" +
               "3. 用户明确要求操作 → 立即执行\n\n" +
               "【输出格式】JSON：\n" +
               "{\n" +
               "  \"advice\": \"详细说明：①调用了什么工具 ②获取的数据 ③分析结果 ④执行的操作（如果有）⑤建议\",\n" +
               "  \"actions\": []  // 通常为空，因为已通过工具执行\n" +
               "}\n\n" +
               "【重要提醒】\n" +
               "- 用户问数据问题 → 调用工具查询，在 advice 中详细回答，不要用 navigate\n" +
               "- 用户要求操作 → 调用执行工具，在 advice 中说明结果\n" +
               "- 只有用户明确说\"打开XX页面\" → 才用 navigate\n" +
               "- advice 必须包含具体数据和分析，不要只说\"正在跳转\"\n\n" +

               "【主动分析触发规则】\n" +
               "- 用户问候 (你好) → 调用 get_system_overview 汇报当前状态\n" +
               "- 农场怎么样/最近情况/帮我看看 → 调用 get_comprehensive_report\n" +
               "- 赚钱吗/经营怎么样 → 调用 get_business_health_score\n" +
               "- 有什么问题/需要注意什么 → 调用 get_comprehensive_report 列出问题清单\n\n" +

               "【分析框架】环境健康→资金健康→库存健康→运营健康→风险预警";

        if (StringUtils.hasText(memorySnippet)) {
            return base + "\n\n【用户长期记忆（必须结合使用；若与本轮指令冲突，以本轮明确指令为准）】\n" + memorySnippet.trim();
        }
        return base;
    }

    /**
        
        // 工具1: 获取所有农田数据
        tools.add(buildTool(
            "get_all_farms",
            "获取所有农田的基本信息和环境数据，包括名称、作物、面积、土壤湿度、温度等",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具2: 获取指定农田详情
        tools.add(buildTool(
            "get_farm_detail",
            "获取指定农田的详细信息",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("farm_name", new JSONObject()
                        .put("type", "string")
                        .put("description", "农田名称或关键字")))
                .put("required", new JSONArray().put("farm_name"))
        ));
        
        // 工具3: 获取需要灌溉的农田
        tools.add(buildTool(
            "get_farms_need_irrigation",
            "查询土壤湿度低于阈值、需要灌溉的农田列表",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("threshold", new JSONObject()
                        .put("type", "integer")
                        .put("description", "土壤湿度阈值，低于此值需要灌溉，默认30%")))
                .put("required", new JSONArray())
        ));
        
        // 工具4: 获取设备状态
        tools.add(buildTool(
            "get_device_status",
            "获取 IoT 设备的实时状态，包括水泵、补光灯等",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具5: 获取环境数据
        tools.add(buildTool(
            "get_environment_data",
            "获取所有农田的环境监测数据，包括温度、湿度、光照、CO2等",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具6: 获取销售收入数据
        tools.add(buildTool(
            "get_sales_data",
            "获取农产品销售数据和收入统计，包括今年收入、销售记录等",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具7: 获取采购数据
        tools.add(buildTool(
            "get_purchase_data",
            "获取物资采购数据（投入成本），包括购买的农资、机械设备等的支出记录",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具8: 获取库存数据
        tools.add(buildTool(
            "get_inventory_data",
            "获取仓库库存数据（当前拥有的物资），包括农资、工具、设备的库存数量和预警状态",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具9: 利润分析
        tools.add(buildTool(
            "get_profit_analysis",
            "获取经营利润分析，自动计算：销售总收入 - 采购总成本 = 毛利润，以及利润率",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具10: 用户统计
        tools.add(buildTool(
            "get_user_statistics",
            "获取系统用户统计数据，包括总用户数、角色分布、活跃情况等",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具11: 系统概览
        tools.add(buildTool(
            "get_system_overview",
            "获取系统整体运营概览，包括农田、用户、销售、库存、公告等全局统计",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具12: 在线销售数据
        tools.add(buildTool(
            "get_online_sale_data",
            "获取在线销售平台的农作物销售数据和订单统计",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具13: 经营健康度评分
        tools.add(buildTool(
            "get_business_health_score",
            "综合评估农场经营健康度，从利润率、库存周转、资金流等多维度打分并给出诊断建议",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具14: 趋势分析
        tools.add(buildTool(
            "get_trend_analysis",
            "分析销售、采购、库存的历史趋势，预测未来走向并给出优化建议",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // 工具15: 综合分析报告
        tools.add(buildTool(
            "get_comprehensive_report",
            "生成全面的经营分析报告，整合财务、运营、环境、库存等全部数据的深度洞察",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray())
        ));
        
        // ========== 执行工具（Action Tools）- AI 可以自主操作系统 ==========
        
        // 工具16: 控制灌溉系统
        tools.add(buildTool(
            "control_irrigation",
            "控制农田灌溉系统（水泵）的开关。当检测到土壤湿度过低时可以自动开启灌溉",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("action", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray().put("on").put("off"))
                        .put("description", "操作类型：on=开启灌溉，off=关闭灌溉"))
                    .put("farm_name", new JSONObject()
                        .put("type", "string")
                        .put("description", "目标农田名称（可选）")))
                .put("required", new JSONArray().put("action"))
        ));
        
        // 工具17: 控制补光灯
        tools.add(buildTool(
            "control_led",
            "控制农田补光灯的开关。当检测到光照不足时可以自动开启补光",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("action", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray().put("on").put("off"))
                        .put("description", "操作类型：on=开启补光灯，off=关闭补光灯"))
                    .put("farm_name", new JSONObject()
                        .put("type", "string")
                        .put("description", "目标农田名称（可选）")))
                .put("required", new JSONArray().put("action"))
        ));
        
        // 工具18: 创建采购订单
        tools.add(buildTool(
            "create_purchase_order",
            "创建物资采购订单。当检测到库存不足时，AI可以自动创建采购订单补充物资",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("product", new JSONObject()
                        .put("type", "string")
                        .put("description", "采购物资名称，如：有机肥料、农药、种子等"))
                    .put("number", new JSONObject()
                        .put("type", "integer")
                        .put("description", "采购数量"))
                    .put("provider", new JSONObject()
                        .put("type", "string")
                        .put("description", "供应商名称"))
                    .put("price", new JSONObject()
                        .put("type", "number")
                        .put("description", "单价（元）")))
                .put("required", new JSONArray().put("product").put("number").put("provider").put("price"))
        ));
        
        // 工具19: 创建销售订单
        tools.add(buildTool(
            "create_sales_order",
            "创建农产品销售订单。记录农产品销售信息",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("product", new JSONObject()
                        .put("type", "string")
                        .put("description", "销售产品名称"))
                    .put("number", new JSONObject()
                        .put("type", "integer")
                        .put("description", "销售数量"))
                    .put("buyer", new JSONObject()
                        .put("type", "string")
                        .put("description", "买家名称"))
                    .put("price", new JSONObject()
                        .put("type", "number")
                        .put("description", "单价（元）")))
                .put("required", new JSONArray().put("product").put("number").put("buyer").put("price"))
        ));
        
        // 工具20: 更新库存
        tools.add(buildTool(
            "update_inventory",
            "更新仓库库存数量。可以增加或减少库存",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("product", new JSONObject()
                        .put("type", "string")
                        .put("description", "物资名称"))
                    .put("change", new JSONObject()
                        .put("type", "integer")
                        .put("description", "库存变化量（正数=增加，负数=减少）"))
                    .put("reason", new JSONObject()
                        .put("type", "string")
                        .put("description", "变更原因，如：采购入库、销售出库、损耗等")))
                .put("required", new JSONArray().put("product").put("change").put("reason"))
        ));
        
        // 工具21: 发送系统通知
        tools.add(buildTool(
            "send_notification",
            "向系统用户发送通知消息。当发现重要问题或需要提醒时使用",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("title", new JSONObject()
                        .put("type", "string")
                        .put("description", "通知标题"))
                    .put("content", new JSONObject()
                        .put("type", "string")
                        .put("description", "通知内容"))
                    .put("level", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray().put("info").put("warning").put("urgent"))
                        .put("description", "通知级别：info=普通，warning=警告，urgent=紧急")))
                .put("required", new JSONArray().put("title").put("content").put("level"))
        ));

        // 工具22: 查询农业知识库
        tools.add(buildTool(
            "search_knowledge_base",
            "查询农业知识库，获取作物病害、灌溉策略、施肥指南等专业农业知识。当需要农业专业知识辅助决策时调用。",
            new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("query", new JSONObject()
                        .put("type", "string")
                        .put("description", "搜索关键词或问题描述"))
                    .put("category", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray()
                            .put("crop_disease").put("irrigation")
                            .put("fertilizer").put("pest").put("general"))
                        .put("description", "知识分类过滤（可选）")))
                .put("required", new JSONArray().put("query"))
        ));

        return tools;
    }

    /**
     * 清理 JSON 内容中的 markdown 标记
     */
    private String cleanJsonContent(String content) {
        if (content == null) return null;
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }

    /**
     * 执行已经确认的动作，所有动作都在白名单内。
     */
    public List<AgentActionResult> executeActions(List<AgentAction> actions) {
        List<AgentActionResult> results = new ArrayList<>();
        if (actions == null || actions.isEmpty()) {
            return results;
        }

        for (AgentAction action : actions) {
            AgentActionResult result = new AgentActionResult();
            result.setId(action.getId());
            result.setType(action.getType());
            result.setStatus("skipped");
            result.setMessage("动作类型未处理");

            try {
                switch (action.getType()) {
                    case "navigate":
                        result.setStatus("pending-client");
                        result.setRoute(action.getRoute());
                        result.setMessage("前端已确认，准备跳转");
                        break;
                    case "irrigation_on":
                        if (oneNetService != null && oneNetService.controlPump(true)) {
                            result.setStatus("success");
                            result.setMessage("已开启智能灌溉（水泵）");
                        } else {
                            result.setStatus("failed");
                            result.setMessage("灌溉开启失败，OneNET 不可用或调用异常");
                        }
                        break;
                    case "irrigation_off":
                        if (oneNetService != null && oneNetService.controlPump(false)) {
                            result.setStatus("success");
                            result.setMessage("已关闭智能灌溉（水泵）");
                        } else {
                            result.setStatus("failed");
                            result.setMessage("灌溉关闭失败，OneNET 不可用或调用异常");
                        }
                        break;
                    case "led_on":
                        if (oneNetService != null && oneNetService.controlLed(true)) {
                            result.setStatus("success");
                            result.setMessage("补光灯已开启");
                        } else {
                            result.setStatus("failed");
                            result.setMessage("补光灯开启失败，OneNET 不可用或调用异常");
                        }
                        break;
                    case "led_off":
                        if (oneNetService != null && oneNetService.controlLed(false)) {
                            result.setStatus("success");
                            result.setMessage("补光灯已关闭");
                        } else {
                            result.setStatus("failed");
                            result.setMessage("补光灯关闭失败，OneNET 不可用或调用异常");
                        }
                        break;
                    case "create_farm":
                        // 创建新农田
                        try {
                            Map<String, Object> params = action.getParams();
                            Statistic newFarm = new Statistic();

                            String address = params.getOrDefault("address", "").toString();
                            newFarm.setAddress(address);

                            // 根据地址匹配预设的地理坐标
                            Map<String, Object> geoData = getPresetGeoLocation(address);

                            // 优先使用预设的建议名称，否则使用用户指定的名称
                            String farmName = params.getOrDefault("name", "新农田").toString();
                            if (geoData != null && geoData.get("suggestedName") != null) {
                                farmName = geoData.get("suggestedName").toString();
                            }
                            newFarm.setFarm(farmName);

                            newFarm.setArea(params.getOrDefault("area", "100").toString());
                            newFarm.setCrop(params.getOrDefault("crop", "未种植").toString());

                            // 从地址中提取区县信息
                            String district = extractDistrictFromAddress(address);
                            if (district != null && !district.isEmpty()) {
                                newFarm.setDistrict(district);
                            }

                            // 填充地理坐标
                            if (geoData != null) {
                                newFarm.setCenterLng(new java.math.BigDecimal(geoData.get("centerLng").toString()));
                                newFarm.setCenterLat(new java.math.BigDecimal(geoData.get("centerLat").toString()));
                                newFarm.setCoordinates(geoData.get("coordinates").toString());
                            }

                            newFarm.setState("正常");
                            newFarm.setSoilhumidity(60);
                            newFarm.setAirhumidity(50);
                            newFarm.setTemperature(new java.math.BigDecimal("25.0"));

                            // 设置农田负责人为当前用户
                            try {
                                User currentUser = TokenUtils.getCurrentUser();
                                if (currentUser != null) {
                                    newFarm.setKeeper(currentUser.getUsername());
                                }
                            } catch (Exception ignored) {
                                // 无用户上下文时跳过
                            }
                            
                            statisticService.save(newFarm);
                            result.setStatus("success");
                            result.setMessage("农田【" + newFarm.getFarm() + "】创建成功！ID: " + newFarm.getId());
                            log.debug("Agent创建农田成功: {}", newFarm.getFarm());
                        } catch (Exception e) {
                            result.setStatus("failed");
                            result.setMessage("创建农田失败: " + e.getMessage());
                            log.error("Agent创建农田失败", e);
                        }
                        break;
                    case "delete_farm":
                        // 删除农田（需权限校验：仅负责人或管理员可删除）
                        try {
                            Map<String, Object> params = action.getParams();
                            Object farmIdObj = params.get("farmId");
                            if (farmIdObj == null) {
                                result.setStatus("failed");
                                result.setMessage("未指定要删除的农田ID");
                                break;
                            }
                            Integer farmId = Integer.parseInt(farmIdObj.toString());
                            Statistic farm = statisticService.getById(farmId);
                            if (farm == null) {
                                result.setStatus("failed");
                                result.setMessage("未找到ID为" + farmId + "的农田");
                                break;
                            }

                            // 权限校验：非管理员只能删除自己负责的农田
                            try {
                                User currentUser = TokenUtils.getCurrentUser();
                                if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                                    if (!currentUser.getUsername().equals(farm.getKeeper())) {
                                        result.setStatus("failed");
                                        result.setMessage("无权限删除该农田，仅负责人可操作");
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {
                                // 无用户上下文时跳过权限校验
                            }

                            statisticService.removeById(farmId);
                            result.setStatus("success");
                            result.setMessage("农田【" + farm.getFarm() + "】已删除");
                            log.debug("Agent删除农田成功: ID={}", farmId);
                        } catch (Exception e) {
                            result.setStatus("failed");
                            result.setMessage("删除农田失败: " + e.getMessage());
                        }
                        break;
                    case "drone_spray":
                        // 飞防消杀
                        try {
                            Map<String, Object> params = action.getParams();
                            String farmName = params != null ? params.getOrDefault("farmName", "农田").toString() : "农田";
                            
                            // 模拟启动无人机飞防消杀
                            result.setStatus("success");
                            result.setMessage("已启动【" + farmName + "】的飞防消杀作业，无人机正在执行任务...");
                            log.debug("Agent启动飞防消杀: {}", farmName);
                        } catch (Exception e) {
                            result.setStatus("failed");
                            result.setMessage("飞防消杀启动失败: " + e.getMessage());
                        }
                        break;
                    default:
                        result.setStatus("skipped");
                        result.setMessage("不支持的动作类型: " + action.getType());
                        break;
                }
            } catch (Exception e) {
                log.error("执行动作失败: {}", action.getId(), e);
                result.setStatus("failed");
                result.setMessage("执行异常: " + e.getMessage());
            }

            results.add(result);
        }

        return results;
    }

    private AgentPlan parsePlan(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String base = cleanJsonContent(content.trim());
        AgentPlan plan = parsePlanFromJsonString(base);
        if (plan != null) {
            return plan;
        }
        int i = base.indexOf('{');
        int j = base.lastIndexOf('}');
        if (i >= 0 && j > i) {
            plan = parsePlanFromJsonString(base.substring(i, j + 1));
            if (plan != null) {
                return plan;
            }
        }
        log.warn("解析 Agent 计划失败（整段与截取均无效）: {}",
                base.length() > 600 ? base.substring(0, 600) + "…" : base);
        return null;
    }

    private AgentPlan parsePlanFromJsonString(String json) {
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            AgentPlan plan = new AgentPlan();
            plan.setAdvice(obj.getStr("advice", "好的，我来帮您处理。"));

            List<AgentAction> actions = new ArrayList<>();
            JSONArray arr = obj.getJSONArray("actions");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    AgentAction action = new AgentAction();
                    action.setId(item.getStr("id", "action-" + i));
                    action.setType(item.getStr("type"));
                    action.setTitle(item.getStr("title", "未命名动作"));
                    action.setDescription(item.getStr("description", ""));
                    action.setRoute(item.getStr("route", null));
                    action.setTarget(item.getStr("target", null));
                    action.setRiskLevel(item.getStr("riskLevel", "medium"));
                    if (item.containsKey("params")) {
                        JSONObject p = item.getJSONObject("params");
                        if (p != null && !p.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> pm = JSONUtil.toBean(p, Map.class);
                            action.setParams(pm != null ? new HashMap<>(pm) : new HashMap<>());
                        }
                    }

                    if (isAllowedType(action.getType())) {
                        actions.add(action);
                    } else {
                        log.warn("丢弃非白名单动作: {}", action.getType());
                    }
                }
            }
            plan.setActions(actions);
            return plan;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAllowedType(String type) {
        return Arrays.asList("navigate", "irrigation_on", "irrigation_off", "led_on", "led_off", 
                            "create_farm", "delete_farm", "update_farm", "drone_spray")
                .contains(type);
    }

    /**
     * 判断用户是否在问「环境/土壤湿度等数值」，以便与兜底逻辑里「含湿度就跳转大屏」区分。
     */
    private static boolean isEnvOrSoilDataQuestion(String lower) {
        if (lower == null || lower.isEmpty()) {
            return false;
        }
        if (lower.contains("土壤湿度") || lower.contains("墒情")) {
            return true;
        }
        if (lower.contains("湿度")
                && (lower.contains("怎么样") || lower.contains("多少") || lower.contains("如何")
                || lower.contains("看下") || lower.contains("看看") || lower.contains("查看") || lower.contains("查询"))) {
            return true;
        }
        if ((lower.contains("环境") || lower.contains("监测"))
                && (lower.contains("怎么样") || lower.contains("如何") || lower.contains("多少") || lower.contains("情况"))) {
            return true;
        }
        if ((lower.contains("看看") || lower.contains("去看")) && (lower.contains("湿度") || lower.contains("温度")
                || lower.contains("光照") || lower.contains("墒情") || lower.contains("土壤"))) {
            if (!lower.contains("大屏") && !lower.contains("打开页面")) {
                return true;
            }
        }
        boolean hasFarmOrdinal = java.util.regex.Pattern.compile("[0-9一二三四五六七八九十]+\\s*号").matcher(lower).find();
        if (hasFarmOrdinal && (lower.contains("湿度") || lower.contains("温度") || lower.contains("光照"))) {
            return !lower.contains("打开") && !lower.contains("跳转") && !lower.contains("进入");
        }
        return false;
    }

    /**
     * 用户问「你能做什么」等元问题，应与具体农事指令区分，避免落入末尾「没听懂」话术。
     */
    private static boolean isCapabilityQuestion(String lower, String raw) {
        if (lower == null || lower.isEmpty()) {
            return false;
        }
        if (lower.contains("你可以做什么") || lower.contains("你能做什么") || lower.contains("你会做什么")
                || lower.contains("你能干什么") || lower.contains("你能干嘛") || lower.contains("你会干啥")
                || lower.contains("你会干嘛") || lower.contains("你可以干嘛") || lower.contains("你可以干啥")
                || lower.contains("有什么功能") || lower.contains("哪些功能") || lower.contains("功能有哪些")
                || lower.contains("能帮我什么") || lower.contains("可以帮我什么") || lower.contains("会帮我什么")
                || lower.contains("自我介绍") || lower.contains("介绍你的功能") || lower.contains("你有什么用")
                || lower.contains("使用说明") || lower.contains("怎么用你") || lower.contains("怎么用助手")
                || lower.contains("你会哪些") || lower.contains("你可以哪些") || lower.contains("你能哪些")) {
            return true;
        }
        String trimmed = raw.trim();
        if (java.util.regex.Pattern.compile("^(你可以|你能|你会)(做|干|弄)?(什么|啥)[吧吗呀呢啊？?！!。.\\s]*$")
                .matcher(trimmed).matches()) {
            return true;
        }
        return false;
    }

    private static String buildCapabilityIntro() {
        return "我是您的智能农情助手，可以帮您做这些事：\n\n"
                + "• 农田与环境：查各块地的土壤湿度、温度、光照；哪块地偏干需要浇水；环境概况一句话总结。\n"
                + "• 设备与作业：按田号开启/关闭灌溉、补光灯；飞防消杀（无人机打药）等（会生成可确认的操作按钮）。\n"
                + "• 经营与物资：销售收入、采购、库存、利润与健康度等经营数据查询与分析。\n"
                + "• 页面跳转：说「打开××页面」「带我去××」可跳转到地图、果蔬检测、大屏、库存等对应界面。\n\n"
                + "您可以直接用自然话提问，例如：「哪块地需要浇水」「今年经营怎么样」「帮我给2号田浇水」「打开农田地图」。";
    }

    private AgentPlan fallbackPlan(Integer userId, String userQuestion) {
        AgentPlan plan = new AgentPlan();
        List<AgentAction> actions = new ArrayList<>();

        if (userQuestion == null || !StringUtils.hasText(userQuestion.trim())) {
            plan.setAdvice("请输入问题或指令。");
            plan.setActions(actions);
            return plan;
        }

        String raw = userQuestion.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // 身份 / 模型类问题：兜底规则也能明确回答（与是否走通义无关，避免固定「没听懂」话术）
        if (raw.contains("什么模型") || raw.contains("你是谁") || raw.contains("哪个模型")
                || (raw.contains("你") && raw.contains("模型")) || lower.contains("qwen")) {
            plan.setAdvice("我是「禾序」智能农业研判助手。对话与推理由阿里通义千问（DashScope，配置项 qwen.model，默认 qwen-max）驱动，"
                    + "并会按需调用您系统里的农田、库存、经营等工具做分析；我本身不是独立聊天 App，更偏向「问农事、给方案、可执行时出动作」。");
            plan.setActions(actions);
            return plan;
        }

        if (isCapabilityQuestion(lower, raw)) {
            plan.setAdvice(buildCapabilityIntro());
            plan.setActions(actions);
            return plan;
        }

        AgentPlan explicitMemory = tryPersistExplicitMemoryPreference(userId, raw);
        if (explicitMemory != null) {
            return explicitMemory;
        }

        // 模糊「农事/种植建议」：兜底时结合已写入的偏好，避免只剩「没听懂」
        if (isVagueFarmingAdviceQuestion(lower)) {
            plan.setAdvice(buildVagueFarmingAdvice(userId));
            if (userId != null && agentUserMemoryService != null) {
                try {
                    agentUserMemoryService.appendConversationTurn(userId, raw, plan.getAdvice());
                } catch (Exception e) {
                    log.warn("农事建议兜底写入对话记忆失败: {}", e.getMessage());
                }
            }
            plan.setActions(actions);
            return plan;
        }

        // 优先匹配：指定田地的操作（灌溉、飞防消杀等）
        String targetFarmName = extractFarmName(userQuestion);
        if (targetFarmName != null) {
            List<Statistic> farms = listFarmsOrEmpty();
            Statistic targetFarm = null;
            
            for (Statistic farm : farms) {
                if (farm.getFarm() != null && 
                    (farm.getFarm().contains(targetFarmName) || matchFarmNumber(farm.getFarm(), targetFarmName))) {
                    targetFarm = farm;
                    break;
                }
            }
            
            if (targetFarm != null) {
                // 飞防消杀
                if (lower.contains("飞防") || lower.contains("消杀") || lower.contains("打药") || lower.contains("喷药") || lower.contains("除虫")) {
                    StringBuilder advice = new StringBuilder();
                    advice.append("好的，即将为【").append(targetFarm.getFarm()).append("】执行飞防消杀。\n\n");
                    advice.append("作物：").append(targetFarm.getCrop() != null ? targetFarm.getCrop() : "未知").append("\n");
                    advice.append("面积：").append(targetFarm.getArea() != null ? targetFarm.getArea() : "未知").append("亩\n\n");
                    advice.append("确认后将启动无人机进行飞防作业。");
                    
                    plan.setAdvice(advice.toString());
                    
                    // 添加导航到该田地
                    AgentAction navAction = new AgentAction();
                    navAction.setId("nav_farm_" + System.currentTimeMillis());
                    navAction.setType("navigate");
                    navAction.setTitle("跳转到" + targetFarm.getFarm());
                    navAction.setDescription("在3D地图中定位到" + targetFarm.getFarm());
                    navAction.setRoute("/farmmap3d?farmId=" + targetFarm.getId());
                    navAction.setRiskLevel("low");
                    actions.add(navAction);
                    
                    // 添加飞防消杀动作
                    AgentAction sprayAction = new AgentAction();
                    sprayAction.setId("drone_spray_" + System.currentTimeMillis());
                    sprayAction.setType("drone_spray");
                    sprayAction.setTitle("飞防消杀：" + targetFarm.getFarm());
                    sprayAction.setDescription("为【" + targetFarm.getFarm() + "】启动无人机飞防消杀");
                    sprayAction.setRiskLevel("medium");
                    Map<String, Object> params = new HashMap<>();
                    params.put("farmId", targetFarm.getId());
                    params.put("farmName", targetFarm.getFarm());
                    sprayAction.setParams(params);
                    actions.add(sprayAction);
                    
                    plan.setActions(actions);
                    return plan;
                }
                
                // 灌溉/浇水
                if (lower.contains("灌溉") || lower.contains("浇水") || lower.contains("浇")) {
                    StringBuilder advice = new StringBuilder();
                    advice.append("好的，即将为【").append(targetFarm.getFarm()).append("】开启智能灌溉。\n\n");
                    advice.append("当前土壤湿度：").append(targetFarm.getSoilhumidity() != null ? targetFarm.getSoilhumidity() + "%" : "未知").append("\n\n");
                    advice.append("确认后将启动灌溉系统。");
                    
                    plan.setAdvice(advice.toString());
                    
                    // 添加导航到该田地
                    AgentAction navAction = new AgentAction();
                    navAction.setId("nav_farm_" + System.currentTimeMillis());
                    navAction.setType("navigate");
                    navAction.setTitle("跳转到" + targetFarm.getFarm());
                    navAction.setDescription("在3D地图中定位到" + targetFarm.getFarm());
                    navAction.setRoute("/farmmap3d?farmId=" + targetFarm.getId());
                    navAction.setRiskLevel("low");
                    actions.add(navAction);
                    
                    // 添加灌溉动作
                    AgentAction irrigation = new AgentAction();
                    irrigation.setId("irrigation_on_" + System.currentTimeMillis());
                    irrigation.setType("irrigation_on");
                    irrigation.setTitle("智能灌溉：" + targetFarm.getFarm());
                    irrigation.setDescription("为【" + targetFarm.getFarm() + "】开启智能灌溉");
                    irrigation.setRiskLevel("medium");
                    Map<String, Object> params = new HashMap<>();
                    params.put("farmId", targetFarm.getId());
                    params.put("farmName", targetFarm.getFarm());
                    irrigation.setParams(params);
                    actions.add(irrigation);
                    
                    plan.setActions(actions);
                    return plan;
                }

                // 查询某块田的土壤湿度 / 环境数据（避免误命中下方「湿度→跳转大屏」兜底）
                if (isEnvOrSoilDataQuestion(lower)
                        && !lower.contains("浇水") && !lower.contains("灌溉") && !lower.contains("浇地")
                        && !lower.contains("飞防") && !lower.contains("消杀") && !lower.contains("打药")) {
                    StringBuilder advice = new StringBuilder();
                    advice.append("【").append(targetFarm.getFarm()).append("】当前数据（系统农田统计）：\n\n");
                    if (targetFarm.getSoilhumidity() != null) {
                        advice.append("• 土壤湿度：").append(targetFarm.getSoilhumidity()).append("%");
                        int h = targetFarm.getSoilhumidity();
                        advice.append(h < 30 ? "（低于常见灌溉阈值 30%，偏干）\n" : "（在常见阈值 30% 以上）\n");
                    } else {
                        advice.append("• 土壤湿度：暂无数据\n");
                    }
                    if (targetFarm.getTemperature() != null) {
                        advice.append("• 温度：").append(targetFarm.getTemperature()).append("℃\n");
                    }
                    if (StringUtils.hasText(targetFarm.getCrop())) {
                        advice.append("• 作物：").append(targetFarm.getCrop()).append("\n");
                    }
                    advice.append("\n说明：以上为业务库中的快照。若需看实时传感器曲线，可从侧栏进入「环境监测 / Aether」页面。");
                    plan.setAdvice(advice.toString());
                    plan.setActions(actions);
                    return plan;
                }
            }
        }
        
        // 优先匹配：查询需要浇水的农田（必须在导航之前）
        if ((lower.contains("需要浇水") || lower.contains("需要灌溉") || lower.contains("要浇水") || lower.contains("要灌溉") ||
             lower.contains("缺水") || lower.contains("干旱")) ||
            ((lower.contains("浇水") || lower.contains("灌溉")) && (lower.contains("哪") || lower.contains("有没有") || lower.contains("看看")))) {
            
            List<Statistic> farms = listFarmsOrEmpty();
            List<Statistic> needWater = farms.stream()
                .filter(f -> f.getSoilhumidity() != null && f.getSoilhumidity() < 30)
                .collect(Collectors.toList());
            
            StringBuilder advice = new StringBuilder();
            if (!needWater.isEmpty()) {
                advice.append("检查了一下，有 ").append(needWater.size()).append(" 块地需要浇水：\n\n");
                for (Statistic farm : needWater) {
                    advice.append(farm.getFarm()).append("：土壤湿度 ")
                          .append(farm.getSoilhumidity()).append("%，有点干了\n");
                }
                advice.append("\n要不要帮您开启灌溉？");
                
                // 添加灌溉动作
                AgentAction irrigation = new AgentAction();
                irrigation.setId("irrigation_on_" + System.currentTimeMillis());
                irrigation.setType("irrigation_on");
                irrigation.setTitle("开启灌溉");
                irrigation.setDescription("为缺水农田开启灌溉系统");
                irrigation.setRiskLevel("medium");
                actions.add(irrigation);
            } else {
                advice.append("检查了一下，所有农田的土壤湿度都正常，暂时不需要浇水。");
            }
            
            plan.setAdvice(advice.toString());
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：新建/创建农田（必须在导航之前，因为"帮我去新建"会触发导航）
        if ((lower.contains("新增") || lower.contains("添加") || lower.contains("创建") || lower.contains("新建")) &&
            (lower.contains("农田") || lower.contains("土地") || lower.contains("田地") || lower.contains("地块") || lower.contains("田"))) {
            
            String crop = extractCropName(userQuestion);
            String area = extractArea(userQuestion);
            String address = extractAddress(userQuestion);
            
            // 优先使用预设地点的建议名称
            String farmName = extractNewFarmName(userQuestion);
            if (address != null && !address.isEmpty()) {
                Map<String, Object> geoData = getPresetGeoLocation(address);
                if (geoData != null && geoData.get("suggestedName") != null) {
                    farmName = geoData.get("suggestedName").toString();
                }
            }
            
            StringBuilder advice = new StringBuilder();
            advice.append("好的，即将为您创建新农田。\n\n");
            advice.append("农田名称：").append(farmName).append("\n");
            advice.append("面积：").append(area).append("亩\n");
            if (!"作物".equals(crop)) {
                advice.append("作物：").append(crop).append("\n");
            }
            if (address != null && !address.isEmpty()) {
                advice.append("位置：").append(address).append("\n");
            }
            advice.append("\n确认后将创建该农田。");
            
            plan.setAdvice(advice.toString());
            
            AgentAction createAction = new AgentAction();
            createAction.setId("create_farm_" + System.currentTimeMillis());
            createAction.setType("create_farm");
            createAction.setTitle("创建农田：" + farmName);
            createAction.setDescription("新建一块名为【" + farmName + "】的农田");
            createAction.setRiskLevel("medium");
            
            Map<String, Object> params = new HashMap<>();
            params.put("name", farmName);
            params.put("area", area);
            params.put("crop", crop);
            if (address != null && !address.isEmpty()) {
                params.put("address", address);
            }
            createAction.setParams(params);
            
            actions.add(createAction);
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：导航意图（带我去、打开、跳转等；排除「问湿度/温度数值」以免误跳大屏）
        if (!isEnvOrSoilDataQuestion(lower) && (lower.contains("带我") || lower.contains("打开") || lower.contains("跳转")
                || lower.contains("进入") || lower.contains("带我去") || lower.contains("去打开") || lower.contains("去进入")
                || lower.contains("看看") || lower.contains("查看"))) {
            
            String route = null;
            String title = null;
            
            // 3D地图/3D界面
            if (lower.contains("3d") || lower.contains("三维") || lower.contains("立体")) {
                route = "/farmmap3d";
                title = "3D农田地图";
            }
            // GIS地图/指挥地图
            else if (lower.contains("gis") || lower.contains("指挥") || lower.contains("卫星")) {
                route = "/farm-map-gaode";
                title = "GIS指挥地图";
            }
            // 地图（通用）
            else if (lower.contains("地图") && (lower.contains("田") || lower.contains("地"))) {
                route = "/farm-map-gaode";
                title = "农田地图";
            }
            // 大屏/数据大屏/环境监测
            else if (lower.contains("大屏") || lower.contains("监测") || lower.contains("环境")) {
                route = "/dashbordnew";
                title = "环境监测大屏";
            }
            // 农田管理/田地管理
            else if ((lower.contains("农田") || lower.contains("田地") || lower.contains("地块")) && 
                     (lower.contains("管理") || lower.contains("列表") || lower.contains("看看"))) {
                route = "/farmland";
                title = "农田管理";
            }
            // 销售/订单
            else if (lower.contains("销售") || lower.contains("订单")) {
                route = "/sale";
                title = "销售管理";
            }
            // 采购
            else if (lower.contains("采购") || lower.contains("进货")) {
                route = "/purchase";
                title = "采购管理";
            }
            // 库存
            else if (lower.contains("库存") || lower.contains("仓库")) {
                route = "/inventory";
                title = "库存管理";
            }
            // 果蔬识别（收窄：避免单字「检测」误伤其它问句）
            else if ((lower.contains("识别") || lower.contains("检测"))
                    && (lower.contains("果蔬") || lower.contains("水果") || lower.contains("蔬菜")
                    || lower.contains("病虫害") || lower.contains("病害") || lower.contains("害虫"))) {
                route = "/fruit-detect";
                title = "果蔬识别";
            }
            
            if (route != null) {
                plan.setAdvice("好的，正在为您打开" + title + "...");
                
                AgentAction navAction = new AgentAction();
                navAction.setId("nav_" + System.currentTimeMillis());
                navAction.setType("navigate");
                navAction.setTitle(title);
                navAction.setDescription("打开" + title);
                navAction.setRoute(route);
                navAction.setRiskLevel("low");
                actions.add(navAction);
                
                plan.setActions(actions);
                return plan;
            }
        }
        
        // 优先匹配：简单问候语
        if (lower.matches("^(你好|您好|hi|hello|嗨|哈喽|早上好|下午好|晚上好|早安|晚安|在吗|在不在|你在吗)[!！。？?]*$")) {
            List<Statistic> farms = listFarmsOrEmpty();
            StringBuilder greeting = new StringBuilder();
            greeting.append("你好！我是您的智能农情助手。\n\n");
            greeting.append("目前系统共管理 ").append(farms.size()).append(" 块农田。\n\n");
            greeting.append("我可以帮您查询农田数据、控制设备、分析经营情况等。\n\n");
            greeting.append("请问您需要了解什么？");
            
            plan.setAdvice(greeting.toString());
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：感谢语
        if (lower.matches("^(谢谢|感谢|多谢|谢了|thx|thanks|thank you)[!！。]*$")) {
            plan.setAdvice("不客气！有任何农业问题随时问我 😊");
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：查看作物统计、种了什么、作物最多
        if ((lower.contains("作物") || lower.contains("种植") || lower.contains("种了") || lower.contains("种什么")) &&
            (lower.contains("什么") || lower.contains("哪些") || lower.contains("多少") || lower.contains("最多") || 
             lower.contains("统计") || lower.contains("看看") || lower.contains("查看"))) {
            
            List<Statistic> farms = listFarmsOrEmpty();
            Map<String, Integer> cropCount = new LinkedHashMap<>();
            
            for (Statistic farm : farms) {
                String crop = farm.getCrop();
                if (crop != null && !crop.trim().isEmpty()) {
                    // 处理多作物（逗号分隔）
                    String[] crops = crop.split("[,，]");
                    for (String c : crops) {
                        c = c.trim();
                        if (!c.isEmpty()) {
                            cropCount.put(c, cropCount.getOrDefault(c, 0) + 1);
                        }
                    }
                }
            }
            
            StringBuilder advice = new StringBuilder();
            if (cropCount.isEmpty()) {
                advice.append("目前还没有作物种植记录。");
            } else {
                // 按数量排序
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(cropCount.entrySet());
                sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                
                String topCrop = sorted.get(0).getKey();
                int topCount = sorted.get(0).getValue();
                
                advice.append("目前种植最多的是").append(topCrop).append("，共有 ").append(topCount).append(" 块地。\n\n");
                
                if (sorted.size() > 1) {
                    advice.append("其他作物：");
                    for (int i = 1; i < sorted.size(); i++) {
                        Map.Entry<String, Integer> entry = sorted.get(i);
                        if (i > 1) advice.append("、");
                        advice.append(entry.getKey()).append(" ").append(entry.getValue()).append("块");
                    }
                    advice.append("。\n\n");
                }
                
                advice.append("总共 ").append(farms.size()).append(" 块农田，种植了 ").append(cropCount.size()).append(" 种作物。");
            }
            
            plan.setAdvice(advice.toString());
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：查看农田概况、有多少地、农田情况
        if ((lower.contains("农田") || lower.contains("地块") || lower.contains("土地") || lower.contains("田地")) &&
            (lower.contains("多少") || lower.contains("几块") || lower.contains("情况") || lower.contains("概况") || 
             lower.contains("统计") || lower.contains("看看") || lower.contains("有哪些"))) {
            
            List<Statistic> farms = listFarmsOrEmpty();
            
            // 计算总面积
            double totalArea = farms.stream()
                .filter(f -> f.getArea() != null)
                .mapToDouble(f -> {
                    try { return Double.parseDouble(f.getArea()); } 
                    catch (Exception e) { return 0; }
                })
                .sum();
            
            StringBuilder advice = new StringBuilder();
            advice.append("您目前共有 ").append(farms.size()).append(" 块农田，总面积约 ").append(String.format("%.1f", totalArea)).append(" 亩。\n\n");
            
            if (!farms.isEmpty()) {
                int showCount = Math.min(5, farms.size());
                for (int i = 0; i < showCount; i++) {
                    Statistic farm = farms.get(i);
                    advice.append(farm.getFarm() != null ? farm.getFarm() : "未命名");
                    if (farm.getCrop() != null) {
                        advice.append("，种植").append(farm.getCrop());
                    }
                    if (farm.getArea() != null) {
                        advice.append("，").append(farm.getArea()).append("亩");
                    }
                    advice.append("。\n");
                }
                if (farms.size() > 5) {
                    advice.append("还有其他 ").append(farms.size() - 5).append(" 块农田。\n");
                }
            }
            
            plan.setAdvice(advice.toString());
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：录入位置、标记位置、设置地址
        if ((lower.contains("录入") || lower.contains("标记") || lower.contains("设置") || lower.contains("添加") || lower.contains("修改")) &&
            (lower.contains("位置") || lower.contains("地址") || lower.contains("坐标") || lower.contains("地图"))) {
            
            StringBuilder advice = new StringBuilder();
            advice.append("好的，我来帮您录入农田位置。\n\n");
            advice.append("请到农田管理页面，点击对应农田的【编辑】按钮，然后点击【在地图上标记】来设置位置。\n\n");
            advice.append("或者您可以告诉我要编辑哪块农田，我帮您跳转。");
            
            plan.setAdvice(advice.toString());
            
            // 添加导航动作
            AgentAction navAction = new AgentAction();
            navAction.setId("nav_to_farmland_" + System.currentTimeMillis());
            navAction.setType("navigate");
            navAction.setTitle("农田管理");
            navAction.setDescription("打开农田管理页面进行位置录入");
            navAction.setRoute("/farmland");
            navAction.setRiskLevel("low");
            actions.add(navAction);
            
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：删除农田
        if ((lower.contains("删除") || lower.contains("移除") || lower.contains("去掉")) &&
            (lower.contains("农田") || lower.contains("土地") || lower.contains("田地") || lower.contains("地块"))) {
            
            String targetFarm = extractFarmName(userQuestion);
            List<Statistic> farms = listFarmsOrEmpty();
            
            if (targetFarm != null) {
                Statistic matchedFarm = null;
                for (Statistic farm : farms) {
                    if (farm.getFarm() != null && 
                        (farm.getFarm().contains(targetFarm) || matchFarmNumber(farm.getFarm(), targetFarm))) {
                        matchedFarm = farm;
                        break;
                    }
                }
                
                if (matchedFarm != null) {
                    plan.setAdvice("您确定要删除【" + matchedFarm.getFarm() + "】吗？\n\n此操作不可撤销，该农田的所有数据将被永久删除。");
                    
                    AgentAction deleteAction = new AgentAction();
                    deleteAction.setId("delete_farm_" + matchedFarm.getId());
                    deleteAction.setType("delete_farm");
                    deleteAction.setTitle("删除农田：" + matchedFarm.getFarm());
                    deleteAction.setDescription("永久删除【" + matchedFarm.getFarm() + "】及其所有数据");
                    deleteAction.setRiskLevel("high");
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("farmId", matchedFarm.getId());
                    params.put("farmName", matchedFarm.getFarm());
                    deleteAction.setParams(params);
                    
                    actions.add(deleteAction);
                } else {
                    plan.setAdvice("未找到【" + targetFarm + "】这块农田。\n\n您可以说：删除1号田、删除XX农田 等。");
                }
            } else {
                plan.setAdvice("请告诉我要删除哪块农田，例如：删除1号田、删除测试农田 等。");
            }
            
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：利润、收入、赚钱相关查询
        if (lower.contains("利润") || lower.contains("净利") || lower.contains("盈利") || lower.contains("赚") || 
            lower.contains("可以赚") || lower.contains("能赚") || lower.contains("今年收入") ||
            (lower.contains("收入") && (lower.contains("多少") || lower.contains("支出"))) || 
            (lower.contains("采购") && lower.contains("多少"))) {
            // 计算利润：收入 - 支出
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            
            // 获取销售收入
            if (salesService != null) {
                List<Sales> salesList = salesService.list();
                for (Sales sale : salesList) {
                    if (sale.getPrice() != null && sale.getNumber() != null) {
                        totalIncome = totalIncome.add(sale.getPrice().multiply(new BigDecimal(sale.getNumber())));
                    }
                }
            }
            
            // 获取采购支出
            if (purchaseService != null) {
                List<Purchase> purchaseList = purchaseService.list();
                for (Purchase purchase : purchaseList) {
                    if (purchase.getPrice() != null && purchase.getNumber() != null) {
                        totalCost = totalCost.add(purchase.getPrice().multiply(new BigDecimal(purchase.getNumber())));
                    }
                }
            }
            
            BigDecimal profit = totalIncome.subtract(totalCost);
            StringBuilder advice = new StringBuilder();
            advice.append("根据系统财务数据分析：\n\n");
            advice.append("销售总收入：¥").append(totalIncome.setScale(2, java.math.RoundingMode.HALF_UP)).append("\n");
            advice.append("采购总支出：¥").append(totalCost.setScale(2, java.math.RoundingMode.HALF_UP)).append("\n");
            advice.append("净利润：¥").append(profit.setScale(2, java.math.RoundingMode.HALF_UP)).append("\n\n");
            
            if (profit.compareTo(BigDecimal.ZERO) > 0) {
                advice.append("经营状况良好，处于盈利状态。");
            } else if (profit.compareTo(BigDecimal.ZERO) < 0) {
                advice.append("当前处于亏损状态，建议优化采购成本或提升销售收入。");
            } else {
                advice.append("收支平衡。");
            }
            
            plan.setAdvice(advice.toString());
            plan.setActions(actions);
            return plan;
        }
        
        // 优先匹配：作物种植建议（温度适合种XX吗、能种XX吗）
        if ((lower.contains("适合") || lower.contains("能种") || lower.contains("可以种") || lower.contains("能不能种")) &&
            (lower.contains("种") || lower.contains("植") || lower.contains("养"))) {
            
            // 提取作物名称
            String crop = extractCropName(userQuestion);
            
            // 获取当前环境数据
            List<Statistic> farms = listFarmsOrEmpty();
            if (!farms.isEmpty()) {
                // 计算平均温度和湿度
                double avgTemp = farms.stream()
                    .filter(f -> f.getTemperature() != null)
                    .mapToDouble(f -> f.getTemperature().doubleValue())
                    .average()
                    .orElse(25.0);
                double avgHumidity = farms.stream()
                    .filter(f -> f.getSoilhumidity() != null)
                    .mapToDouble(f -> f.getSoilhumidity().doubleValue())
                    .average()
                    .orElse(50.0);
                
                StringBuilder advice = new StringBuilder();
                advice.append("根据当前环境数据分析：\n\n");
                advice.append("🌡️ 平均温度：").append(String.format("%.1f", avgTemp)).append("°C\n");
                advice.append("💧 平均土壤湿度：").append(String.format("%.0f", avgHumidity)).append("%\n\n");
                
                // 根据作物给出建议
                String cropAdvice = getCropGrowthAdvice(crop, avgTemp, avgHumidity);
                advice.append(cropAdvice);
                
                plan.setAdvice(advice.toString());
                plan.setActions(actions);
                return plan;
            }
        }
        
        // 采购数据查询
        if (lower.contains("采购") && !lower.contains("跳转") && !lower.contains("界面") && !lower.contains("页面")) {
            if (purchaseService != null) {
                List<Purchase> purchaseList = purchaseService.list();
                BigDecimal totalCost = BigDecimal.ZERO;
                
                StringBuilder advice = new StringBuilder();
                advice.append("根据采购记录，共有 ").append(purchaseList.size()).append(" 条采购记录：\n\n");
                
                int showCount = Math.min(5, purchaseList.size());
                for (int i = 0; i < showCount; i++) {
                    Purchase p = purchaseList.get(i);
                    advice.append(i + 1).append(". ").append(p.getProduct());
                    if (p.getPrice() != null && p.getNumber() != null) {
                        BigDecimal cost = p.getPrice().multiply(new BigDecimal(p.getNumber()));
                        totalCost = totalCost.add(cost);
                        advice.append(" - ¥").append(cost.setScale(2, java.math.RoundingMode.HALF_UP));
                    }
                    advice.append("\n");
                }
                
                if (purchaseList.size() > 5) {
                    advice.append("...(还有").append(purchaseList.size() - 5).append("条记录)\n");
                }
                advice.append("\n采购总支出：¥").append(totalCost.setScale(2, java.math.RoundingMode.HALF_UP));
                
                plan.setAdvice(advice.toString());
                plan.setActions(actions);
                return plan;
            }
        }
        
        // 销售/收入数据查询
        if ((lower.contains("销售") || lower.contains("收入") || lower.contains("账单")) && 
            !lower.contains("跳转") && !lower.contains("界面") && !lower.contains("页面")) {
            if (salesService != null) {
                List<Sales> salesList = salesService.list();
                BigDecimal totalIncome = BigDecimal.ZERO;
                
                StringBuilder advice = new StringBuilder();
                advice.append("根据销售记录，共有 ").append(salesList.size()).append(" 条销售记录：\n\n");
                
                int showCount = Math.min(5, salesList.size());
                for (int i = 0; i < showCount; i++) {
                    Sales s = salesList.get(i);
                    advice.append(i + 1).append(". ").append(s.getProduct());
                    if (s.getPrice() != null && s.getNumber() != null) {
                        BigDecimal income = s.getPrice().multiply(new BigDecimal(s.getNumber()));
                        totalIncome = totalIncome.add(income);
                        advice.append(" - ¥").append(income.setScale(2, java.math.RoundingMode.HALF_UP));
                    }
                    advice.append("\n");
                }
                
                if (salesList.size() > 5) {
                    advice.append("...(还有").append(salesList.size() - 5).append("条记录)\n");
                }
                advice.append("\n销售总收入：¥").append(totalIncome.setScale(2, java.math.RoundingMode.HALF_UP));
                
                plan.setAdvice(advice.toString());
                plan.setActions(actions);
                return plan;
            }
        }
        
        // 农田数量查询（避免与上面的利润查询冲突）
        if ((lower.contains("几块") || lower.contains("有哪些")) && (lower.contains("农场") || lower.contains("农田"))) {
            // 查询数据库获取农田信息
            List<Statistic> farms = listFarmsOrEmpty();
            if (farms != null && !farms.isEmpty()) {
                StringBuilder advice = new StringBuilder();
                advice.append("根据系统数据，您目前共有 ").append(farms.size()).append(" 块农田：\n\n");
                for (int i = 0; i < farms.size(); i++) {
                    Statistic farm = farms.get(i);
                    advice.append(i + 1).append(". ").append(farm.getFarm());
                    if (farm.getCrop() != null) {
                        advice.append(" - 种植").append(farm.getCrop());
                    }
                    if (farm.getArea() != null) {
                        advice.append("，面积").append(farm.getArea()).append("亩");
                    }
                    if (farm.getSoilhumidity() != null) {
                        advice.append("，土壤湿度").append(farm.getSoilhumidity()).append("%");
                    }
                    advice.append("\n");
                }
                plan.setAdvice(advice.toString());
                plan.setActions(actions);
                return plan;
            } else {
                plan.setAdvice("系统中暂无农田数据，请先添加农田信息。");
                plan.setActions(actions);
                return plan;
            }
        }
        
        // 查询需要浇水的农田
        if (lower.contains("需要浇水") || lower.contains("需要灌溉") || lower.contains("哪些") && (lower.contains("浇水") || lower.contains("灌溉"))) {
            List<Statistic> farms = listFarmsOrEmpty();
            List<Statistic> needWater = farms.stream()
                .filter(f -> f.getSoilhumidity() != null && f.getSoilhumidity() < 30)
                .collect(Collectors.toList());
            
            if (!needWater.isEmpty()) {
                StringBuilder advice = new StringBuilder();
                advice.append("我看了一下，这几块地有点干了，需要浇浇水：\n\n");
                for (Statistic farm : needWater) {
                    advice.append("🌾 ").append(farm.getFarm()).append("：土壤湿度只有 ")
                          .append(farm.getSoilhumidity()).append("%，有点缺水了\n");
                }
                plan.setAdvice(advice.toString());
                plan.setActions(actions);
                return plan;
            } else {
                plan.setAdvice("我看了一下，所有农田的土壤湿度都挺好的，暂时不用浇水哦~");
                plan.setActions(actions);
                return plan;
            }
        }
        
        // 查询缺水/灌溉相关
        if (lower.contains("缺水") || lower.contains("干旱")) {
            List<Statistic> farms = listFarmsOrEmpty();
            List<Statistic> needWater = farms.stream()
                .filter(f -> f.getSoilhumidity() != null && f.getSoilhumidity() < 30)
                .collect(Collectors.toList());
            
            if (!needWater.isEmpty()) {
                StringBuilder advice = new StringBuilder();
                advice.append("发现有 ").append(needWater.size()).append(" 块地有点干了：\n\n");
                for (Statistic farm : needWater) {
                    advice.append("🌾 ").append(farm.getFarm()).append("：湿度只有 ")
                          .append(farm.getSoilhumidity()).append("%（正常要30%以上）\n");
                }
                advice.append("\n建议赶紧浇浇水，别让庄稼渴着了！");
                plan.setAdvice(advice.toString());
                
                // 添加灌溉动作
                AgentAction irrigation = new AgentAction();
                irrigation.setId("irrigation_on");
                irrigation.setType("irrigation_on");
                irrigation.setTitle("开启灌溉");
                irrigation.setDescription("为缺水农田开启灌溉");
                irrigation.setRiskLevel("medium");
                actions.add(irrigation);
            } else {
                plan.setAdvice("放心吧，所有地的湿度都正常，暂时不用浇水。");
            }
            plan.setActions(actions);
            return plan;
        }
        
        // 优先处理：给指定农田浇水的命令
        if ((lower.contains("浇水") || lower.contains("灌溉")) && 
            (lower.contains("给") || lower.contains("帮") || lower.contains("开"))) {
            
            // 尝试提取农田名称（支持多种格式：二号田、2号田、田地2、农田二等）
            String targetFarm = extractFarmName(userQuestion);
            List<Statistic> farms = listFarmsOrEmpty();
            
            if (targetFarm != null) {
                // 查找匹配的农田
                Statistic matchedFarm = null;
                for (Statistic farm : farms) {
                    if (farm.getFarm() != null && 
                        (farm.getFarm().contains(targetFarm) || 
                         matchFarmNumber(farm.getFarm(), targetFarm))) {
                        matchedFarm = farm;
                        break;
                    }
                }
                
                if (matchedFarm != null) {
                    StringBuilder advice = new StringBuilder();
                    advice.append("好的，为您准备给【").append(matchedFarm.getFarm()).append("】浇水。\n");
                    advice.append("当前土壤湿度：").append(matchedFarm.getSoilhumidity()).append("%\n");
                    if (matchedFarm.getSoilhumidity() != null && matchedFarm.getSoilhumidity() >= 30) {
                        advice.append("（提示：当前湿度正常，确定要浇水吗？）");
                    }
                    plan.setAdvice(advice.toString());
                    
                    AgentAction irrigation = new AgentAction();
                    irrigation.setId("irrigation_on");
                    irrigation.setType("irrigation_on");
                    irrigation.setTitle("给" + matchedFarm.getFarm() + "浇水");
                    irrigation.setDescription("开启灌溉系统为 " + matchedFarm.getFarm() + " 浇水");
                    irrigation.setRiskLevel("medium");
                    actions.add(irrigation);
                    plan.setActions(actions);
                    return plan;
                } else {
                    plan.setAdvice("抱歉，没找到【" + targetFarm + "】这块地。\n您可以说：帮我给1号田浇水、给二号田地灌溉 等。");
                    plan.setActions(actions);
                    return plan;
                }
            } else {
                // 没有指定具体农田，检查哪些需要浇水
                List<Statistic> needWater = farms.stream()
                    .filter(f -> f.getSoilhumidity() != null && f.getSoilhumidity() < 30)
                    .collect(Collectors.toList());
                
                if (!needWater.isEmpty()) {
                    StringBuilder advice = new StringBuilder();
                    advice.append("检测到以下农田需要浇水：\n");
                    for (Statistic farm : needWater) {
                        advice.append("• ").append(farm.getFarm()).append("（湿度").append(farm.getSoilhumidity()).append("%）\n");
                    }
                    advice.append("\n确认后将开启灌溉系统。");
                    plan.setAdvice(advice.toString());
                    
                    AgentAction irrigation = new AgentAction();
                    irrigation.setId("irrigation_on");
                    irrigation.setType("irrigation_on");
                    irrigation.setTitle("开启灌溉");
                    irrigation.setDescription("为缺水农田开启灌溉");
                    irrigation.setRiskLevel("medium");
                    actions.add(irrigation);
                } else {
                    plan.setAdvice("所有农田湿度正常，暂时不需要浇水。\n如需强制浇水，请指定农田名称，如：帮我给1号田浇水");
                }
                plan.setActions(actions);
                return plan;
            }
        }

        // 未指定田号，但明显在问环境/土壤湿度数值（不要跳转大屏）
        if (isEnvOrSoilDataQuestion(lower)) {
            List<Statistic> farms = listFarmsOrEmpty();
            if (farms != null && !farms.isEmpty()) {
                StringBuilder advice = new StringBuilder();
                advice.append("各农田土壤湿度一览（系统统计）：\n\n");
                int n = Math.min(12, farms.size());
                for (int i = 0; i < n; i++) {
                    Statistic f = farms.get(i);
                    advice.append("• ").append(f.getFarm() != null ? f.getFarm() : ("ID " + f.getId()));
                    advice.append("：土壤湿度 ");
                    advice.append(f.getSoilhumidity() != null ? f.getSoilhumidity() + "%" : "暂无");
                    advice.append("\n");
                }
                if (farms.size() > n) {
                    advice.append("\n… 共 ").append(farms.size()).append(" 块田，此处列出前 ").append(n).append(" 条。\n");
                }
                advice.append("\n可再问：「3号田土壤湿度怎么样」查看单块地详情。");
                plan.setAdvice(advice.toString());
                plan.setActions(actions);
                return plan;
            }
        }
        
        // 默认兜底逻辑
        plan.setAdvice("好的，我来帮您处理这个请求。");
        if (lower.contains("浇水") || lower.contains("灌溉")) {
            // 已在上面处理，这里是兜底
            plan.setAdvice("您想给哪块地浇水呢？可以说：帮我给1号田浇水");
        }
        else if (lower.contains("关水") || (lower.contains("停") && lower.contains("水"))) {
            plan.setAdvice("好的，帮您准备关水操作了。");
            AgentAction irrigationOff = new AgentAction();
            irrigationOff.setId("irrigation_off");
            irrigationOff.setType("irrigation_off");
            irrigationOff.setTitle("关闭灌溉");
            irrigationOff.setDescription("关闭水泵");
            irrigationOff.setRiskLevel("low");
            actions.add(irrigationOff);
        }
        else if (lower.contains("灯") || lower.contains("补光")) {
            plan.setAdvice("好的，帮您准备开灯操作了。");
            AgentAction led = new AgentAction();
            led.setId("led_on");
            led.setType("led_on");
            led.setTitle("开启补光灯");
            led.setDescription("补光灯开启以提升光照");
            led.setRiskLevel("low");
            actions.add(led);
        }
        // 智能匹配页面跳转
        else if (lower.contains("果蔬") || lower.contains("双检") || lower.contains("检测") || lower.contains("识别")) {
            plan.setAdvice("好的，为您打开果蔬双检分析界面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_fruit_detect");
            nav.setType("navigate");
            nav.setTitle("跳转到果蔬双检分析");
            nav.setDescription("进入果蔬双检分析界面");
            nav.setRoute("/fruit-detect");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("3d") || lower.contains("沙盘") || lower.contains("三维")) {
            plan.setAdvice("好的，为您打开3D沙盘界面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_3d");
            nav.setType("navigate");
            nav.setTitle("跳转到3D沙盘");
            nav.setDescription("进入农场3D沙盘界面");
            nav.setRoute("/farmmap3d");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("aether") || lower.contains("传感") || lower.contains("实时数据")) {
            plan.setAdvice("好的，为您打开传感器数据监控界面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_aether");
            nav.setType("navigate");
            nav.setTitle("跳转到Aether数据监控");
            nav.setDescription("进入Aether传感器数据监控");
            nav.setRoute("/aether-monitor");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("采购") || lower.contains("物资")) {
            plan.setAdvice("好的，为您打开物资采购管理页面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_purchase");
            nav.setType("navigate");
            nav.setTitle("跳转到物资采购");
            nav.setDescription("进入物资采购管理页面");
            nav.setRoute("/purchase");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("收入") || lower.contains("销售") || lower.contains("账单") || lower.contains("出售")) {
            plan.setAdvice("好的，为您打开销售账单页面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_sales");
            nav.setType("navigate");
            nav.setTitle("跳转到出售账单");
            nav.setDescription("进入出售账单查看收入");
            nav.setRoute("/sales");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (isBusinessAnalysisPageNavigation(lower)) {
            plan.setAdvice("好的，为您打开经营分析页面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_analysis");
            nav.setType("navigate");
            nav.setTitle("跳转到经营分析");
            nav.setDescription("进入经营效益分析页面");
            nav.setRoute("/business-analysis");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("库存") || lower.contains("仓库")) {
            plan.setAdvice("好的，为您打开库存管理页面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_inventory");
            nav.setType("navigate");
            nav.setTitle("跳转到物资库存");
            nav.setDescription("进入物资库存管理页面");
            nav.setRoute("/inventory");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("大屏") || lower.contains("可视化")) {
            plan.setAdvice("好的，为您打开可视化大屏。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_bigscreen");
            nav.setType("navigate");
            nav.setTitle("跳转到可视化大屏");
            nav.setDescription("进入全局可视化大屏");
            nav.setRoute("/bigscreen");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (!isEnvOrSoilDataQuestion(lower)
                && (lower.contains("监测") || lower.contains("环境") || lower.contains("温度") || lower.contains("湿度"))) {
            plan.setAdvice("好的，为您打开环境监测大屏。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_dashbord");
            nav.setType("navigate");
            nav.setTitle("跳转到环境监测");
            nav.setDescription("进入环境监测大屏");
            nav.setRoute("/dashbordnew");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("农田") || lower.contains("统计")) {
            plan.setAdvice("好的，为您打开农田信息统计页面。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_statistic");
            nav.setType("navigate");
            nav.setTitle("跳转到农田信息");
            nav.setDescription("进入农田信息统计页面");
            nav.setRoute("/statistic");
            nav.setRiskLevel("low");
            actions.add(nav);
        } else if (lower.contains("地图") || lower.contains("高德")) {
            plan.setAdvice("好的，为您打开地理地图。");
            AgentAction nav = new AgentAction();
            nav.setId("navigate_map");
            nav.setType("navigate");
            nav.setTitle("跳转到地理地图");
            nav.setDescription("进入农场地理地图");
            nav.setRoute("/farm-map-gaode");
            nav.setRiskLevel("low");
            actions.add(nav);
        }
        if (actions.isEmpty() && isVagueFarmingAdviceQuestion(lower)) {
            plan.setAdvice(buildVagueFarmingAdvice(userId));
            if (userId != null && agentUserMemoryService != null) {
                try {
                    agentUserMemoryService.appendConversationTurn(userId, raw, plan.getAdvice());
                } catch (Exception e) {
                    log.warn("农事建议兜底（末段）写入对话记忆失败: {}", e.getMessage());
                }
            }
        } else if (actions.isEmpty()) {
            plan.setAdvice("不好意思，我没太明白您的意思。您可以问我：\n• 哪块地需要浇水\n• 今年赚了多少钱\n• 帮我给X号田浇水\n• 打开XX页面");
        }

        plan.setActions(actions);
        return plan;
    }
    
    // ==================== 新增工具实现：全系统数据读取和深度分析 ====================
    
    /**
     * 工具11: 获取系统整体概览
     */
    private String getSystemOverview() {
        JSONObject result = new JSONObject();
        try {
            // 农田统计
            int totalFarms = 0;
            if (statisticService != null) {
                totalFarms = listFarmsOrEmpty().size();
            }
            
            // 用户统计
            int totalUsers = 0;
            if (userService != null) {
                totalUsers = userService.list().size();
            }
            
            // 销售统计
            int totalSales = 0;
            BigDecimal totalRevenue = BigDecimal.ZERO;
            if (salesService != null) {
                List<Sales> salesList = salesService.list();
                totalSales = salesList.size();
                for (Sales sale : salesList) {
                    if (sale.getPrice() != null && sale.getNumber() != null) {
                        totalRevenue = totalRevenue.add(sale.getPrice().multiply(new BigDecimal(sale.getNumber())));
                    }
                }
            }
            
            // 库存统计
            int totalInventoryItems = 0;
            int lowStockItems = 0;
            if (inventoryService != null) {
                List<Inventory> inventoryList = inventoryService.list();
                totalInventoryItems = inventoryList.size();
                for (Inventory inv : inventoryList) {
                    if (inv.getNumber() != null && inv.getNumber() < 10) {
                        lowStockItems++;
                    }
                }
            }
            
            // 公告统计
            int totalNotices = 0;
            if (noticeService != null) {
                totalNotices = noticeService.list().size();
            }
            
            result.put("total_farms", totalFarms);
            result.put("total_users", totalUsers);
            result.put("total_sales_orders", totalSales);
            result.put("total_revenue", totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
            result.put("total_inventory_items", totalInventoryItems);
            result.put("low_stock_items", lowStockItems);
            result.put("total_notices", totalNotices);
            result.put("status", "success");
            
            return result.toString();
        } catch (Exception e) {
            log.error("获取系统概览失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 工具13: 经营健康度评分
     */
    private String getBusinessHealthScore() {
        JSONObject result = new JSONObject();
        try {
            int totalScore = 0;
            JSONArray diagnostics = new JSONArray();
            
            // 1. 利润率评分 (30分)
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            
            if (salesService != null) {
                List<Sales> salesList = salesService.list();
                for (Sales sale : salesList) {
                    if (sale.getPrice() != null && sale.getNumber() != null) {
                        totalIncome = totalIncome.add(sale.getPrice().multiply(new BigDecimal(sale.getNumber())));
                    }
                }
            }
            
            if (purchaseService != null) {
                List<Purchase> purchaseList = purchaseService.list();
                for (Purchase purchase : purchaseList) {
                    if (purchase.getPrice() != null && purchase.getNumber() != null) {
                        totalCost = totalCost.add(purchase.getPrice().multiply(new BigDecimal(purchase.getNumber())));
                    }
                }
            }
            
            BigDecimal profitRate = BigDecimal.ZERO;
            if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                profitRate = totalIncome.subtract(totalCost).divide(totalIncome, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal(100));
            }
            
            int profitScore = 0;
            String profitDiagnosis = "";
            if (profitRate.compareTo(new BigDecimal(50)) >= 0) {
                profitScore = 30;
                profitDiagnosis = "利润率" + profitRate.setScale(2, java.math.RoundingMode.HALF_UP) + "%，非常健康";
            } else if (profitRate.compareTo(new BigDecimal(30)) >= 0) {
                profitScore = 20;
                profitDiagnosis = "利润率" + profitRate.setScale(2, java.math.RoundingMode.HALF_UP) + "%，良好";
            } else if (profitRate.compareTo(BigDecimal.ZERO) > 0) {
                profitScore = 10;
                profitDiagnosis = "利润率" + profitRate.setScale(2, java.math.RoundingMode.HALF_UP) + "%，偏低，建议优化成本";
            } else {
                profitScore = 0;
                profitDiagnosis = "当前亏损，需要紧急改善经营策略";
            }
            totalScore += profitScore;
            diagnostics.add("【利润率】" + profitDiagnosis + " (" + profitScore + "/30分)");
            
            // 2. 库存管理评分 (25分)
            int inventoryScore = 25;
            int lowStockCount = 0;
            int totalInventory = 0;
            
            if (inventoryService != null) {
                List<Inventory> inventoryList = inventoryService.list();
                totalInventory = inventoryList.size();
                for (Inventory inv : inventoryList) {
                    if (inv.getNumber() != null && inv.getNumber() < 10) {
                        lowStockCount++;
                    }
                }
                
                if (totalInventory > 0) {
                    double lowStockRate = (double) lowStockCount / totalInventory;
                    if (lowStockRate > 0.3) {
                        inventoryScore = 10;
                        diagnostics.add("【库存管理】低库存物品占比" + String.format("%.1f%%", lowStockRate * 100) + "，需要补货 (" + inventoryScore + "/25分)");
                    } else if (lowStockRate > 0.1) {
                        inventoryScore = 18;
                        diagnostics.add("【库存管理】部分物品库存偏低，建议关注 (" + inventoryScore + "/25分)");
                    } else {
                        diagnostics.add("【库存管理】库存充足，管理良好 (" + inventoryScore + "/25分)");
                    }
                } else {
                    diagnostics.add("【库存管理】暂无库存数据 (" + inventoryScore + "/25分)");
                }
            }
            totalScore += inventoryScore;
            
            // 3. 资金流动性评分 (25分)
            int cashFlowScore = 0;
            if (totalIncome.compareTo(totalCost) > 0) {
                cashFlowScore = 25;
                diagnostics.add("【资金流】收入大于支出，资金流健康 (" + cashFlowScore + "/25分)");
            } else if (totalIncome.compareTo(totalCost) == 0) {
                cashFlowScore = 15;
                diagnostics.add("【资金流】收支平衡，建议提升盈利能力 (" + cashFlowScore + "/25分)");
            } else {
                cashFlowScore = 5;
                diagnostics.add("【资金流】支出大于收入，需要控制成本或增加收入 (" + cashFlowScore + "/25分)");
            }
            totalScore += cashFlowScore;
            
            // 4. 农田环境评分 (20分)
            int envScore = 20;
            int abnormalFarms = 0;
            int totalFarms = 0;
            
            if (statisticService != null) {
                List<Statistic> farms = listFarmsOrEmpty();
                totalFarms = farms.size();
                for (Statistic farm : farms) {
                    if (farm.getSoilhumidity() != null && farm.getSoilhumidity() < 30) abnormalFarms++;
                    if (farm.getTemperature() != null && 
                        (farm.getTemperature().compareTo(new BigDecimal(10)) < 0 || 
                         farm.getTemperature().compareTo(new BigDecimal(35)) > 0)) abnormalFarms++;
                }
                
                if (totalFarms > 0) {
                    double abnormalRate = (double) abnormalFarms / totalFarms;
                    if (abnormalRate > 0.3) {
                        envScore = 8;
                        diagnostics.add("【农田环境】多块农田环境异常，需要及时处理 (" + envScore + "/20分)");
                    } else if (abnormalRate > 0) {
                        envScore = 15;
                        diagnostics.add("【农田环境】个别农田需要关注 (" + envScore + "/20分)");
                    } else {
                        diagnostics.add("【农田环境】所有农田环境良好 (" + envScore + "/20分)");
                    }
                } else {
                    diagnostics.add("【农田环境】暂无农田数据 (" + envScore + "/20分)");
                }
            }
            totalScore += envScore;
            
            // 综合评价
            String overallRating = "";
            String suggestion = "";
            if (totalScore >= 85) {
                overallRating = "优秀";
                suggestion = "农场经营状况非常好，保持当前策略，继续优化细节。";
            } else if (totalScore >= 70) {
                overallRating = "良好";
                suggestion = "农场经营整体健康，部分方面有提升空间。";
            } else if (totalScore >= 50) {
                overallRating = "中等";
                suggestion = "农场经营存在一些问题，建议重点优化利润率和库存管理。";
            } else {
                overallRating = "需改善";
                suggestion = "农场经营面临较大挑战，建议尽快制定改善计划，优先解决资金流和利润问题。";
            }
            
            result.put("total_score", totalScore);
            result.put("max_score", 100);
            result.put("rating", overallRating);
            result.put("diagnostics", diagnostics);
            result.put("suggestion", suggestion);
            result.put("status", "success");
            
            return result.toString();
        } catch (Exception e) {
            log.error("计算经营健康度失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 工具14: 趋势分析
     */
    private String getTrendAnalysis() {
        JSONObject result = new JSONObject();
        try {
            JSONArray insights = new JSONArray();
            
            // 销售趋势分析
            if (salesService != null) {
                List<Sales> salesList = salesService.list();
                if (salesList.size() > 3) {
                    insights.add("销售记录共" + salesList.size() + "条，业务活跃度较好");
                    
                    // 简单的趋势判断：最近的销售额
                    BigDecimal recentSales = BigDecimal.ZERO;
                    int recentCount = Math.min(3, salesList.size());
                    for (int i = salesList.size() - recentCount; i < salesList.size(); i++) {
                        Sales sale = salesList.get(i);
                        if (sale.getPrice() != null && sale.getNumber() != null) {
                            recentSales = recentSales.add(sale.getPrice().multiply(new BigDecimal(sale.getNumber())));
                        }
                    }
                    insights.add("近期销售额约¥" + recentSales.setScale(2, java.math.RoundingMode.HALF_UP) + "，建议继续扩大销售渠道");
                } else {
                    insights.add("销售记录较少，建议加强产品销售和市场推广");
                }
            }
            
            // 库存趋势分析
            if (inventoryService != null) {
                List<Inventory> inventoryList = inventoryService.list();
                int lowStock = 0;
                for (Inventory inv : inventoryList) {
                    if (inv.getNumber() != null && inv.getNumber() < 10) lowStock++;
                }
                
                if (lowStock > 0) {
                    insights.add("当前有" + lowStock + "项物资库存不足，建议及时补充避免影响生产");
                } else {
                    insights.add("库存充足，物资管理良好");
                }
            }
            
            // 采购成本趋势
            if (purchaseService != null) {
                List<Purchase> purchaseList = purchaseService.list();
                BigDecimal totalPurchase = BigDecimal.ZERO;
                for (Purchase p : purchaseList) {
                    if (p.getPrice() != null && p.getNumber() != null) {
                        totalPurchase = totalPurchase.add(p.getPrice().multiply(new BigDecimal(p.getNumber())));
                    }
                }
                insights.add("累计采购支出¥" + totalPurchase.setScale(2, java.math.RoundingMode.HALF_UP) + "，建议对比市场价格优化采购渠道");
            }
            
            // 预测建议
            insights.add("【预测建议】根据当前数据，建议：1) 保持稳定的销售节奏 2) 提前规划物资采购避免缺货 3) 关注农田环境变化及时调整");
            
            result.put("insights", insights);
            result.put("status", "success");
            
            return result.toString();
        } catch (Exception e) {
            log.error("趋势分析失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    // ==================== 农田名称解析辅助方法 ====================
    
    /**
     * 从用户输入中提取农田名称/编号
     * 支持格式：二号田、2号田、田地2、农田二、1号、一号 等
     */
    private String extractFarmName(String input) {
        if (input == null) return null;
        
        // 中文数字映射
        Map<String, String> chineseNumbers = new LinkedHashMap<>();
        chineseNumbers.put("一", "1");
        chineseNumbers.put("二", "2");
        chineseNumbers.put("三", "3");
        chineseNumbers.put("四", "4");
        chineseNumbers.put("五", "5");
        chineseNumbers.put("六", "6");
        chineseNumbers.put("七", "7");
        chineseNumbers.put("八", "8");
        chineseNumbers.put("九", "9");
        chineseNumbers.put("十", "10");
        
        // 正则匹配多种格式
        java.util.regex.Pattern[] patterns = {
            // 匹配 "X号田" "X号田地" "X号农田"
            java.util.regex.Pattern.compile("([一二三四五六七八九十\\d]+)\\s*号\\s*(田|田地|农田)?"),
            // 匹配 "田地X" "农田X"
            java.util.regex.Pattern.compile("(田地|农田|田)\\s*([一二三四五六七八九十\\d]+)"),
            // 匹配 "第X块"
            java.util.regex.Pattern.compile("第\\s*([一二三四五六七八九十\\d]+)\\s*(块|号)?"),
        };
        
        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                String number = matcher.group(1);
                if (number == null && matcher.groupCount() > 1) {
                    number = matcher.group(2);
                }
                if (number != null) {
                    // 转换中文数字为阿拉伯数字
                    for (Map.Entry<String, String> entry : chineseNumbers.entrySet()) {
                        number = number.replace(entry.getKey(), entry.getValue());
                    }
                    return number;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 从用户输入中提取新农田名称
     */
    private String extractNewFarmName(String input) {
        if (input == null) return "新农田";
        
        // 尝试匹配 "叫XX" "名字叫XX" "命名为XX"
        java.util.regex.Pattern[] patterns = {
            java.util.regex.Pattern.compile("(?:叫|名字叫|命名为|名为|取名)\\s*[\"'「]?([\\u4e00-\\u9fa5\\w]+)[\"'」]?"),
            java.util.regex.Pattern.compile("[\"'「]([\\u4e00-\\u9fa5\\w]+)[\"'」]\\s*(?:农田|土地|田地)?"),
        };
        
        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                String name = matcher.group(1);
                if (name != null && name.length() >= 2 && name.length() <= 20) {
                    return name;
                }
            }
        }
        
        // 生成默认名称
        List<Statistic> farms = listFarmsOrEmpty();
        int nextNum = farms.size() + 1;
        return nextNum + "号田";
    }
    
    /**
     * 从用户输入中提取面积
     */
    private String extractArea(String input) {
        if (input == null) return "100";
        
        // 匹配数字+亩
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:亩|mu|平方米|㎡)?");
        java.util.regex.Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "100";
    }
    
    /**
     * 从用户输入中提取作物名称
     */
    private String extractCropName(String input) {
        if (input == null) return "作物";
        
        // 常见作物关键词
        String[] crops = {"草莓", "番茄", "西红柿", "黄瓜", "辣椒", "茄子", "白菜", "生菜", 
                          "玉米", "水稻", "小麦", "土豆", "红薯", "萝卜", "胡萝卜", "西瓜",
                          "葡萄", "苹果", "梨", "桃子", "橙子", "柑橘", "香蕉", "芒果",
                          "蓝莓", "樱桃", "荔枝", "龙眼", "柚子", "猕猴桃", "火龙果"};
        
        for (String crop : crops) {
            if (input.contains(crop)) {
                return crop;
            }
        }
        return "作物";
    }
    
    /**
     * 根据地址匹配预设的地理坐标（用于3D地图和GIS地图显示区块）
     */
    private Map<String, Object> getPresetGeoLocation(String address) {
        if (address == null || address.isEmpty()) return null;
        
        // 预设地点坐标数据库（可根据需要扩展）
        Map<String, Map<String, Object>> locationDb = new LinkedHashMap<>();
        
        // 张家界七十二奇楼（位于永定区）
        Map<String, Object> qilou = new LinkedHashMap<>();
        qilou.put("centerLng", "110.455388");
        qilou.put("centerLat", "29.145479");
        qilou.put("coordinates", "[{\"lng\":110.454834,\"lat\":29.145675},{\"lng\":110.45501,\"lat\":29.145181},{\"lng\":110.455942,\"lat\":29.145325},{\"lng\":110.45577,\"lat\":29.145777}]");
        qilou.put("district", "永定区");
        qilou.put("suggestedName", "72奇楼");  // 建议的农田名称
        locationDb.put("七十二奇楼", qilou);
        locationDb.put("72奇楼", qilou);
        locationDb.put("奇楼", qilou);
        
        // 张家界天门山（示例扩展）
        Map<String, Object> tianmen = new LinkedHashMap<>();
        tianmen.put("centerLng", "110.478889");
        tianmen.put("centerLat", "29.053333");
        tianmen.put("coordinates", "[{\"lng\":110.477,\"lat\":29.054},{\"lng\":110.478,\"lat\":29.052},{\"lng\":110.480,\"lat\":29.053},{\"lng\":110.479,\"lat\":29.055}]");
        tianmen.put("district", "张家界市");
        locationDb.put("天门山", tianmen);
        
        // 张家界武陵源（示例扩展）
        Map<String, Object> wulingyuan = new LinkedHashMap<>();
        wulingyuan.put("centerLng", "110.550000");
        wulingyuan.put("centerLat", "29.345000");
        wulingyuan.put("coordinates", "[{\"lng\":110.548,\"lat\":29.346},{\"lng\":110.549,\"lat\":29.343},{\"lng\":110.552,\"lat\":29.344},{\"lng\":110.551,\"lat\":29.347}]");
        wulingyuan.put("district", "武陵源区");
        locationDb.put("武陵源", wulingyuan);
        
        // 遍历匹配
        for (Map.Entry<String, Map<String, Object>> entry : locationDb.entrySet()) {
            if (address.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 从地址中提取区县信息（用于GIS地图匹配）
     */
    private String extractDistrictFromAddress(String address) {
        if (address == null || address.isEmpty()) return null;
        
        // 先检查预设地点是否有区县信息
        Map<String, Object> geoData = getPresetGeoLocation(address);
        if (geoData != null && geoData.get("district") != null) {
            return geoData.get("district").toString();
        }
        
        // 常见城市/区县关键词匹配
        java.util.regex.Pattern[] patterns = {
            // 匹配 XX市、XX区、XX县
            java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]{2,}(?:市|区|县|州))"),
        };
        
        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(address);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        // 如果没有明确的市/区/县后缀，尝试提取前几个字作为地区
        if (address.length() >= 2) {
            // 检查是否以常见城市名开头
            String[] cities = {"北京", "上海", "广州", "深圳", "杭州", "南京", "武汉", "成都", 
                               "重庆", "天津", "苏州", "西安", "长沙", "青岛", "郑州", "东莞",
                               "张家界", "桂林", "三亚", "厦门", "昆明", "丽江", "大理"};
            for (String city : cities) {
                if (address.startsWith(city)) {
                    return city + "市";
                }
            }
        }
        
        return null;
    }
    
    /**
     * 从用户输入中提取地址/位置信息
     */
    private String extractAddress(String input) {
        if (input == null) return null;
        
        // 匹配 "位置在XX" "地址是XX" "在XX"
        java.util.regex.Pattern[] patterns = {
            java.util.regex.Pattern.compile("(?:位置在|地址是|地址在|位于|在)\\s*([\\u4e00-\\u9fa5\\w]+(?:市|区|县|镇|村|路|街|号|楼|栋)[\\u4e00-\\u9fa5\\w]*)"),
            java.util.regex.Pattern.compile("(?:位置|地址)[：:]\\s*([\\u4e00-\\u9fa5\\w]+)"),
        };
        
        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                String address = matcher.group(1);
                if (address != null && address.length() >= 2) {
                    return address;
                }
            }
        }
        return null;
    }
    
    /**
     * 根据作物和环境数据给出种植建议
     */
    private String getCropGrowthAdvice(String crop, double temp, double humidity) {
        StringBuilder advice = new StringBuilder();
        
        // 作物生长条件数据库
        Map<String, int[]> cropConditions = new LinkedHashMap<>();
        // 格式：{最低温度, 最高温度, 最适温度, 最低湿度, 最高湿度}
        cropConditions.put("草莓", new int[]{5, 30, 20, 60, 80});
        cropConditions.put("番茄", new int[]{10, 35, 25, 50, 70});
        cropConditions.put("西红柿", new int[]{10, 35, 25, 50, 70});
        cropConditions.put("黄瓜", new int[]{15, 35, 28, 60, 80});
        cropConditions.put("辣椒", new int[]{15, 35, 25, 50, 70});
        cropConditions.put("茄子", new int[]{15, 35, 26, 50, 70});
        cropConditions.put("白菜", new int[]{5, 25, 18, 60, 80});
        cropConditions.put("生菜", new int[]{5, 25, 18, 60, 80});
        cropConditions.put("玉米", new int[]{10, 35, 25, 50, 70});
        cropConditions.put("水稻", new int[]{15, 35, 28, 70, 90});
        cropConditions.put("西瓜", new int[]{18, 38, 28, 50, 70});
        cropConditions.put("葡萄", new int[]{10, 35, 25, 50, 70});
        
        int[] conditions = cropConditions.getOrDefault(crop, new int[]{10, 30, 22, 50, 70});
        int minTemp = conditions[0];
        int maxTemp = conditions[1];
        int optTemp = conditions[2];
        int minHum = conditions[3];
        int maxHum = conditions[4];
        
        // 温度评估
        boolean tempOk = temp >= minTemp && temp <= maxTemp;
        boolean tempOptimal = Math.abs(temp - optTemp) <= 5;
        
        // 湿度评估
        boolean humOk = humidity >= minHum && humidity <= maxHum;
        
        if (tempOptimal && humOk) {
            advice.append("【非常适合】当前环境非常适合种植").append(crop).append("！\n\n");
            advice.append("• ").append(crop).append("最适生长温度：").append(optTemp).append("°C左右\n");
            advice.append("• 当前温度").append(String.format("%.1f", temp)).append("°C，处于最佳范围\n");
            advice.append("• 土壤湿度适中，利于根系发育\n\n");
            advice.append("💡 建议：现在是种植").append(crop).append("的好时机，可以开始播种或移栽。");
        } else if (tempOk && humOk) {
            advice.append("👍 【适合种植】当前环境适合种植").append(crop).append("。\n\n");
            advice.append("• ").append(crop).append("适宜温度范围：").append(minTemp).append("-").append(maxTemp).append("°C\n");
            advice.append("• 当前温度").append(String.format("%.1f", temp)).append("°C，在可接受范围内\n\n");
            if (temp < optTemp - 3) {
                advice.append("💡 提示：温度略低于最佳值，生长速度可能稍慢，建议适当保温。");
            } else if (temp > optTemp + 3) {
                advice.append("💡 提示：温度略高，注意通风降温和补充水分。");
            }
        } else {
            advice.append("【暂不建议】当前环境不太适合种植").append(crop).append("。\n\n");
            if (!tempOk) {
                if (temp < minTemp) {
                    advice.append("• 温度过低（当前").append(String.format("%.1f", temp)).append("°C，需要≥").append(minTemp).append("°C）\n");
                    advice.append("💡 建议：等气温回升或使用大棚保温后再种植。\n");
                } else {
                    advice.append("• 温度过高（当前").append(String.format("%.1f", temp)).append("°C，需要≤").append(maxTemp).append("°C）\n");
                    advice.append("💡 建议：加强通风降温，或等天气凉爽后再种植。\n");
                }
            }
            if (!humOk) {
                if (humidity < minHum) {
                    advice.append("• 土壤偏干（当前").append(String.format("%.0f", humidity)).append("%，建议").append(minHum).append("-").append(maxHum).append("%）\n");
                    advice.append("💡 建议：增加灌溉频率。\n");
                } else {
                    advice.append("• 土壤过湿（当前").append(String.format("%.0f", humidity)).append("%）\n");
                    advice.append("💡 建议：改善排水，避免根部腐烂。\n");
                }
            }
        }
        
        return advice.toString();
    }
    
    /**
     * 匹配农田编号（支持中文和阿拉伯数字）
     * 例如：农田名称"1号田" 匹配目标"1" 或 "一"
     */
    private boolean matchFarmNumber(String farmName, String target) {
        if (farmName == null || target == null) return false;
        
        // 中文数字映射
        Map<String, String> chineseToArabic = new LinkedHashMap<>();
        chineseToArabic.put("一", "1");
        chineseToArabic.put("二", "2");
        chineseToArabic.put("三", "3");
        chineseToArabic.put("四", "4");
        chineseToArabic.put("五", "5");
        chineseToArabic.put("六", "6");
        chineseToArabic.put("七", "7");
        chineseToArabic.put("八", "8");
        chineseToArabic.put("九", "9");
        chineseToArabic.put("十", "10");
        
        // 将目标转换为阿拉伯数字
        String normalizedTarget = target;
        for (Map.Entry<String, String> entry : chineseToArabic.entrySet()) {
            normalizedTarget = normalizedTarget.replace(entry.getKey(), entry.getValue());
        }
        
        // 从农田名称中提取数字
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([一二三四五六七八九十\\d]+)");
        java.util.regex.Matcher matcher = pattern.matcher(farmName);
        
        while (matcher.find()) {
            String farmNumber = matcher.group(1);
            // 转换中文数字
            for (Map.Entry<String, String> entry : chineseToArabic.entrySet()) {
                farmNumber = farmNumber.replace(entry.getKey(), entry.getValue());
            }
            if (farmNumber.equals(normalizedTarget)) {
                return true;
            }
        }
        
        return false;
    }
    
    // ==================== AI Agent 执行工具实现 ====================
    
    /**
     * 是否应跳转「经营分析」页面：避免「经营上的农事建议」等因含「经营」误跳页。
     */
    private static boolean isBusinessAnalysisPageNavigation(String lower) {
        if (!StringUtils.hasText(lower)) {
            return false;
        }
        if ((lower.contains("农事") || lower.contains("种植") || lower.contains("栽培")) && lower.contains("建议")) {
            return false;
        }
        if (lower.contains("经营分析") || lower.contains("效益分析") || lower.contains("经营报表")
                || lower.contains("经营效益") || lower.contains("效益报表")) {
            return true;
        }
        if ((lower.contains("打开") || lower.contains("跳转") || lower.contains("进入") || lower.contains("看看") || lower.contains("查看"))
                && (lower.contains("经营分析") || (lower.contains("经营") && lower.contains("分析")))) {
            return true;
        }
        if (lower.contains("分析") && (lower.contains("经营") || lower.contains("效益") || lower.contains("销售") || lower.contains("利润"))) {
            return true;
        }
        if (lower.contains("效益") && (lower.contains("怎么样") || lower.contains("如何") || lower.contains("报表") || lower.contains("分析"))) {
            return true;
        }
        if (lower.contains("经营") && (lower.contains("页面") || lower.contains("大屏") || lower.contains("看板"))) {
            return true;
        }
        return false;
    }

    /** 兜底：用户是否在要泛化的农事/种植建议（非具体设备页跳转） */
    private static boolean isVagueFarmingAdviceQuestion(String lower) {
        if (!StringUtils.hasText(lower)) {
            return false;
        }
        if (lower.contains("农事建议") || lower.contains("种植建议") || lower.contains("栽培建议") || lower.contains("生产建议")) {
            return true;
        }
        if (lower.contains("建议") && (lower.contains("农事") || lower.contains("种植") || lower.contains("田里")
                || lower.contains("大棚") || lower.contains("作物"))) {
            return true;
        }
        if ((lower.contains("给我") || lower.contains("给点") || lower.contains("给些") || lower.contains("想要") || lower.contains("想听"))
                && lower.contains("建议")
                && (lower.contains("农") || lower.contains("种") || lower.contains("田") || lower.contains("棚"))) {
            return true;
        }
        if ((lower.contains("指点") || lower.contains("支招") || lower.contains("支个招"))
                && (lower.contains("种") || lower.contains("田") || lower.contains("棚") || lower.contains("农"))) {
            return true;
        }
        return false;
    }

    /**
     * 兜底路径下给出可读农事要点；若已有偏好（如番茄），文案向其靠拢。
     */
    private String buildVagueFarmingAdvice(Integer userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("可以，先给您几条马上能对照执行的农事思路（具体仍以您田里传感器与当地规程为准）：\n\n");
        String prefs = null;
        if (userId != null && agentUserMemoryService != null) {
            try {
                AgentUserMemory m = agentUserMemoryService.getOrCreate(userId);
                if (m != null && StringUtils.hasText(m.getPreferences())) {
                    prefs = m.getPreferences().trim();
                }
            } catch (Exception e) {
                log.debug("读取偏好用于农事建议兜底: {}", e.getMessage());
            }
        }
        if (StringUtils.hasText(prefs)) {
            String show = prefs.length() > 500 ? prefs.substring(prefs.length() - 500) : prefs;
            sb.append("【结合您在「记忆」里保存的偏好】\n");
            sb.append(show).append("\n\n");
        }
        boolean tomato = prefs != null && (prefs.contains("番茄") || prefs.contains("西红柿"));
        if (tomato) {
            sb.append("结合您偏好的番茄，近期可重点留意：\n");
            sb.append("• 花果期：控氮肥防徒长；坐果期补钙硼，减少裂果与脐腐风险。\n");
            sb.append("• 水肥：小水勤浇，忌长期积水闷根；晴天中午短时通风降湿。\n");
            sb.append("• 病虫害：早防早治，重点巡叶背粉虱/潜叶蝇与早疫病斑。\n");
        } else {
            sb.append("通用设施蔬菜方向：\n");
            sb.append("• 先看土壤湿度与天气预报再浇水，阴雨天适当控水。\n");
            sb.append("• 追肥少量多次，避免一次重肥烧苗。\n");
            sb.append("• 棚室注意通风与温差，防高湿诱发病害。\n");
        }
        try {
            List<Statistic> farms = listFarmsOrEmpty();
            if (!farms.isEmpty()) {
                sb.append("\n您系统里已登记的田块：");
                int n = Math.min(5, farms.size());
                for (int i = 0; i < n; i++) {
                    Statistic f = farms.get(i);
                    if (f.getFarm() != null) {
                        sb.append("\n• ").append(f.getFarm());
                        if (StringUtils.hasText(f.getCrop())) {
                            sb.append("（").append(f.getCrop()).append("）");
                        }
                    }
                }
                if (farms.size() > n) {
                    sb.append("\n…等共 ").append(farms.size()).append(" 块，可再说田号我按地块细写。");
                } else {
                    sb.append("\n如需按某一块田写得更细，请直接说田号或名称。");
                }
            }
        } catch (Exception ignored) {
            sb.append("\n如需按某一块田写得更细，请直接说田号或名称。");
        }
        return sb.toString();
    }

    /**
     * 用户显式要求写入长期偏好时立即落库并返回固定应答；否则返回 null。
     * 在通义调用前与兜底路径中共用，保证「请记住 / 备注一下」等说法一定写库。
     */
    private AgentPlan tryPersistExplicitMemoryPreference(Integer userId, String userQuestion) {
        if (!StringUtils.hasText(userQuestion)) {
            return null;
        }
        String raw = userQuestion.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!isExplicitMemoryPreferenceRequest(raw, lower)) {
            return null;
        }
        AgentPlan plan = new AgentPlan();
        plan.setActions(new ArrayList<>());
        // 未登录时无法绑定 userId，原先直接 return null 会掉进通义/兜底「没听懂」
        if (userId == null) {
            plan.setAdvice("已识别您是在补充长期偏好，但当前未登录，偏好无法写入您的账号。请先登录后再发同样内容；登录后也可在「记忆」面板里手动添加。");
            return plan;
        }
        if (agentUserMemoryService == null) {
            plan.setAdvice("已识别为偏好备注，但记忆服务未启用，暂时无法落库。请联系管理员检查配置。");
            return plan;
        }
        try {
            String note = extractMemoryPreferenceNote(raw);
            if (StringUtils.hasText(note)) {
                agentUserMemoryService.appendPreferenceNote(userId, note);
                plan.setAdvice("好的，我已经把这条偏好记进数据库了。之后生成方案、回答农事时会结合这条记录。你也可以点「记忆」查看或清空。");
            } else {
                plan.setAdvice("想让我记住的内容不太明确。可以说完整些，例如：「请记住：我主要种番茄」或「备注一下：我关心番茄整枝」。也可在「记忆」面板里点「记入上一句」。");
            }
            return plan;
        } catch (Exception e) {
            log.warn("显式偏好写入失败（将改走其它逻辑）: {}", e.getMessage());
            return null;
        }
    }

    /** 从用户输入中剥离"请记住/备注一下"等前缀，提取真正的偏好正文 */
    private static String extractMemoryPreferenceNote(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String note = raw;
        String[] prefixes = {
                "请帮我记住：", "请帮我记住:", "请你记住：", "请你记住:",
                "帮我记住：", "帮我记住:", "请记住：", "请记住:", "请记住",
                "帮我备注：", "帮我备注:", "记个备注：", "记个备注:",
                "加条备注：", "加条备注:", "不要忘记：", "不要忘记:",
                "记下来：", "记下来:", "备注：", "备注:", "备忘：", "备忘:"
        };
        for (String p : prefixes) {
            if (note.startsWith(p)) {
                note = note.substring(p.length()).trim();
                break;
            }
        }
        return StringUtils.hasText(note) ? note : null;
    }

    /** 兜底规则：用户是否在显式要求写入长期偏好 */
    private static boolean isExplicitMemoryPreferenceRequest(String raw, String lower) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        if (raw.contains("请记住") || raw.contains("请你记住") || raw.contains("请帮我记住")
                || raw.contains("帮我记住") || raw.contains("不要忘记") || raw.contains("记下来")) {
            return true;
        }
        if (raw.contains("备注一下") || raw.contains("帮我备注") || raw.contains("记个备注") || raw.contains("加条备注")) {
            return true;
        }
        if (raw.startsWith("备注：") || raw.startsWith("备注:") || raw.startsWith("备忘：") || raw.startsWith("备忘:")) {
            return true;
        }
        return false;
    }
}
    
