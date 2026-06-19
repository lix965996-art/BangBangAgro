package com.farmland.intel.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.farmland.intel.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.farmland.intel.utils.TokenUtils;

/**
 * Spring AI @Tool 注解方法，替代 AgentService 中手动 JSON 工具定义和 switch 分发。
 * 框架自动生成 JSON Schema 并处理 Function Calling 调度。
 */
@Component
@Slf4j
public class AgentTools {

    private final IStatisticService statisticService;
    private final IOneNetService oneNetService;
    private final ISalesService salesService;
    private final IPurchaseService purchaseService;
    private final IInventoryService inventoryService;
    private final IUserService userService;
    private final IRoleService roleService;
    private final INoticeService noticeService;
    private final IOnlineSaleService onlineSaleService;
    private final IKnowledgeService knowledgeService;
    private final IAgentTaskQueueService taskQueueService;
    private final IAiConfigService aiConfigService;

    public AgentTools(IStatisticService statisticService,
                      ObjectProvider<IOneNetService> oneNetServiceProvider,
                      ISalesService salesService,
                      IPurchaseService purchaseService,
                      IInventoryService inventoryService,
                      IUserService userService,
                      IRoleService roleService,
                      INoticeService noticeService,
                      IOnlineSaleService onlineSaleService,
                      IKnowledgeService knowledgeService,
                      IAgentTaskQueueService taskQueueService,
                      IAiConfigService aiConfigService) {
        this.statisticService = statisticService;
        this.oneNetService = oneNetServiceProvider.getIfAvailable();
        this.salesService = salesService;
        this.purchaseService = purchaseService;
        this.inventoryService = inventoryService;
        this.userService = userService;
        this.roleService = roleService;
        this.noticeService = noticeService;
        this.onlineSaleService = onlineSaleService;
        this.knowledgeService = knowledgeService;
        this.taskQueueService = taskQueueService;
        this.aiConfigService = aiConfigService;
    }

    // ==================== 查询工具 (Query Tools) ====================

    @Tool(description = "获取所有农田的基本信息和环境数据，包括名称、作物、面积、土壤湿度、温度等")
    public String getAllFarms() {
        List<Statistic> farms = listFarmsOrEmpty();
        JSONArray result = new JSONArray();
        for (Statistic farm : farms) {
            result.add(farmToJson(farm));
        }
        return result.toString();
    }

    @Tool(description = "获取指定农田的详细信息")
    public String getFarmDetail(
            @ToolParam(description = "农田名称或关键字") String farmName) {
        List<Statistic> farms = listFarmsOrEmpty();
        for (Statistic farm : farms) {
            if (farm.getFarm() != null && farm.getFarm().contains(farmName)) {
                return farmToJson(farm).toString();
            }
        }
        return "{\"error\": \"未找到农田: " + farmName + "\"}";
    }

    @Tool(description = "查询土壤湿度低于阈值、需要灌溉的农田列表")
    public String getFarmsNeedIrrigation(
            @ToolParam(description = "土壤湿度阈值，低于此值需要灌溉，默认30%") int threshold) {
        if (threshold <= 0) threshold = 30;
        List<Statistic> farms = listFarmsOrEmpty();
        JSONArray result = new JSONArray();
        for (Statistic farm : farms) {
            Integer soilHumidity = farm.getSoilhumidity();
            if (soilHumidity != null && soilHumidity < threshold) {
                JSONObject item = farmToJson(farm);
                item.put("need_irrigation", true);
                item.put("urgency", soilHumidity < 20 ? "紧急" : "一般");
                result.add(item);
            }
        }
        if (result.isEmpty()) {
            return "{\"message\": \"所有农田土壤湿度正常，无需灌溉\", \"threshold\": " + threshold + "}";
        }
        return result.toString();
    }

    @Tool(description = "获取 IoT 设备的实时状态，包括空气温湿度、土壤湿度(soil)、补光灯(led)、风扇(fan)、报警器(buzzer)、水泵(pump)等。风扇和报警器由单片机按温度/土壤自动控制，只读状态")
    public String getDeviceStatus() {
        if (oneNetService == null) {
            return "{\"error\": \"OneNET 服务不可用\"}";
        }
        try {
            Map<String, Object> data = oneNetService.getDeviceData();
            return new JSONObject(data).toString();
        } catch (Exception e) {
            return "{\"error\": \"获取设备状态失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "获取所有农田的环境监测数据，包括温度、湿度、光照、CO2等")
    public String getEnvironmentData() {
        JSONObject result = new JSONObject();

        if (oneNetService != null) {
            try {
                Map<String, Object> data = oneNetService.getDeviceData();
                result.put("realtime", data);
            } catch (Exception e) {
                result.put("realtime_error", e.getMessage());
            }
        }

        List<Statistic> farms = listFarmsOrEmpty();
        JSONArray farmEnvs = new JSONArray();
        for (Statistic farm : farms) {
            JSONObject env = new JSONObject();
            env.put("farm_name", farm.getFarm());
            env.put("temperature", farm.getTemperature());
            env.put("air_humidity", farm.getAirhumidity());
            env.put("soil_humidity", farm.getSoilhumidity());
            env.put("light", farm.getLight());
            env.put("co2", farm.getCarbon());
            env.put("ph", farm.getPh());
            farmEnvs.add(env);
        }
        result.put("farms", farmEnvs);

        return result.toString();
    }

    @Tool(description = "获取农产品销售数据和收入统计，包括今年收入、销售记录等")
    public String getSalesData() {
        JSONObject result = new JSONObject();
        if (salesService == null) {
            result.put("error", "销售服务未配置");
            return result.toString();
        }
        try {
            List<Sales> salesList = salesService.list();
            BigDecimal totalIncome = BigDecimal.ZERO;
            JSONArray records = new JSONArray();
            for (Sales sale : salesList) {
                JSONObject record = new JSONObject();
                record.put("id", sale.getId());
                record.put("product", sale.getProduct());
                record.put("price", sale.getPrice());
                record.put("number", sale.getNumber());
                record.put("buyer", sale.getBuyer());
                record.put("shipper", sale.getShipper());
                if (sale.getPrice() != null && sale.getNumber() != null) {
                    BigDecimal income = sale.getPrice().multiply(new BigDecimal(sale.getNumber()));
                    record.put("income", income);
                    totalIncome = totalIncome.add(income);
                }
                records.add(record);
            }
            result.put("total_records", salesList.size());
            result.put("total_income", totalIncome);
            result.put("records", records);
        } catch (Exception e) {
            result.put("error", "获取销售数据失败: " + e.getMessage());
        }
        return result.toString();
    }

    @Tool(description = "获取物资采购数据（投入成本），包括购买的农资、机械设备等的支出记录")
    public String getPurchaseData() {
        JSONObject result = new JSONObject();
        if (purchaseService == null) {
            result.put("error", "采购服务未配置");
            return result.toString();
        }
        try {
            List<Purchase> purchaseList = purchaseService.list();
            BigDecimal totalCost = BigDecimal.ZERO;
            JSONArray records = new JSONArray();
            for (Purchase purchase : purchaseList) {
                JSONObject record = new JSONObject();
                record.put("id", purchase.getId());
                record.put("product", purchase.getProduct());
                record.put("price", purchase.getPrice());
                record.put("number", purchase.getNumber());
                record.put("provider", purchase.getProvider());
                record.put("purchaser", purchase.getPurchaser());
                if (purchase.getPrice() != null && purchase.getNumber() != null) {
                    BigDecimal cost = purchase.getPrice().multiply(new BigDecimal(purchase.getNumber()));
                    record.put("cost", cost);
                    totalCost = totalCost.add(cost);
                }
                records.add(record);
            }
            result.put("total_records", purchaseList.size());
            result.put("total_cost", totalCost);
            result.put("records", records);
        } catch (Exception e) {
            result.put("error", "获取采购数据失败: " + e.getMessage());
        }
        return result.toString();
    }

    @Tool(description = "获取仓库库存数据（当前拥有的物资），包括农资、工具、设备的库存数量和预警状态")
    public String getInventoryData() {
        JSONObject result = new JSONObject();
        if (inventoryService == null) {
            result.put("error", "库存服务未配置");
            return result.toString();
        }
        try {
            List<Inventory> inventoryList = inventoryService.list();
            JSONArray records = new JSONArray();
            for (Inventory inv : inventoryList) {
                JSONObject record = new JSONObject();
                record.put("id", inv.getId());
                record.put("produce", inv.getProduce());
                record.put("number", inv.getNumber());
                record.put("warehouse", inv.getWarehouse());
                records.add(record);
            }
            result.put("total_items", inventoryList.size());
            result.put("records", records);
        } catch (Exception e) {
            result.put("error", "获取库存数据失败: " + e.getMessage());
        }
        return result.toString();
    }

    @Tool(description = "获取经营利润分析，自动计算：销售总收入 - 采购总成本 = 毛利润，以及利润率")
    public String getProfitAnalysis() {
        JSONObject result = new JSONObject();
        try {
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            if (salesService != null) {
                for (Sales sale : salesService.list()) {
                    if (sale.getPrice() != null && sale.getNumber() != null) {
                        totalIncome = totalIncome.add(sale.getPrice().multiply(new BigDecimal(sale.getNumber())));
                    }
                }
            }
            if (purchaseService != null) {
                for (Purchase p : purchaseService.list()) {
                    if (p.getPrice() != null && p.getNumber() != null) {
                        totalCost = totalCost.add(p.getPrice().multiply(new BigDecimal(p.getNumber())));
                    }
                }
            }

            BigDecimal profit = totalIncome.subtract(totalCost);
            BigDecimal margin = totalIncome.compareTo(BigDecimal.ZERO) > 0
                    ? profit.divide(totalIncome, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;

            result.put("total_income", totalIncome);
            result.put("total_cost", totalCost);
            result.put("gross_profit", profit);
            result.put("profit_margin_percent", margin);
        } catch (Exception e) {
            result.put("error", "利润分析失败: " + e.getMessage());
        }
        return result.toString();
    }

    @Tool(description = "获取系统用户统计数据，包括总用户数、角色分布、活跃情况等")
    public String getUserStatistics() {
        JSONObject result = new JSONObject();
        try {
            if (userService != null) {
                List<User> users = userService.list();
                result.put("total_users", users.size());
                Map<String, Long> roleCount = users.stream()
                        .filter(u -> u.getRole() != null)
                        .collect(java.util.stream.Collectors.groupingBy(User::getRole, java.util.stream.Collectors.counting()));
                result.put("role_distribution", roleCount);
            }
            if (roleService != null) {
                result.put("total_roles", roleService.list().size());
            }
        } catch (Exception e) {
            result.put("error", "获取用户统计失败: " + e.getMessage());
        }
        return result.toString();
    }

    @Tool(description = "获取系统整体运营概览，包括农田、用户、销售、库存、公告等全局统计")
    public String getSystemOverview() {
        JSONObject result = new JSONObject();
        try {
            List<Statistic> farms = listFarmsOrEmpty();
            result.put("total_farms", farms.size());
            if (salesService != null) result.put("total_sales", salesService.list().size());
            if (purchaseService != null) result.put("total_purchases", purchaseService.list().size());
            if (inventoryService != null) result.put("total_inventory_items", inventoryService.list().size());
            if (userService != null) result.put("total_users", userService.list().size());
            if (noticeService != null) result.put("total_notices", noticeService.list().size());
        } catch (Exception e) {
            result.put("error", "获取系统概览失败: " + e.getMessage());
        }
        return result.toString();
    }

    @Tool(description = "获取在线销售平台的农作物销售数据和订单统计")
    public String getOnlineSaleData() {
        JSONObject result = new JSONObject();
        if (onlineSaleService == null) {
            result.put("error", "在线销售服务未配置");
            return result.toString();
        }
        try {
            List<OnlineSale> list = onlineSaleService.list();
            result.put("total_items", list.size());
            JSONArray records = new JSONArray();
            for (OnlineSale item : list) {
                JSONObject r = new JSONObject();
                r.put("id", item.getId());
                r.put("produce", item.getProduce());
                r.put("price", item.getPrice());
                r.put("quantity", item.getQuantity());
                r.put("status", item.getStatus());
                records.add(r);
            }
            result.put("records", records);
        } catch (Exception e) {
            result.put("error", "获取在线销售数据失败: " + e.getMessage());
        }
        return result.toString();
    }

    @Tool(description = "综合评估农场经营健康度，从利润率、库存安全水平等真实维度打分并给出诊断建议")
    public String getBusinessHealthScore() {
        JSONObject profit = new JSONObject(getProfitAnalysis());
        JSONObject result = new JSONObject();
        result.put("profit_analysis", profit);

        if (profit.containsKey("error")) {
            result.put("health_score", 0);
            result.put("advice", "无法获取利润数据，请检查销售/采购服务配置");
            return result.toString();
        }

        // 真实利润率（来自 getProfitAnalysis 的真实聚合）
        Double marginObj = profit.getDouble("profit_margin_percent");
        double margin = marginObj != null ? marginObj : 0.0;

        // 真实低库存占比（number < safeStock）
        int total = 0, low = 0;
        if (inventoryService != null) {
            for (Inventory inv : inventoryService.list()) {
                total++;
                if (inv.getNumber() != null && inv.getSafeStock() != null
                        && inv.getNumber() < inv.getSafeStock()) {
                    low++;
                }
            }
        }
        double lowRatio = total > 0 ? (double) low / total : 0.0;

        // 评分全部基于真实数据：利润率分量(0-70) + 库存安全分量(0-30)
        double profitScore = Math.max(0, Math.min(70, 35 + margin)); // 0%→35、35%→70、负利润更低
        double stockScore = 30 * (1 - lowRatio);
        int score = (int) Math.round(Math.max(0, Math.min(100, profitScore + stockScore)));

        result.put("health_score", score);
        result.put("profit_margin_percent", margin);
        result.put("inventory_total", total);
        result.put("inventory_low_stock", low);

        StringBuilder advice = new StringBuilder();
        if (margin <= 0) advice.append("当前毛利为负或为零，需控制采购成本或提升售价；");
        else if (margin < 15) advice.append("利润率偏低，关注成本结构；");
        if (lowRatio > 0.3) advice.append("超三成物资低于安全库存，及时补货；");
        if (advice.length() == 0) advice.append("经营指标健康，保持当前节奏");
        result.put("advice", advice.toString());
        return result.toString();
    }

    @Tool(description = "汇总销售/采购/库存的真实经营聚合数据与低库存清单。注意：系统未存交易时间序列，不提供涨跌趋势预测")
    public String getTrendAnalysis() {
        JSONObject result = new JSONObject();
        JSONObject profit = new JSONObject(getProfitAnalysis());
        result.put("total_income", profit.getOrDefault("total_income", 0));
        result.put("total_cost", profit.getOrDefault("total_cost", 0));
        result.put("gross_profit", profit.getOrDefault("gross_profit", 0));

        // 真实低库存物资清单
        JSONArray lowStock = new JSONArray();
        if (inventoryService != null) {
            for (Inventory inv : inventoryService.list()) {
                if (inv.getNumber() != null && inv.getSafeStock() != null
                        && inv.getNumber() < inv.getSafeStock()) {
                    JSONObject o = new JSONObject();
                    o.put("produce", inv.getProduce());
                    o.put("number", inv.getNumber());
                    o.put("safe_stock", inv.getSafeStock());
                    lowStock.add(o);
                }
            }
        }
        result.put("low_stock_items", lowStock);
        // 诚实声明：无时间序列，不臆造趋势方向
        result.put("note", "以上为实时聚合的真实数据；系统未存储交易时间序列，无法计算涨跌趋势，故不提供方向性预测。");
        result.put("advice", lowStock.isEmpty()
                ? "库存均高于安全水平；建议持续记录交易时间，以支持后续趋势分析"
                : "存在低于安全库存的物资，建议优先补货");
        return result.toString();
    }

    @Tool(description = "生成全面的经营分析报告，整合财务、运营、环境、库存等全部数据的深度洞察")
    public String getComprehensiveReport() {
        JSONObject result = new JSONObject();
        result.put("farms", new JSONObject(getAllFarms()));
        result.put("profit", new JSONObject(getProfitAnalysis()));
        result.put("sales_summary", new JSONObject(getSalesData()));
        result.put("inventory", new JSONObject(getInventoryData()));
        result.put("generated_at", new java.util.Date().toString());
        return result.toString();
    }

    // ==================== 执行工具 (Action Tools) ====================

    @Tool(description = "控制农田灌溉系统（水泵）的开关。当检测到土壤湿度过低时可以自动开启灌溉")
    public String controlIrrigation(
            @ToolParam(description = "操作类型：on=开启灌溉，off=关闭灌溉") String action,
            @ToolParam(description = "目标农田名称（可选）") String farmName) {
        if (oneNetService == null) {
            return "{\"error\": \"OneNET 服务不可用\"}";
        }
        try {
            if ("on".equalsIgnoreCase(action)) {
                boolean ok = oneNetService.controlPump(true);
                return ok
                        ? "{\"success\": true, \"action\": \"irrigation_on\", \"farm\": \"" + (farmName != null ? farmName : "默认农田") + "\"}"
                        : "{\"success\": false, \"action\": \"irrigation_on\", \"error\": \"OneNET 拒绝或下发失败\"}";
            } else if ("off".equalsIgnoreCase(action)) {
                boolean ok = oneNetService.controlPump(false);
                return ok
                        ? "{\"success\": true, \"action\": \"irrigation_off\", \"farm\": \"" + (farmName != null ? farmName : "默认农田") + "\"}"
                        : "{\"success\": false, \"action\": \"irrigation_off\", \"error\": \"OneNET 拒绝或下发失败\"}";
            }
            return "{\"error\": \"无效操作: " + action + "\"}";
        } catch (Exception e) {
            return "{\"error\": \"控制灌溉失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "控制农田补光灯的开关。当检测到光照不足时可以自动开启补光")
    public String controlLed(
            @ToolParam(description = "操作类型：on=开启补光灯，off=关闭补光灯") String action,
            @ToolParam(description = "目标农田名称（可选）") String farmName) {
        if (oneNetService == null) {
            return "{\"error\": \"OneNET 服务不可用\"}";
        }
        try {
            if ("on".equalsIgnoreCase(action)) {
                boolean ok = oneNetService.controlLed(true);
                return ok
                        ? "{\"success\": true, \"action\": \"led_on\", \"farm\": \"" + (farmName != null ? farmName : "默认农田") + "\"}"
                        : "{\"success\": false, \"action\": \"led_on\", \"error\": \"OneNET 拒绝或下发失败\"}";
            } else if ("off".equalsIgnoreCase(action)) {
                boolean ok = oneNetService.controlLed(false);
                return ok
                        ? "{\"success\": true, \"action\": \"led_off\", \"farm\": \"" + (farmName != null ? farmName : "默认农田") + "\"}"
                        : "{\"success\": false, \"action\": \"led_off\", \"error\": \"OneNET 拒绝或下发失败\"}";
            }
            return "{\"error\": \"无效操作: " + action + "\"}";
        } catch (Exception e) {
            return "{\"error\": \"控制补光灯失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "创建物资采购订单。当检测到库存不足时，AI可以自动创建采购订单补充物资。注意：采购=高风险，半审批/全审批模式下会提交等用户确认，不会立即下单。")
    public String createPurchaseOrder(
            @ToolParam(description = "采购物资名称，如：有机肥料、农药、种子等") String product,
            @ToolParam(description = "采购数量") int number,
            @ToolParam(description = "供应商名称") String provider,
            @ToolParam(description = "单价（元）") double price) {
        if (purchaseService == null) {
            return "{\"error\": \"采购服务未配置\"}";
        }
        JSONObject params = new JSONObject();
        params.put("product", product);
        params.put("number", number);
        params.put("provider", provider);
        params.put("price", price);
        return dispatchWrite("create_purchase", params, "AI创建采购订单: " + product + " x" + number);
    }

    @Tool(description = "创建农产品销售订单。记录农产品销售信息。注意：销售=高风险，半审批/全审批模式下会提交等用户确认。")
    public String createSalesOrder(
            @ToolParam(description = "销售产品名称") String product,
            @ToolParam(description = "销售数量") int number,
            @ToolParam(description = "买家名称") String buyer,
            @ToolParam(description = "单价（元）") double price) {
        if (salesService == null) {
            return "{\"error\": \"销售服务未配置\"}";
        }
        JSONObject params = new JSONObject();
        params.put("product", product);
        params.put("number", number);
        params.put("buyer", buyer);
        params.put("price", price);
        return dispatchWrite("create_sale", params, "AI创建销售订单: " + product + " x" + number);
    }

    @Tool(description = "更新仓库库存数量。可以增加或减少库存。库存调整=中风险，半审批模式下通常自动执行。")
    public String updateInventory(
            @ToolParam(description = "物资名称") String product,
            @ToolParam(description = "库存变化量（正数=增加，负数=减少）") int change,
            @ToolParam(description = "变更原因，如：采购入库、销售出库、损耗等") String reason) {
        if (inventoryService == null) {
            return "{\"error\": \"库存服务未配置\"}";
        }
        JSONObject params = new JSONObject();
        params.put("product", product);
        params.put("change", change);
        params.put("reason", reason);
        return dispatchWrite("update_inventory", params, "AI更新库存: " + product + " 变化" + (change > 0 ? "+" : "") + change);
    }

    @Tool(description = "向系统用户发送通知消息。当发现重要问题或需要提醒时使用。通知=低风险，通常自动发送。")
    public String sendNotification(
            @ToolParam(description = "通知标题") String title,
            @ToolParam(description = "通知内容") String content,
            @ToolParam(description = "通知级别：info=普通，warning=警告，urgent=紧急") String level) {
        if (noticeService == null) {
            return "{\"error\": \"通知服务未配置\"}";
        }
        JSONObject params = new JSONObject();
        params.put("title", title);
        params.put("content", content);
        params.put("level", level);
        return dispatchWrite("send_notification", params, "AI发送通知: " + title);
    }

    // ==================== 写操作审批分发器 ====================

    /** 每个写动作的风险等级：high=花钱/收钱，medium=可逆改库存，low=发通知 */
    private static final Map<String, String> ACTION_RISK = Map.of(
            "create_purchase", "high",
            "create_sale", "high",
            "update_inventory", "medium",
            "send_notification", "low");

    /**
     * 写操作统一入口：按当前用户的审批策略决定 立即执行 / 入队等审批。
     *  full_auto=全部立即；full_approval=全部入队；semi_approval=low/medium立即，high入队。
     *  自动与入队都落 agent_task_queue 一条记录，作为完整审计链。
     */
    private String dispatchWrite(String actionType, JSONObject params, String reasoning) {
        User user = TokenUtils.getCurrentUser();
        if (user == null) {
            return "{\"success\": false, \"error\": \"未登录，AI 无法执行写操作\"}";
        }
        if (taskQueueService == null) {
            return "{\"success\": false, \"error\": \"任务队列服务未配置\"}";
        }
        String policy = resolvePolicy(user.getId());
        String risk = ACTION_RISK.getOrDefault(actionType, "high");
        boolean auto = decideAuto(policy, risk);
        String paramsJson = params.toString();

        if (auto) {
            String result = executeAction(actionType, params);
            boolean failed = result != null && result.contains("\"error\"");
            // 审计：落一条记录留痕每一次 AI 写操作（自动执行也要可追溯）
            try {
                AgentTaskQueue task = taskQueueService.createTask(
                        null, taskTypeOf(actionType), priorityOf(risk), risk, true,
                        params.getStr("farm"), actionType, paramsJson, reasoning, null, null);
                task.setUserId(user.getId());
                taskQueueService.updateById(task);
                if (failed) {
                    taskQueueService.markFailed(task.getTaskId(), result);
                } else {
                    taskQueueService.markCompleted(task.getTaskId(), result);
                }
            } catch (Exception auditEx) {
                log.warn("AI写操作审计落库失败: {}", auditEx.getMessage());
            }
            return failed ? result
                    : "{\"success\": true, \"mode\": \"auto\", \"action\": \"" + actionType + "\", \"result\": " + result + "}";
        }

        // 入队等审批：先去重，防 LLM 反复提交相同任务
        if (taskQueueService.hasPendingDuplicate(user.getId(), actionType, paramsJson)) {
            return "{\"success\": false, \"mode\": \"duplicate\", \"message\": \"相同任务已提交审批，待确认，无需重复提交\"}";
        }
        try {
            AgentTaskQueue task = taskQueueService.createTask(
                    null, taskTypeOf(actionType), priorityOf(risk), risk, false,
                    params.getStr("farm"), actionType, paramsJson, reasoning, null, null);
            task.setUserId(user.getId());
            taskQueueService.updateById(task);
            return "{\"success\": true, \"mode\": \"pending_approval\", \"taskId\": \"" + task.getTaskId()
                    + "\", \"action\": \"" + actionType + "\", \"message\": \"已按你的审批策略提交，等待确认后执行\"}";
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"提交审批失败: " + e.getMessage() + "\"}";
        }
    }

    /** 读取当前用户审批策略，缺失/异常一律回退 semi_approval */
    private String resolvePolicy(Integer userId) {
        if (aiConfigService == null) return "semi_approval";
        try {
            AiConfig cfg = aiConfigService.getByUserId(userId);
            String p = cfg != null ? cfg.getAiActionPolicy() : null;
            if (p == null || p.isBlank()) return "semi_approval";
            return p;
        } catch (Exception e) {
            return "semi_approval";
        }
    }

    private boolean decideAuto(String policy, String risk) {
        if ("full_auto".equals(policy)) return true;
        if ("full_approval".equals(policy)) return false;
        return !"high".equals(risk); // semi_approval
    }

    private String taskTypeOf(String actionType) {
        switch (actionType) {
            case "create_purchase": return "purchase";
            case "create_sale": return "sales";
            case "update_inventory": return "inventory";
            case "send_notification": return "notification";
            default: return actionType;
        }
    }

    private String priorityOf(String risk) {
        switch (risk) {
            case "high": return "high";
            case "medium": return "medium";
            default: return "low";
        }
    }

    /**
     * 真正执行写动作（自动执行 + 审批通过后执行 都走这里）。
     * actionType → 对应 service 调用，参数从 params(JSON) 取。
     */
    public String executeAction(String actionType, JSONObject p) {
        if (p == null) p = new JSONObject();
        try {
            switch (actionType) {
                case "create_purchase": {
                    if (purchaseService == null) return "{\"error\": \"采购服务未配置\"}";
                    Purchase purchase = new Purchase();
                    purchase.setProduct(p.getStr("product"));
                    purchase.setNumber(p.getInt("number"));
                    purchase.setProvider(p.getStr("provider"));
                    Double price = p.getDouble("price");
                    purchase.setPrice(price != null ? new BigDecimal(String.valueOf(price)) : BigDecimal.ZERO);
                    purchaseService.save(purchase);
                    return "{\"success\": true, \"message\": \"采购订单已创建: " + p.getStr("product") + " x" + p.getInt("number") + "\"}";
                }
                case "create_sale": {
                    if (salesService == null) return "{\"error\": \"销售服务未配置\"}";
                    Sales sale = new Sales();
                    sale.setProduct(p.getStr("product"));
                    sale.setNumber(p.getInt("number"));
                    sale.setBuyer(p.getStr("buyer"));
                    Double price = p.getDouble("price");
                    sale.setPrice(price != null ? new BigDecimal(String.valueOf(price)) : BigDecimal.ZERO);
                    salesService.save(sale);
                    return "{\"success\": true, \"message\": \"销售订单已创建: " + p.getStr("product") + " x" + p.getInt("number") + "\"}";
                }
                case "update_inventory": {
                    if (inventoryService == null) return "{\"error\": \"库存服务未配置\"}";
                    String product = p.getStr("product");
                    int change = p.getInt("change", 0);
                    List<Inventory> list = inventoryService.list();
                    Inventory target = null;
                    for (Inventory inv : list) {
                        if (product != null && product.equals(inv.getProduce())) {
                            target = inv;
                            break;
                        }
                    }
                    if (target != null) {
                        int newNumber = (target.getNumber() != null ? target.getNumber() : 0) + change;
                        target.setNumber(Math.max(0, newNumber));
                        inventoryService.updateById(target);
                    } else {
                        Inventory inv = new Inventory();
                        inv.setProduce(product);
                        inv.setNumber(Math.max(0, change));
                        inventoryService.save(inv);
                    }
                    return "{\"success\": true, \"message\": \"库存已更新: " + product + " 变化" + (change > 0 ? "+" : "") + change + "\"}";
                }
                case "send_notification": {
                    if (noticeService == null) return "{\"error\": \"通知服务未配置\"}";
                    Notice notice = new Notice();
                    notice.setName(p.getStr("title"));
                    notice.setContent(p.getStr("content"));
                    noticeService.save(notice);
                    return "{\"success\": true, \"message\": \"通知已发送: " + p.getStr("title") + "\"}";
                }
                default:
                    return "{\"error\": \"未知动作类型: " + actionType + "\"}";
            }
        } catch (Exception e) {
            return "{\"error\": \"执行失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "查询农业知识库，获取作物病害、灌溉策略、施肥指南等专业农业知识。当需要农业专业知识辅助决策时调用。")
    public String searchKnowledgeBase(
            @ToolParam(description = "搜索关键词或问题描述") String query,
            @ToolParam(description = "知识分类过滤（可选）：crop_disease/irrigation/fertilizer/pest/general") String category) {
        if (knowledgeService == null) {
            return "{\"error\": \"知识库服务未配置\"}";
        }
        try {
            List<?> results = knowledgeService.search(query, category, 5);
            return new JSONArray(results).toString();
        } catch (Exception e) {
            return "{\"error\": \"查询知识库失败: " + e.getMessage() + "\"}";
        }
    }

    // ==================== 内部辅助方法 ====================

    private List<Statistic> listFarmsOrEmpty() {
        try {
            return statisticService != null ? statisticService.list() : List.of();
        } catch (Exception e) {
            log.warn("获取农田数据失败", e);
            return List.of();
        }
    }

    private JSONObject farmToJson(Statistic farm) {
        JSONObject json = new JSONObject();
        json.put("farm_name", farm.getFarm());
        json.put("crop", farm.getCrop());
        json.put("area", farm.getArea());
        json.put("temperature", farm.getTemperature());
        json.put("air_humidity", farm.getAirhumidity());
        json.put("soil_humidity", farm.getSoilhumidity());
        json.put("light", farm.getLight());
        json.put("co2", farm.getCarbon());
        json.put("ph", farm.getPh());
        return json;
    }
}
