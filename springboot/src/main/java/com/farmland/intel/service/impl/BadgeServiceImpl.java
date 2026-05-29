package com.farmland.intel.service.impl;

import com.farmland.intel.entity.Badge;
import com.farmland.intel.mapper.BadgeMapper;
import com.farmland.intel.service.IBadgeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BadgeServiceImpl extends ServiceImpl<BadgeMapper, Badge> implements IBadgeService {
}
