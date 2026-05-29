package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.ChatGroup;
import com.farmland.intel.entity.ChatGroupMember;

import java.util.List;
import java.util.Map;

public interface IChatGroupService extends IService<ChatGroup> {

    /**
     * 创建群组并添加成员
     */
    ChatGroup createGroup(String name, Integer ownerId, List<Integer> memberUserIds);

    /**
     * 获取用户所在的所有群组
     */
    List<Map<String, Object>> getUserGroups(Integer userId);

    /**
     * 获取群成员列表
     */
    List<ChatGroupMember> getGroupMembers(Integer groupId);

    /**
     * 添加群成员
     */
    void addMembers(Integer groupId, List<Integer> userIds);

    /**
     * 移除群成员
     */
    void removeMember(Integer groupId, Integer userId);

    /**
     * 获取可邀请的用户列表（不在该群中的用户）
     */
    List<Map<String, Object>> getNonMembers(Integer groupId);

    /**
     * 通过群号搜索群组
     */
    ChatGroup searchByGroupNumber(String groupNumber);

    /**
     * 通过群号加入群组
     */
    boolean joinGroupByNumber(String groupNumber, Integer userId);
}
