package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.OnlineSale;
import com.farmland.intel.mapper.OnlineSaleMapper;
import com.farmland.intel.service.IOnlineSaleService;
import org.springframework.stereotype.Service;

/**
 * 农作物在线销售Service实现类
 */
@Service
public class OnlineSaleServiceImpl extends ServiceImpl<OnlineSaleMapper, OnlineSale> implements IOnlineSaleService {
}
