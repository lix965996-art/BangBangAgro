package com.farmland.intel.agent;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个可执行动作的描述，包含前端确认所需的信息。
 */
@Data
public class AgentAction {
    private String id;
    private String type;       // navigate | irrigation_on | irrigation_off | led_on | led_off | create_farm | delete_farm
    private String title;
    private String description;
    private String route;      // 仅当 type=navigate 时使用
    private String target;     // 目标农田等
    private String riskLevel;  // low | medium | high
    private Map<String, Object> params = new HashMap<>();
}
