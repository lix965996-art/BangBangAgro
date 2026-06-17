package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.ScoreAdjustment;
import com.farmland.intel.mapper.ScoreAdjustmentMapper;
import com.farmland.intel.service.IScoreAdjustmentService;
import org.springframework.stereotype.Service;

@Service
public class ScoreAdjustmentServiceImpl
        extends ServiceImpl<ScoreAdjustmentMapper, ScoreAdjustment>
        implements IScoreAdjustmentService {
}
