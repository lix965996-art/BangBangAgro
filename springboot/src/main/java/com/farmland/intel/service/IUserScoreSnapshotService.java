package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.UserScoreSnapshot;

import java.util.List;
import java.util.Map;

/**
 * 周滚动评分快照服务
 */
public interface IUserScoreSnapshotService extends IService<UserScoreSnapshot> {

    /** 取某用户最新一行快照 (按 window_end 倒序), 无则返回 null */
    UserScoreSnapshot getLatestByUser(Integer userId);

    /** 批量取若干用户的最新快照 (一条 JOIN+GROUP BY), key=userId */
    Map<Integer, UserScoreSnapshot> getLatestByUserIds(List<Integer> userIds);
}
