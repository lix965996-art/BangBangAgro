package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.CropYieldConfig;
import com.farmland.intel.mapper.CropYieldConfigMapper;
import com.farmland.intel.service.ICropYieldConfigService;
import org.springframework.stereotype.Service;

/**
 * 作物产量配置服务实现类
 */
@Service
public class CropYieldConfigServiceImpl extends ServiceImpl<CropYieldConfigMapper, CropYieldConfig>
        implements ICropYieldConfigService {
}
