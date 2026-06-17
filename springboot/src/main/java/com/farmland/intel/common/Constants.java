package com.farmland.intel.common;

/**
 * 系统常量类
 * 统一管理系统中使用的常量值
 */
public class Constants {

    // ========== 响应状态码 ==========
    public static final String CODE_200 = "200"; // 成功
    public static final String CODE_400 = "400"; // 请求参数错误
    public static final String CODE_401 = "401"; // 未授权
    public static final String CODE_403 = "403"; // 权限不足
    public static final String CODE_404 = "404"; // 资源不存在
    public static final String CODE_500 = "500"; // 系统错误
    public static final String CODE_600 = "600"; // 业务异常

    // ========== 用户角色 ==========
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_USER = "ROLE_USER";

    // ========== 字典类型 ==========
    public static final String DICT_TYPE_ICON = "icon";

    private Constants() {
        // 私有构造函数，防止实例化
    }
}
