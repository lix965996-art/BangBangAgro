package com.farmland.intel.service.impl;

import com.farmland.intel.entity.MiniUser;
import com.farmland.intel.mapper.MiniUserMapper;
import com.farmland.intel.service.IMiniUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class MiniUserServiceImpl extends ServiceImpl<MiniUserMapper, MiniUser> implements IMiniUserService {
}
