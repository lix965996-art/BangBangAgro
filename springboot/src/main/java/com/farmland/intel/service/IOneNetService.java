package com.farmland.intel.service;

import java.util.Map;

/**
 * OneNET物联网平台服务接口
 */
public interface IOneNetService {
    
    /**
     * 从OneNET获取设备属性数据
     * @return 设备数据（温度、湿度、LED状态）
     */
    Map<String, Object> getDeviceData();
    
    /**
     * 控制OneNET设备属性
     * @param led LED状态（true-开启，false-关闭）
     * @return 是否成功
     */
    boolean controlLed(boolean led);
    
    /**
     * 同步OneNET数据到数据库
     * @return 是否成功
     */
    boolean syncDataToDatabase();
    
    /**
     * 控制水泵开关（新设备 KK57iNOm8d）
     * @param bump 水泵状态（true-开启，false-关闭）
     * @return 是否成功
     */
    boolean controlBump(boolean bump);
    
    /**
     * 获取新设备数据（温度、湿度、水泵状态）
     * @return 设备数据
     */
    Map<String, Object> getNewDeviceData();
    
    /**
     * 同步新设备（KK57iNOm8d）数据到数据库
     * @return 是否成功
     */
    boolean syncNewDeviceDataToDatabase();
}

