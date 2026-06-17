package com.farmland.intel.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 工具类单元测试。
 * <p>
 * 重点验证 v1.1.0 新增的 {@link Result#success(Object, String)} 重载,
 * 这是 BUG-01 (管理员新建用户无法登录) 修复的关键 — 通过 msg 字段把
 * 初始密码回传给前端 ElMessage 弹窗。
 */
class ResultTest {

    @Test
    void successWithNoArgsShouldReturn200() {
        Result r = Result.success();
        assertEquals(Constants.CODE_200, r.getCode());
        assertNull(r.getData());
    }

    @Test
    void successWithDataShouldKeepDataAndEmptyMsg() {
        String payload = "hello";
        Result r = Result.success(payload);
        assertEquals(Constants.CODE_200, r.getCode());
        assertEquals(payload, r.getData());
        assertEquals("", r.getMsg(), "无 msg 参数时应为空字符串");
    }

    @Test
    void successWithDataAndMsgShouldKeepBoth() {
        // BUG-01 修复场景: 管理员新建用户时,初始密码需通过 msg 字段返回
        String userData = "{username: zhangsan}";
        String tipMsg = "用户已创建,初始密码:Aazhangsan@2026";

        Result r = Result.success(userData, tipMsg);

        assertEquals(Constants.CODE_200, r.getCode());
        assertEquals(userData, r.getData());
        assertEquals(tipMsg, r.getMsg(), "初始密码提示必须能通过 msg 字段返回前端");
    }

    @Test
    void errorShouldUseProvidedCode() {
        Result r = Result.error("404", "找不到");
        assertEquals("404", r.getCode());
        assertEquals("找不到", r.getMsg());
        assertNull(r.getData());
    }
}
