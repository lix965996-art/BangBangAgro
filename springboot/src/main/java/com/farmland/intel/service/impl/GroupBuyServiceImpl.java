package com.farmland.intel.service.impl;

import com.farmland.intel.entity.GroupBuy;
import com.farmland.intel.mapper.GroupBuyMapper;
import com.farmland.intel.service.IGroupBuyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class GroupBuyServiceImpl extends ServiceImpl<GroupBuyMapper, GroupBuy> implements IGroupBuyService {
}
