package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.ChatGroup;
import com.farmland.intel.entity.ChatGroupMember;
import com.farmland.intel.entity.User;
import com.farmland.intel.mapper.ChatGroupMapper;
import com.farmland.intel.mapper.ChatGroupMemberMapper;
import com.farmland.intel.mapper.UserMapper;
import com.farmland.intel.service.IChatGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatGroupServiceImpl extends ServiceImpl<ChatGroupMapper, ChatGroup> implements IChatGroupService {

    @Resource
    private ChatGroupMemberMapper chatGroupMemberMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional
    public ChatGroup createGroup(String name, Integer ownerId, List<Integer> memberUserIds) {
        User owner = userMapper.selectById(ownerId);
        ChatGroup group = new ChatGroup();
        group.setName(name);
        group.setOwnerId(ownerId);
        group.setOwnerName(owner != null ? owner.getUsername() : "");
        group.setGroupNumber(generateUniqueGroupNumber());
        save(group);

        // 群主自动成为成员
        ChatGroupMember ownerMember = new ChatGroupMember();
        ownerMember.setGroupId(group.getId());
        ownerMember.setUserId(ownerId);
        ownerMember.setUsername(owner != null ? owner.getUsername() : "");
        chatGroupMemberMapper.insert(ownerMember);

        // 添加其他成员
        if (memberUserIds != null && !memberUserIds.isEmpty()) {
            for (Integer uid : memberUserIds) {
                if (!uid.equals(ownerId)) {
                    User u = userMapper.selectById(uid);
                    ChatGroupMember m = new ChatGroupMember();
                    m.setGroupId(group.getId());
                    m.setUserId(uid);
                    m.setUsername(u != null ? u.getUsername() : "");
                    chatGroupMemberMapper.insert(m);
                }
            }
        }

        return group;
    }

    @Override
    public List<Map<String, Object>> getUserGroups(Integer userId) {
        QueryWrapper<ChatGroupMember> memberQw = new QueryWrapper<>();
        memberQw.eq("user_id", userId);
        List<ChatGroupMember> memberships = chatGroupMemberMapper.selectList(memberQw);

        if (memberships.isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> groupIds = memberships.stream()
            .map(ChatGroupMember::getGroupId).collect(Collectors.toList());

        List<ChatGroup> groups = listByIds(groupIds);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ChatGroup g : groups) {
            Map<String, Object> m = new HashMap<>();
            m.put("groupId", g.getId());
            m.put("name", g.getName());
            m.put("ownerId", g.getOwnerId());
            m.put("ownerName", g.getOwnerName());
            m.put("type", "group");

            // 获取群成员数
            QueryWrapper<ChatGroupMember> countQw = new QueryWrapper<>();
            countQw.eq("group_id", g.getId());
            m.put("memberCount", chatGroupMemberMapper.selectCount(countQw).intValue());
            result.add(m);
        }

        return result;
    }

    @Override
    public List<ChatGroupMember> getGroupMembers(Integer groupId) {
        QueryWrapper<ChatGroupMember> qw = new QueryWrapper<>();
        qw.eq("group_id", groupId);
        return chatGroupMemberMapper.selectList(qw);
    }

    @Override
    @Transactional
    public void addMembers(Integer groupId, List<Integer> userIds) {
        for (Integer uid : userIds) {
            // 检查是否已存在
            QueryWrapper<ChatGroupMember> checkQw = new QueryWrapper<>();
            checkQw.eq("group_id", groupId).eq("user_id", uid);
            if (chatGroupMemberMapper.selectCount(checkQw) > 0) {
                continue;
            }
            User u = userMapper.selectById(uid);
            ChatGroupMember m = new ChatGroupMember();
            m.setGroupId(groupId);
            m.setUserId(uid);
            m.setUsername(u != null ? u.getUsername() : "");
            chatGroupMemberMapper.insert(m);
        }
    }

    @Override
    public void removeMember(Integer groupId, Integer userId) {
        QueryWrapper<ChatGroupMember> qw = new QueryWrapper<>();
        qw.eq("group_id", groupId).eq("user_id", userId);
        chatGroupMemberMapper.delete(qw);
    }

    /** 通过群号搜索群组 */
    @Override
    public ChatGroup searchByGroupNumber(String groupNumber) {
        if (groupNumber == null || groupNumber.trim().isEmpty()) return null;
        QueryWrapper<ChatGroup> qw = new QueryWrapper<>();
        qw.eq("group_number", groupNumber.trim());
        return getOne(qw);
    }

    /** 通过群号加群（加入成员） */
    @Override
    @Transactional
    public boolean joinGroupByNumber(String groupNumber, Integer userId) {
        ChatGroup group = searchByGroupNumber(groupNumber);
        if (group == null) return false;
        // 检查是否已在群里
        QueryWrapper<ChatGroupMember> checkQw = new QueryWrapper<>();
        checkQw.eq("group_id", group.getId()).eq("user_id", userId);
        if (chatGroupMemberMapper.selectCount(checkQw) > 0) return false; // 已在群里

        User u = userMapper.selectById(userId);
        ChatGroupMember m = new ChatGroupMember();
        m.setGroupId(group.getId());
        m.setUserId(userId);
        m.setUsername(u != null ? u.getUsername() : "");
        chatGroupMemberMapper.insert(m);
        return true;
    }

    /** 生成唯一10位群号 */
    private String generateUniqueGroupNumber() {
        java.util.Random random = new java.util.Random();
        String num;
        int maxRetries = 20;
        do {
            long base = 1000000000L + (long)(random.nextDouble() * 9000000000L);
            num = String.valueOf(base);
            maxRetries--;
        } while (maxRetries > 0 &&
                count(new QueryWrapper<ChatGroup>().eq("group_number", num)) > 0);
        return num;
    }

    @Override
    public List<Map<String, Object>> getNonMembers(Integer groupId) {
        QueryWrapper<ChatGroupMember> memberQw = new QueryWrapper<>();
        memberQw.eq("group_id", groupId);
        List<ChatGroupMember> members = chatGroupMemberMapper.selectList(memberQw);
        Set<Integer> memberUserIds = members.stream()
            .map(ChatGroupMember::getUserId).collect(Collectors.toSet());

        List<User> allUsers = userMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : allUsers) {
            if (!memberUserIds.contains(u.getId())) {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", u.getId());
                m.put("username", u.getUsername());
                m.put("nickname", u.getNickname());
                m.put("avatarUrl", u.getAvatarUrl());
                result.add(m);
            }
        }
        return result;
    }
}
