package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.UserScoreSnapshot;
import com.farmland.intel.mapper.UserScoreSnapshotMapper;
import com.farmland.intel.service.IUserScoreSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评分快照服务实现。批量"最新快照"查询走 JdbcTemplate (一条 JOIN+GROUP BY),
 * 与 AchievementController 的 raw GROUP BY 风格一致。
 */
@Service
@Slf4j
public class UserScoreSnapshotServiceImpl
        extends ServiceImpl<UserScoreSnapshotMapper, UserScoreSnapshot>
        implements IUserScoreSnapshotService {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public UserScoreSnapshot getLatestByUser(Integer userId) {
        if (jdbcTemplate == null || userId == null) {
            return null;
        }
        try {
            List<UserScoreSnapshot> list = jdbcTemplate.query(
                    "SELECT * FROM user_score_snapshot WHERE user_id = ? ORDER BY window_end DESC LIMIT 1",
                    (rs, i) -> mapRow(rs), userId);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("[评分快照] getLatestByUser({}) 失败: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<Integer, UserScoreSnapshot> getLatestByUserIds(List<Integer> userIds) {
        Map<Integer, UserScoreSnapshot> result = new HashMap<>();
        if (jdbcTemplate == null || userIds == null || userIds.isEmpty()) {
            return result;
        }
        try {
            String placeholders = userIds.stream().map(x -> "?").collect(Collectors.joining(","));
            String sql = "SELECT s.* FROM user_score_snapshot s JOIN ("
                    + "SELECT user_id, MAX(window_end) AS we FROM user_score_snapshot "
                    + "WHERE user_id IN (" + placeholders + ") GROUP BY user_id"
                    + ") m ON s.user_id = m.user_id AND s.window_end = m.we";
            List<UserScoreSnapshot> list = jdbcTemplate.query(sql,
                    (rs, i) -> mapRow(rs), userIds.toArray());
            for (UserScoreSnapshot s : list) {
                result.put(s.getUserId(), s);
            }
        } catch (Exception e) {
            log.warn("[评分快照] getLatestByUserIds 失败: {}", e.getMessage());
        }
        return result;
    }

    private UserScoreSnapshot mapRow(ResultSet rs) throws java.sql.SQLException {
        UserScoreSnapshot s = new UserScoreSnapshot();
        s.setId(rs.getLong("id"));
        s.setUserId(rs.getInt("user_id"));
        s.setWindowStart(rs.getTimestamp("window_start"));
        s.setWindowEnd(rs.getTimestamp("window_end"));
        s.setAttendanceSub(rs.getBigDecimal("attendance_sub"));
        s.setAlertSub(rs.getBigDecimal("alert_sub"));
        s.setAiSub(rs.getBigDecimal("ai_sub"));
        s.setApprovalSub(rs.getBigDecimal("approval_sub"));
        s.setKnowledgeSub(rs.getBigDecimal("knowledge_sub"));
        s.setTotal(rs.getBigDecimal("total"));
        s.setGrade(rs.getString("grade"));
        s.setCommentary(rs.getString("commentary"));
        s.setDataThin(rs.getInt("data_thin"));
        s.setIsOverride(rs.getInt("is_override"));
        Timestamp c = rs.getTimestamp("computed_at");
        s.setComputedAt(c != null ? new Date(c.getTime()) : null);
        return s;
    }
}
