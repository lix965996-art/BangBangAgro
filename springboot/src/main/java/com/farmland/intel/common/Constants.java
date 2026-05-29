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

    // ========== 设备相关 ==========
    public static final String DEFAULT_DEVICE_NAME = "STM32-001";
    public static final Integer LED_OFF = 0;
    public static final Integer LED_ON = 1;
    
    // ========== 数据源标识 ==========
    public static final String DATA_SOURCE_ONENET = "OneNET";
    public static final String DATA_SOURCE_DATABASE = "Database";
    public static final String DATA_SOURCE_CACHE = "Cache";
    
    // ========== 文件上传 ==========
    public static final String[] ALLOWED_IMAGE_TYPES = {"jpg", "jpeg", "png", "gif"};
    public static final String[] ALLOWED_DOCUMENT_TYPES = {"pdf", "doc", "docx", "xls", "xlsx"};
    public static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    
    // ========== 时间相关 ==========
    public static final int DEFAULT_HISTORY_DAYS = 7;
    public static final int MAX_HISTORY_DAYS = 365;
    public static final int MIN_HISTORY_DAYS = 1;
    
    // ========== 分页相关 ==========
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;
    
    // ========== 农田状态 ==========
    public static final String FARM_STATUS_NORMAL = "正常";
    public static final String FARM_STATUS_WARNING = "预警";
    public static final String FARM_STATUS_DANGER = "危险";
    
    // ========== 消息模板 ==========
    public static final String MSG_SUCCESS = "操作成功";
    public static final String MSG_SAVE_SUCCESS = "保存成功";
    public static final String MSG_DELETE_SUCCESS = "删除成功";
    public static final String MSG_UPDATE_SUCCESS = "更新成功";
    
    public static final String MSG_PARAM_ERROR = "参数错误";
    public static final String MSG_SYSTEM_ERROR = "系统繁忙，请稍后重试";
    public static final String MSG_UNAUTHORIZED = "未授权，请重新登录";
    public static final String MSG_FORBIDDEN = "权限不足";
    
    // ========== OneNET相关 ==========
    public static final String ONENET_METHOD_SHA1 = "sha1";
    public static final long ONENET_TOKEN_EXPIRE_DAYS = 365;
    public static final int ONENET_SYNC_INTERVAL = 30000; // 30秒
    
    // ========== 缓存相关 ==========
    public static final String CACHE_USER_TOKEN = "user:token:";
    public static final String CACHE_USER_MENU = "user:menu:";
    public static final int CACHE_EXPIRE_HOURS = 24;
    
    // ========== 字典类型 ==========
    public static final String DICT_TYPE_ICON = "icon";

    private Constants() {
        // 私有构造函数，防止实例化
    }
}