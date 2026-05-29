package com.farmland.intel.service;

/**
 * 通义千问 AI 服务接口
 */
public interface IQwenService {
    // 定义一个方法：向 AI 提问
    String askAgriExpert(String userQuestion, double indoorTemp, double indoorHumidity, double outdoorTemp);
}