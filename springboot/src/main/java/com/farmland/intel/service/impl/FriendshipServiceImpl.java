package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.ChatMessage;
import com.farmland.intel.entity.FriendRequest;
import com.farmland.intel.entity.Friendship;
import com.farmland.intel.entity.User;
import com.farmland.intel.mapper.ChatMessageMapper;
import com.farmland.intel.mapper.FriendRequestMapper;
import com.farmland.intel.mapper.FriendshipMapper;
import com.farmland.intel.mapper.UserMapper;
import com.farmland.intel.service.IFriendshipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FriendshipServiceImpl extends ServiceImpl<FriendshipMapper, Friendship>
        implements IFriendshipService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private FriendRequestMapper friendRequestMapper;

    @Override
    public User searchByUid(String uid, Integer currentUserId) {
        if (uid == null || uid.trim().isEmpty()) return null;
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("uid", uid.trim());
        User user = userMapper.selectOne(qw);
        if (user == null || user.getId().equals(currentUserId)) return null;
        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional
    public String addFriend(Integer userId, Integer friendId) {
        if (isFriend(userId, friendId)) return "already";

        Friendship f1 = new Friendship();
        f1.setUserId(userId);
        f1.setFriendId(friendId);
        save(f1);

        Friendship f2 = new Friendship();
        f2.setUserId(friendId);
        f2.setFriendId(userId);
        save(f2);

        return "added";
    }

    @Override
    @Transactional
    public void removeFriend(Integer userId, Integer friendId) {
        QueryWrapper<Friendship> qw1 = new QueryWrapper<>();
        qw1.eq("user_id", userId).eq("friend_id", friendId);
        remove(qw1);

        QueryWrapper<Friendship> qw2 = new QueryWrapper<>();
        qw2.eq("user_id", friendId).eq("friend_id", userId);
        remove(qw2);
    }

    @Override
    public boolean isFriend(Integer userId, Integer friendId) {
        QueryWrapper<Friendship> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("friend_id", friendId);
        return count(qw) > 0;
    }

    @Override
    public List<Map<String, Object>> getFriendList(Integer userId) {
        // 查好友ID列表
        QueryWrapper<Friendship> fqw = new QueryWrapper<>();
        fqw.eq("user_id", userId);
        List<Friendship> friendships = list(fqw);

        if (friendships.isEmpty()) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Friendship fs : friendships) {
            Integer friendId = fs.getFriendId();
            User friend = userMapper.selectById(friendId);
            if (friend == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("userId", friend.getId());
            item.put("username", friend.getUsername());
            item.put("nickname", friend.getNickname());
            item.put("avatarUrl", friend.getAvatarUrl());
            item.put("role", friend.getRole());
            item.put("uid", friend.getUid());
            item.put("type", "private");

            // 最后一条私聊消息
            QueryWrapper<ChatMessage> msgQw = new QueryWrapper<>();
            msgQw.and(w -> w
                    .and(a -> a.eq("from_user_id", userId).eq("to_user_id", friendId))
                    .or(a -> a.eq("from_user_id", friendId).eq("to_user_id", userId))
            ).isNull("to_group_id").orderByDesc("create_time").last("LIMIT 1");
            ChatMessage lastMsg = chatMessageMapper.selectOne(msgQw);
            item.put("lastMessage", lastMsg != null ? lastMsg.getContent() : "");
            item.put("lastTime", lastMsg != null ? lastMsg.getCreateTime() : null);

            // 未读数
            QueryWrapper<ChatMessage> unreadQw = new QueryWrapper<>();
            unreadQw.eq("from_user_id", friendId).eq("to_user_id", userId)
                    .isNull("to_group_id").eq("is_read", 0);
            int unread = chatMessageMapper.selectCount(unreadQw).intValue();
            item.put("unread", unread);

            result.add(item);
        }

        // 按最后消息时间降序排序（有消息的排前面）
        result.sort((a, b) -> {
            Object ta = a.get("lastTime");
            Object tb = b.get("lastTime");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.toString().compareTo(ta.toString());
        });

        return result;
    }

    /** 发送好友申请（pending 状态） */
    @Override
    public String sendFriendRequest(Integer fromUserId, Integer toUserId) {
        if (fromUserId.equals(toUserId)) return "self";
        if (isFriend(fromUserId, toUserId)) return "already";

        // 检查是否已有 pending 申请
        QueryWrapper<FriendRequest> qw = new QueryWrapper<>();
        qw.eq("from_user_id", fromUserId).eq("to_user_id", toUserId).eq("status", "pending");
        if (friendRequestMapper.selectCount(qw) > 0) return "pending";

        FriendRequest req = new FriendRequest();
        req.setFromUserId(fromUserId);
        req.setToUserId(toUserId);
        req.setStatus("pending");
        req.setCreateTime(LocalDateTime.now());
        friendRequestMapper.insert(req);
        return "sent";
    }

    /** 获取我收到的待处理申请（含申请人信息） */
    @Override
    public List<Map<String, Object>> getIncomingRequests(Integer userId) {
        QueryWrapper<FriendRequest> qw = new QueryWrapper<>();
        qw.eq("to_user_id", userId).eq("status", "pending").orderByDesc("create_time");
        List<FriendRequest> list = friendRequestMapper.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (FriendRequest req : list) {
            User from = userMapper.selectById(req.getFromUserId());
            if (from == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("requestId", req.getId());
            item.put("fromUserId", from.getId());
            item.put("fromUsername", from.getUsername());
            item.put("fromNickname", from.getNickname());
            item.put("fromAvatarUrl", from.getAvatarUrl());
            item.put("fromUid", from.getUid());
            item.put("createTime", req.getCreateTime());
            result.add(item);
        }
        return result;
    }

    /** 同意好友申请 */
    @Override
    @Transactional
    public boolean acceptRequest(Integer requestId, Integer currentUserId) {
        FriendRequest req = friendRequestMapper.selectById(requestId);
        if (req == null || !req.getToUserId().equals(currentUserId)) return false;
        if (!"pending".equals(req.getStatus())) return false;

        req.setStatus("accepted");
        friendRequestMapper.updateById(req);
        addFriend(req.getToUserId(), req.getFromUserId());
        return true;
    }

    /** 拒绝好友申请 */
    @Override
    public boolean rejectRequest(Integer requestId, Integer currentUserId) {
        FriendRequest req = friendRequestMapper.selectById(requestId);
        if (req == null || !req.getToUserId().equals(currentUserId)) return false;
        if (!"pending".equals(req.getStatus())) return false;

        req.setStatus("rejected");
        friendRequestMapper.updateById(req);
        return true;
    }
}
