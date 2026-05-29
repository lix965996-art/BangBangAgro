package com.farmland.intel.agent;

import lombok.Data;

/**
 * 动作执行结果，返回给前端用于提示或继续导航。
 */
@Data
public class AgentActionResult {
    private String id;
    private String type;
    private String status;   // success | failed | skipped | pending-client
    private String message;
    private String route;    // 导航动作需要由前端完成跳转
}
