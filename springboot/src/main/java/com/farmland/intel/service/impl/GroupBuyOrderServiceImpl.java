package com.farmland.intel.service.impl;

import com.farmland.intel.entity.GroupBuyOrder;
import com.farmland.intel.mapper.GroupBuyOrderMapper;
import com.farmland.intel.service.IGroupBuyOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class GroupBuyOrderServiceImpl extends ServiceImpl<GroupBuyOrderMapper, GroupBuyOrder> implements IGroupBuyOrderService {
}
