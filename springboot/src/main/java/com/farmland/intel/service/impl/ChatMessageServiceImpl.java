package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.ChatMessage;
import com.farmland.intel.entity.Friendship;
import com.farmland.intel.entity.User;
import com.farmland.intel.mapper.ChatMessageMapper;
import com.farmland.intel.mapper.FriendshipMapper;
import com.farmland.intel.service.IChatMessageService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    @Resource
    private com.farmland.intel.mapper.UserMapper userMapper;

    @Resource
    private com.farmland.intel.mapper.ChatGroupMemberMapper chatGroupMemberMapper;

    @Resource
    private FriendshipMapper friendshipMapper;

    @Override
    public List<ChatMessage> getPrivateMessages(Integer userId1, Integer userId2, int pageNum, int pageSize) {
        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.and(w -> w
            .and(a -> a.eq("from_user_id", userId1).eq("to_user_id", userId2))
            .or(a -> a.eq("from_user_id", userId2).eq("to_user_id", userId1))
        );
        qw.isNull("to_group_id");
        qw.orderByDesc("create_time");

        IPage<ChatMessage> page = page(new Page<>(pageNum, pageSize), qw);
        List<ChatMessage> list = page.getRecords();
        Collections.reverse(list);
        return list;
    }

    @Override
    public List<ChatMessage> getGroupMessages(Integer groupId, int pageNum, int pageSize) {
        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.eq("to_group_id", groupId);
        qw.orderByDesc("create_time");

        IPage<ChatMessage> page = page(new Page<>(pageNum, pageSize), qw);
        List<ChatMessage> list = page.getRecords();
        Collections.reverse(list);
        return list;
    }

    @Override
    public List<Map<String, Object>> getContacts(Integer userId) {
        // 只返回好友列表（通过UID添加过的用户）
        QueryWrapper<Friendship> fqw = new QueryWrapper<>();
        fqw.eq("user_id", userId);
        List<Friendship> friendships = friendshipMapper.selectList(fqw);

        List<Map<String, Object>> contacts = new ArrayList<>();

        for (Friendship fs : friendships) {
            Integer friendId = fs.getFriendId();
            User friend = userMapper.selectById(friendId);
            if (friend == null) continue;

            Map<String, Object> contact = new HashMap<>();
            contact.put("userId", friend.getId());
            contact.put("username", friend.getUsername());
            contact.put("nickname", friend.getNickname());
            contact.put("avatarUrl", friend.getAvatarUrl());
            contact.put("role", friend.getRole());
            contact.put("uid", friend.getUid());
            contact.put("type", "private");

            // 最后一条私聊消息
            QueryWrapper<ChatMessage> msgQw = new QueryWrapper<>();
            msgQw.and(w -> w
                .and(a -> a.eq("from_user_id", userId).eq("to_user_id", friendId))
                .or(a -> a.eq("from_user_id", friendId).eq("to_user_id", userId))
            ).isNull("to_group_id").orderByDesc("create_time").last("LIMIT 1");
            ChatMessage lastMsg = baseMapper.selectOne(msgQw);
            contact.put("lastMessage", lastMsg != null ? lastMsg.getContent() : "");
            contact.put("lastTime", lastMsg != null ? lastMsg.getCreateTime() : null);

            int unread = countUnreadPrivate(friendId, userId);
            contact.put("unread", unread);
            contacts.add(contact);
        }

        // 按最后消息时间降序排列（有消息的靠前）
        contacts.sort((a, b) -> {
            Object ta = a.get("lastTime");
            Object tb = b.get("lastTime");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.toString().compareTo(ta.toString());
        });

        return contacts;
    }

    @Override
    public int getUnreadCount(Integer userId) {
        // 先查询用户所属的群组 ID 列表
        QueryWrapper<com.farmland.intel.entity.ChatGroupMember> gmQw = new QueryWrapper<>();
        gmQw.eq("user_id", userId).select("group_id");
        List<Object> groupIds = chatGroupMemberMapper.selectList(gmQw)
                .stream().map(m -> m.getGroupId()).collect(Collectors.toList());

        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.eq("is_read", 0);
        qw.and(w -> {
            w.eq("to_user_id", userId);
            if (!groupIds.isEmpty()) {
                w.or().in("to_group_id", groupIds);
            }
        });
        return baseMapper.selectCount(qw).intValue();
    }

    @Override
    public void markPrivateRead(Integer fromUserId, Integer toUserId) {
        ChatMessage update = new ChatMessage();
        update.setIsRead(1);
        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.eq("from_user_id", fromUserId).eq("to_user_id", toUserId).eq("is_read", 0);
        update(update, qw);
    }

    @Override
    public void markGroupRead(Integer groupId, Integer userId) {
        ChatMessage update = new ChatMessage();
        update.setIsRead(1);
        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.eq("to_group_id", groupId).ne("from_user_id", userId).eq("is_read", 0);
        update(update, qw);
    }

    @Override
    public ChatMessage sendMessage(ChatMessage message) {
        message.setCreateTime(LocalDateTime.now());
        message.setIsRead(0);
        if (message.getMessageType() == null) {
            message.setMessageType("text");
        }
        save(message);
        return message;
    }

    @Override
    public List<User> getAllUsersForChat() {
        List<User> users = userMapper.selectList(null);
        for (User u : users) {
            u.setPassword(null);
        }
        return users;
    }

    private int countUnreadPrivate(Integer fromUserId, Integer toUserId) {
        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.eq("from_user_id", fromUserId).eq("to_user_id", toUserId).eq("is_read", 0);
        return baseMapper.selectCount(qw).intValue();
    }
}
