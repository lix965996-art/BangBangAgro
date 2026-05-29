package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.AiConfig;

public interface IAiConfigService extends IService<AiConfig> {

    /**
     * 获取指定用户的 AI 配置；若不存在则返回携带默认值的实例（不入库）。
     */
    AiConfig getByUserId(Integer userId);

    /**
     * 保存或更新配置（userId 唯一键，重复则覆盖）。
     */
    AiConfig saveOrUpdateByUserId(AiConfig config);
}
